"""Opt-in, private 24-hour WAV checkpoints. No transcripts or ASR results."""

import base64
from datetime import datetime, timezone
import fcntl
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import tempfile
import threading
import time
import uuid
import wave


TTL_SECONDS = 24 * 60 * 60


class RecoveryError(Exception):
    """Stable first-party recovery failures."""


def validate_wav(raw):
    if not 44 < len(raw) <= 1_000_000:
        raise RecoveryError("RECOVERY_AUDIO_INVALID")
    with wave.open(io.BytesIO(raw), "rb") as wav:
        frames = wav.getnframes()
        if ((wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getcomptype())
                != (1, 2, 16000, "NONE") or not 0 < frames <= 480000
                or len(wav.readframes(frames)) != frames * 2):
            raise RecoveryError("RECOVERY_AUDIO_INVALID")


class RecoveryStore:
    def __init__(self, root, sample_ids, reference_hash, *, clock=time.time):
        self.root = Path(root)
        self.sample_ids = tuple(sample_ids)
        if any(not re.fullmatch(r"[a-z][a-z0-9-]{0,63}", item) for item in self.sample_ids):
            raise RecoveryError("RECOVERY_CONFIG_INVALID")
        self.reference_hash = reference_hash
        self.clock = clock
        self.lock = threading.RLock()
        if any(path.is_symlink() for path in (self.root, *self.root.parents)):
            raise RecoveryError("RECOVERY_DIRECTORY_INVALID")
        self.root.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.root.chmod(0o700)
        self.fd = os.open(self.root / ".lock", os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        os.fchmod(self.fd, 0o600)
        try:
            fcntl.flock(self.fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            os.close(self.fd)
            raise RecoveryError("RECOVERY_ALREADY_OPEN") from None
        try:
            self.purge()
        except Exception:
            self.close()
            raise

    def close(self):
        with self.lock:
            if self.fd is not None:
                os.close(self.fd)
                self.fd = None

    def _atomic(self, name, content):
        staged = None
        try:
            with tempfile.NamedTemporaryFile(dir=self.root, prefix=".stage-", delete=False) as output:
                staged = Path(output.name)
                os.fchmod(output.fileno(), 0o600)
                output.write(content)
                output.flush()
                os.fsync(output.fileno())
            os.replace(staged, self.root / name)
            directory_fd = os.open(self.root, os.O_DIRECTORY)
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)
        finally:
            if staged is not None:
                staged.unlink(missing_ok=True)

    def _read_file(self, name, limit):
        fd = os.open(self.root / name, os.O_RDONLY | os.O_NOFOLLOW)
        with os.fdopen(fd, "rb") as source:
            raw = source.read(limit + 1)
        if len(raw) > limit:
            raise RecoveryError("RECOVERY_FILE_INVALID")
        return raw

    def _read(self):
        try:
            value = json.loads(self._read_file("manifest.json", 65536))
        except FileNotFoundError:
            return None
        except (ValueError, UnicodeError):
            raise RecoveryError("RECOVERY_MANIFEST_INVALID") from None
        try:
            valid = (value["schema_version"] == 1 and value["reference_hash"] == self.reference_hash
                     and re.fullmatch(r"[a-f0-9]{32}", value["session_id"])
                     and math.isfinite(value["created_at"]) and math.isfinite(value["expires_at"])
                     and 0 < value["expires_at"] - value["created_at"] <= TTL_SECONDS
                     and isinstance(value["clips"], dict)
                     and set(value["clips"]).issubset(self.sample_ids)
                     and all(type(entry["bytes"]) is int and 44 < entry["bytes"] <= 1_000_000
                             and re.fullmatch(r"[a-f0-9]{64}", entry["sha256"])
                             for entry in value["clips"].values()))
        except (KeyError, TypeError, ValueError):
            valid = False
        if not valid:
            raise RecoveryError("RECOVERY_MANIFEST_INVALID")
        return value

    def _write(self, value):
        self._atomic("manifest.json", json.dumps(value).encode())

    def _delete(self):
        for item in self.sample_ids:
            (self.root / (item + ".wav")).unlink(missing_ok=True)
        (self.root / "manifest.json").unlink(missing_ok=True)
        for staged in self.root.glob(".stage-*"):
            if staged.is_file() or staged.is_symlink():
                staged.unlink()

    def _new(self):
        now = self.clock()
        value = {"schema_version": 1, "session_id": uuid.uuid4().hex,
                 "reference_hash": self.reference_hash, "created_at": now,
                 "expires_at": now + TTL_SECONDS, "clips": {}}
        self._write(value)
        return value

    def purge(self):
        with self.lock:
            value = self._read()
            if value is not None and value["expires_at"] <= self.clock():
                self._delete()
                value = None
            if value is None:
                # A crash before the first manifest cannot leave audio unbounded.
                for path in self.root.glob("*.wav"):
                    if path.stem in self.sample_ids and self.clock() - path.lstat().st_mtime >= TTL_SECONDS:
                        path.unlink()
                value = self._new()
            return value

    def info(self):
        with self.lock:
            value = self.purge()
            return {"enabled": True, "session_id": value["session_id"],
                    "clip_count": len(value["clips"]), "ttl_hours": 24,
                    "expires_at_utc": datetime.fromtimestamp(value["expires_at"], timezone.utc).isoformat()}

    def save(self, clips, *, session_id=None):
        with self.lock:
            value = self.purge()
            if session_id is not None and session_id != value["session_id"]:
                raise RecoveryError("RECOVERY_SESSION_EXPIRED")
            for clip in clips:
                if clip["kind"] != "speech":
                    continue
                item, raw = clip["id"], clip["wav"]
                if item not in self.sample_ids:
                    raise RecoveryError("RECOVERY_CLIP_INVALID")
                if item not in value["clips"] and self.sample_ids.index(item) != len(value["clips"]):
                    raise RecoveryError("RECOVERY_INCOMPLETE_SEQUENCE")
                validate_wav(raw)
                self._atomic(item + ".wav", raw)
                value["clips"][item] = {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
                self._write(value)
            return self.info()

    def restore(self):
        with self.lock:
            value = self.purge()
            clips = []
            for item in self.sample_ids:
                if item not in value["clips"]:
                    continue
                raw = self._read_file(item + ".wav", 1_000_000)
                entry = value["clips"][item]
                if len(raw) != entry["bytes"] or hashlib.sha256(raw).hexdigest() != entry["sha256"]:
                    raise RecoveryError("RECOVERY_CHECKSUM_MISMATCH")
                validate_wav(raw)
                clips.append({"id": item, "wav_base64": base64.b64encode(raw).decode()})
            if [clip["id"] for clip in clips] != list(self.sample_ids[:len(clips)]):
                raise RecoveryError("RECOVERY_INCOMPLETE_SEQUENCE")
            return {**self.info(), "clips": clips}

    def clear(self):
        with self.lock:
            self._delete()
            self._new()  # Rotate the generation so delayed writes cannot resurrect deleted audio.
            return self.info()
