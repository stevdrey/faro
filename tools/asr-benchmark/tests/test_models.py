"""Small-model pins, isolated runtime and setup; no downloads or Docker required."""

from contextlib import redirect_stdout
import copy
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import benchmark as bench
import models


class ModelTests(unittest.TestCase):
    def test_small_provenance_and_runtime_are_isolated_from_base(self):
        before = (bench.baseline.MODEL_PATH, bench.baseline.BASE_URL, bench.baseline.CONTAINER,
                  bench.baseline.manifest())
        small = models.small_runtime()
        spec = small.manifest()
        self.assertEqual(spec["model"]["id"], "small-multilingual")
        self.assertEqual(spec["model"]["size_bytes"], 487601967)
        self.assertEqual(spec["model"]["sha256"], "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b")
        self.assertEqual(spec["runtime"], before[3]["runtime"])
        self.assertEqual((bench.baseline.MODEL_PATH, bench.baseline.BASE_URL,
                          bench.baseline.CONTAINER, bench.baseline.manifest()), before)
        args = small.docker_start_args(spec, "token")
        self.assertEqual(args[args.index("--publish") + 1], "127.0.0.1:8086:8080")
        self.assertEqual(args[args.index("--name") + 1], "faro-whisper-small")
        self.assertIn("io.github.stevdrey.faro.baseline=whisper-small", args)
        self.assertEqual(args[args.index("--log-driver") + 1], "none")
        self.assertIn("ggml-small.bin:/models/ggml-base.bin:ro,Z", args[args.index("--volume") + 1])
        self.assertEqual(args[args.index("--language") + 1], "es")
        self.assertEqual(args[args.index("--threads") + 1], "4")
        self.assertIn("--pull=never", args)

    def test_small_transport_uses_its_own_fixed_endpoint_and_no_fallback(self):
        runtime = models.small_runtime()
        adapter = object.__new__(bench.Whisper)
        adapter.runtime = runtime
        with patch.object(runtime, "local_json", return_value={"text": "seis"}) as http:
            self.assertEqual(adapter.transcribe(b"synthetic audio"), "seis")
            self.assertEqual(http.call_args.args, ("/inference",))
            self.assertEqual(runtime.BASE_URL, "http://127.0.0.1:8086")
        with patch.object(runtime, "local_json", side_effect=runtime.BaselineError("PRIVATE")), \
                patch.object(bench.baseline, "local_json") as base:
            with self.assertRaisesRegex(bench.BenchmarkError, "^PROVIDER_ERROR$"):
                adapter.transcribe(b"synthetic audio")
            base.assert_not_called()

    def test_setup_downloads_only_when_missing_and_rejects_corrupt_cache(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Mock()
            runtime.MODEL_PATH = Path(directory) / "small.bin"
            runtime.manifest.return_value = {"model": {"sha256": "synthetic"}}
            runtime.download_model.side_effect = lambda path, spec: path.write_bytes(b"public test weights")
            with patch.object(models, "small_runtime", return_value=runtime), \
                    patch.object(sys, "argv", ["models.py", "setup-small"]), redirect_stdout(io.StringIO()):
                # Result formatting requires the pinned metadata too.
                runtime.manifest.return_value["model"]["size_bytes"] = 19
                self.assertEqual(models.main(), 0)
            runtime.download_model.assert_called_once()
            self.assertEqual(runtime.MODEL_PATH.stat().st_mode & 0o777, 0o644)
            runtime.download_model.reset_mock()
            runtime.verify_model.side_effect = ValueError("corrupt cache")
            with patch.object(models, "small_runtime", return_value=runtime), \
                    patch.object(sys, "argv", ["models.py", "setup-small"]), patch("sys.stderr", io.StringIO()):
                self.assertEqual(models.main(), 1)
            runtime.download_model.assert_not_called()


if __name__ == "__main__":
    unittest.main()
