"""Private WAV checkpoints use synthetic samples in temporary directories only."""

import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import benchmark as bench
from recovery import RecoveryError, RecoveryStore, TTL_SECONDS


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "private"
        self.now = 1000000000.0
        self.store = RecoveryStore(self.root, ["one", "two"], "reference", clock=lambda: self.now)
        self.addCleanup(lambda: self.store.close())
        self.wav = bench.wav_bytes(bytes(32000))

    def clip(self, item="one"):
        return {"id": item, "kind": "speech", "wav": self.wav}

    def test_round_trip_restart_private_permissions_and_no_transcripts(self):
        self.store.save([self.clip(), self.clip("two"), {"kind": "silence"}])
        self.assertEqual(self.root.stat().st_mode & 0o777, 0o700)
        for path in self.root.iterdir():
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.store.close()
        self.store = RecoveryStore(self.root, ["one", "two"], "reference", clock=lambda: self.now)
        result = self.store.restore()
        self.assertEqual([c["id"] for c in result["clips"]], ["one", "two"])
        self.assertEqual(result["clip_count"], 2)
        manifest = json.loads((self.root / "manifest.json").read_text())
        self.assertEqual(manifest["clips"]["one"]["sha256"], hashlib.sha256(self.wav).hexdigest())
        for forbidden in ("transcript", "text", "hypothesis"):
            self.assertNotIn(forbidden, json.dumps(manifest))

    def test_expiration_removes_audio_before_restore_and_does_not_extend_on_save(self):
        initial = self.store.info()
        self.store.save([self.clip()])
        self.now += 60
        self.store.save([self.clip("two")])
        self.assertEqual(self.store.info()["expires_at_utc"], initial["expires_at_utc"])
        self.now += TTL_SECONDS
        self.assertEqual(self.store.restore()["clips"], [])
        self.assertEqual(list(self.root.glob("*.wav")), [])

    def test_window_starts_at_first_retained_clip_not_guide_start(self):
        self.now += 3600  # The guide sat idle for an hour with an empty session.
        self.store.save([self.clip()])
        expected = self.store.info()["expires_at_utc"]
        self.now += 60
        self.store.save([self.clip("two")])
        self.assertEqual(self.store.info()["expires_at_utc"], expected)
        self.now += TTL_SECONDS - 61
        self.assertEqual(len(self.store.restore()["clips"]), 2)
        self.now += 1
        self.assertEqual(self.store.restore()["clips"], [])

    def test_identical_checkpoint_is_not_rewritten(self):
        self.store.save([self.clip(), self.clip("two")])
        with patch.object(self.store, "_atomic", wraps=self.store._atomic) as atomic:
            self.store.save([self.clip(), self.clip("two")])
        atomic.assert_not_called()

    def test_crash_while_replacing_clip_leaves_contiguous_verifiable_prefix(self):
        self.store.save([self.clip(), self.clip("two")])
        changed = bench.wav_bytes(bytes(30000))
        original = self.store._atomic

        def crash(name, content):
            if name == "one.wav":
                raise OSError("simulated crash")
            return original(name, content)
        with patch.object(self.store, "_atomic", side_effect=crash), self.assertRaises(OSError):
            self.store.save([{"id": "one", "kind": "speech", "wav": changed}])
        self.store.close()
        self.store = RecoveryStore(self.root, ["one", "two"], "reference", clock=lambda: self.now)
        self.assertEqual(self.store.restore()["clips"], [])
        deadline = self.store.info()["expires_at_utc"]
        self.now += 60
        self.store.save([{"id": "one", "kind": "speech", "wav": changed}, self.clip("two")])
        self.assertEqual([c["id"] for c in self.store.restore()["clips"]], ["one", "two"])
        self.assertEqual(self.store.info()["expires_at_utc"], deadline)  # Leftover audio keeps its deadline.

    def test_clear_rotates_session_and_rejects_late_write(self):
        session = self.store.info()["session_id"]
        self.store.save([self.clip()], session_id=session)
        self.store.clear()
        self.assertEqual(list(self.root.glob("*.wav")), [])
        with self.assertRaisesRegex(RecoveryError, "RECOVERY_SESSION_EXPIRED"):
            self.store.save([self.clip()], session_id=session)

    def test_corruption_and_symlinks_are_not_restored(self):
        self.store.save([self.clip()])
        path = self.root / "one.wav"
        path.write_bytes(self.wav[:-1] + b"x")
        with self.assertRaisesRegex(RecoveryError, "RECOVERY_CHECKSUM_MISMATCH"):
            self.store.restore()
        path.unlink()
        path.symlink_to(Path(self.temp.name) / "outside.wav")
        with self.assertRaises(OSError):
            self.store.restore()

    def test_only_known_bounded_wavs_and_contiguous_recordings(self):
        for clip in ({"id": "../outside", "kind": "speech", "wav": self.wav},
                     {"id": "one", "kind": "speech", "wav": b"invalid"}):
            with self.assertRaises(RecoveryError):
                self.store.save([clip])
        with self.assertRaisesRegex(RecoveryError, "RECOVERY_INCOMPLETE_SEQUENCE"):
            self.store.save([self.clip("two")])

    def test_second_process_lock_and_changed_reference_fail_closed(self):
        with self.assertRaisesRegex(RecoveryError, "RECOVERY_ALREADY_OPEN"):
            RecoveryStore(self.root, ["one", "two"], "reference")
        self.store.save([self.clip()])
        self.store.close()
        with self.assertRaisesRegex(RecoveryError, "RECOVERY_MANIFEST_INVALID"):
            RecoveryStore(self.root, ["one", "two"], "changed", clock=lambda: self.now)
        self.assertTrue((self.root / "one.wav").exists())
        # Invalid metadata stays fail-closed, but audio never outlives the 24-hour limit.
        stale = self.now - TTL_SECONDS
        os.utime(self.root / "one.wav", (stale, stale))
        with self.assertRaisesRegex(RecoveryError, "RECOVERY_MANIFEST_INVALID"):
            RecoveryStore(self.root, ["one", "two"], "changed", clock=lambda: self.now)
        self.assertFalse((self.root / "one.wav").exists())
        self.assertTrue((self.root / "manifest.json").exists())


if __name__ == "__main__":
    unittest.main()
