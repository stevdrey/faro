"""Offline tests: no Docker daemon, downloads, voices, or model weights required."""

from contextlib import redirect_stderr, redirect_stdout
import copy
import hashlib
import http.client
import importlib.util
import io
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import urllib.error
import wave


MODULE_PATH = Path(__file__).resolve().parents[1] / "baseline.py"
SPEC = importlib.util.spec_from_file_location("baseline", MODULE_PATH)
baseline = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(baseline)
IDENTITY = "a" * 64


class BaselineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="faro-baseline-test-")
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.model = self.directory / "model directory" / "ggml-base.bin"
        self.config = copy.deepcopy(baseline.manifest())
        self.weights = b"synthetic-test-weights"
        self.config["model"].update(size_bytes=len(self.weights),
                                    sha256=hashlib.sha256(self.weights).hexdigest())
        self.addCleanup(patch.stopall)
        patch.object(baseline, "MODEL_PATH", self.model).start()
        patch.object(baseline, "manifest", return_value=self.config).start()

    def install_model(self):
        self.model.parent.mkdir(parents=True)
        self.model.write_bytes(self.weights)

    def fake_download(self, payload):
        opener = Mock()
        opener.open.return_value = io.BytesIO(payload)
        return patch.object(baseline.urllib.request, "build_opener", return_value=opener)

    def test_committed_artifact_pins_and_download_agree(self):
        config = json.loads((MODULE_PATH.parent / "artifacts.json").read_text())
        self.assertRegex(config["runtime"]["image"], r"^ghcr.io/ggml-org/whisper.cpp@sha256:[0-9a-f]{64}$")
        model = config["model"]
        self.assertEqual(model["file"], "ggml-base.bin")
        self.assertEqual(model["id"], "base-multilingual")
        self.assertRegex(model["revision"], r"^[0-9a-f]{40}$")
        self.assertRegex(model["sha256"], r"^[0-9a-f]{64}$")
        self.assertEqual(model["url"], "https://huggingface.co/ggerganov/whisper.cpp/resolve/"
                         + model["revision"] + "/ggml-base.bin")

    def test_download_installs_only_verified_model(self):
        with self.fake_download(self.weights):
            baseline.download_model(self.model, self.config["model"])
        self.assertEqual(self.model.read_bytes(), self.weights)
        self.assertEqual(self.model.stat().st_mode & 0o777, 0o644)
        self.assertEqual(list(self.model.parent.glob("*.part")), [])

    def test_incomplete_corrupt_and_oversize_downloads_are_removed(self):
        for payload in (b"short", b"x" * len(self.weights), self.weights * 2):
            with self.subTest(length=len(payload)), self.fake_download(payload):
                with self.assertRaises(baseline.BaselineError):
                    baseline.download_model(self.model, self.config["model"])
                self.assertFalse(self.model.exists())
                self.assertEqual(list(self.model.parent.glob("*.part")), [])

    def test_interrupted_download_preserves_existing_destination(self):
        self.install_model()
        opener = Mock()
        opener.open.side_effect = http.client.IncompleteRead(b"private-content", 10)
        with patch.object(baseline.urllib.request, "build_opener", return_value=opener):
            with self.assertRaisesRegex(baseline.BaselineError, "^MODEL_DOWNLOAD_FAILED$"):
                baseline.download_model(self.model, self.config["model"])
        self.assertEqual(self.model.read_bytes(), self.weights)
        self.assertEqual(list(self.model.parent.glob("*.part")), [])

    def test_download_http_error_is_closed_and_staging_removed(self):
        response = io.BytesIO(b"private error response")
        error = urllib.error.HTTPError("https://example.invalid", 503, "unavailable", {}, response)
        opener = Mock()
        opener.open.side_effect = error
        with patch.object(baseline.urllib.request, "build_opener", return_value=opener):
            with self.assertRaisesRegex(baseline.BaselineError, "MODEL_DOWNLOAD_FAILED"):
                baseline.download_model(self.model, self.config["model"])
        self.assertTrue(response.closed)
        self.assertFalse(self.model.exists())
        self.assertEqual(list(self.model.parent.glob("*.part")), [])

    def test_missing_tampered_and_symlink_models_rejected_before_docker(self):
        run = patch.object(baseline, "run_tool").start()
        with self.assertRaisesRegex(baseline.BaselineError, "MODEL_MISSING"):
            baseline.start()
        self.install_model()
        self.model.write_bytes(b"x" * len(self.weights))
        with self.assertRaisesRegex(baseline.BaselineError, "MODEL_CHECKSUM_MISMATCH"):
            baseline.start()
        self.model.unlink()
        target = self.directory / "target.bin"
        target.write_bytes(self.weights)
        self.model.symlink_to(target)
        with self.assertRaisesRegex(baseline.BaselineError, "MODEL_MISSING_OR_SYMLINK"):
            baseline.start()
        run.assert_not_called()

    def test_setup_reuses_verified_model_and_pulls_exact_image(self):
        self.install_model()
        with patch.object(baseline, "download_model") as download, \
                patch.object(baseline, "run_tool") as run, redirect_stdout(io.StringIO()):
            baseline.setup()
        download.assert_not_called()
        self.assertEqual(run.call_args.args[0], ["docker", "pull", "--platform", "linux/amd64",
                                               self.config["runtime"]["image"]])

    def test_setup_downloads_missing_model_before_pulling(self):
        events = []
        with patch.object(baseline, "download_model", side_effect=lambda *args: events.append("download")), \
                patch.object(baseline, "run_tool", side_effect=lambda *args, **kwargs: events.append("pull")), \
                redirect_stdout(io.StringIO()):
            baseline.setup()
        self.assertEqual(events, ["download", "pull"])

    def test_docker_args_keep_cpu_spanish_and_container_boundaries(self):
        args = baseline.docker_start_args(self.config)
        for flag, value in (("--publish", "127.0.0.1:8085:8080"), ("--user", "65534:65534"),
                            ("--cap-drop", "ALL"), ("--log-driver", "none"),
                            ("--entrypoint", "/app/build/bin/whisper-server"),
                            ("--language", "es"), ("--model", "/models/ggml-base.bin"),
                            ("--max-context", "0"),
                            ("--workdir", "/tmp"), ("--security-opt", "no-new-privileges")):
            self.assertEqual(args[args.index(flag) + 1], value)
        self.assertEqual(args[args.index("--volume") + 1],
                         f"{self.model}:/models/ggml-base.bin:ro,Z")
        for flag in ("--read-only", "--no-gpu", "--pull=never", "--rm"):
            self.assertIn(flag, args)
        for flag in ("--convert", "--privileged", "--gpus", "--network=host"):
            self.assertNotIn(flag, args)
        self.assertNotIn("bash", args)
        self.assertEqual(args.count(self.config["runtime"]["image"]), 1)

    def test_tool_errors_are_redacted_and_commands_never_use_a_shell(self):
        errors = [FileNotFoundError("private path"), subprocess.TimeoutExpired("secret", 1),
                  subprocess.CalledProcessError(1, "docker", stderr=b"private transcript")]
        for error in errors:
            with self.subTest(error=type(error).__name__), \
                    patch.object(baseline.subprocess, "run", side_effect=error) as run:
                with self.assertRaisesRegex(baseline.BaselineError, "^SAFE_FAILURE$"):
                    baseline.run_tool(["docker", "version"], code="SAFE_FAILURE")
                self.assertNotIn("shell", run.call_args.kwargs)
                self.assertTrue(run.call_args.kwargs["capture_output"])
                self.assertGreater(run.call_args.kwargs["timeout"], 0)

    def test_start_waits_for_health(self):
        self.install_model()
        with patch.object(baseline, "run_tool", return_value=IDENTITY), \
                patch.object(baseline, "wait_ready") as ready, redirect_stdout(io.StringIO()):
            baseline.start()
        ready.assert_called_once_with(IDENTITY)

    def test_start_cleanup_on_timeout_or_interrupt(self):
        self.install_model()
        for error in (baseline.BaselineError("SERVER_START_TIMEOUT"), KeyboardInterrupt()):
            with self.subTest(error=type(error).__name__), \
                    patch.object(baseline, "run_tool", return_value=IDENTITY) as run, \
                    patch.object(baseline, "owned_container", return_value=IDENTITY), \
                    patch.object(baseline, "wait_ready", side_effect=error):
                with self.assertRaises(type(error)):
                    baseline.start()
                self.assertEqual(run.call_args.args[0], ["docker", "rm", "--force", IDENTITY])

    def test_start_does_not_remove_replacement_or_already_removed_container(self):
        self.install_model()
        for remaining in ("", "b" * 64):
            with self.subTest(remaining=remaining), \
                    patch.object(baseline, "run_tool", return_value=IDENTITY) as run, \
                    patch.object(baseline, "owned_container", return_value=remaining), \
                    patch.object(baseline, "wait_ready", side_effect=baseline.BaselineError("EXITED")):
                with self.assertRaises(baseline.BaselineError):
                    baseline.start()
                self.assertEqual(run.call_count, 1)

    def test_readiness_retries_loading_then_accepts_health(self):
        with patch.object(baseline, "run_tool", return_value="true"), \
                patch.object(baseline.time, "sleep"), \
                patch.object(baseline, "local_json", side_effect=[
                    baseline.BaselineError("LOCAL_HTTP_STATUS_FAILED"), {"status": "ok"}]) as http:
            baseline.wait_ready(IDENTITY)
        self.assertEqual(http.call_count, 2)

    def test_readiness_detects_exit_and_deadline(self):
        with patch.object(baseline, "run_tool", return_value="false"):
            with self.assertRaisesRegex(baseline.BaselineError, "CONTAINER_EXITED"):
                baseline.wait_ready(IDENTITY)
        with patch.object(baseline.time, "monotonic", side_effect=[0, 121]):
            with self.assertRaisesRegex(baseline.BaselineError, "SERVER_START_TIMEOUT"):
                baseline.wait_ready(IDENTITY)

    def test_stop_is_owned_and_idempotent(self):
        with patch.object(baseline, "run_tool", side_effect=[IDENTITY, "", ""]) as run, \
                redirect_stdout(io.StringIO()):
            baseline.stop()
        listing = run.call_args_list[0].args[0]
        self.assertIn("name=^/faro-whisper-baseline$", listing)
        self.assertIn("label=" + baseline.OWNER_LABEL, listing)
        self.assertEqual(run.call_args_list[1].args[0], ["docker", "stop", "--time", "10", IDENTITY])
        with patch.object(baseline, "run_tool", return_value="") as run, \
                redirect_stdout(io.StringIO()):
            baseline.stop()
        self.assertEqual(run.call_count, 1)

    def test_stop_waits_until_docker_releases_container_name(self):
        with patch.object(baseline, "owned_container", side_effect=[IDENTITY, IDENTITY, ""]), \
                patch.object(baseline, "run_tool"), patch.object(baseline.time, "sleep") as sleep, \
                redirect_stdout(io.StringIO()):
            baseline.stop()
        sleep.assert_called_once_with(0.25)

    def test_stop_removal_deadline_is_not_reported_as_success(self):
        with patch.object(baseline, "owned_container", return_value=IDENTITY), \
                patch.object(baseline, "run_tool"), \
                patch.object(baseline.time, "monotonic", side_effect=[0, 31]):
            with self.assertRaisesRegex(baseline.BaselineError, "DOCKER_REMOVE_TIMEOUT"):
                baseline.stop()

    def test_docker_unavailable_is_not_treated_as_already_stopped(self):
        with patch.object(baseline, "run_tool", side_effect=baseline.BaselineError("DOCKER_UNAVAILABLE")):
            with self.assertRaisesRegex(baseline.BaselineError, "DOCKER_UNAVAILABLE"):
                baseline.stop()

    def test_multipart_carries_exact_wav_and_spanish_fields(self):
        from email import policy
        from email.parser import BytesParser
        wav = b"RIFF\x00\xff\r\nWAVE-synthetic"
        body, headers = baseline.multipart(wav)
        message = BytesParser(policy=policy.default).parsebytes(
            f'Content-Type: {headers["Content-Type"]}\r\nMIME-Version: 1.0\r\n\r\n'.encode() + body)
        fields = {part.get_param("name", header="content-disposition"): part
                  for part in message.iter_parts()}
        self.assertEqual(fields["file"].get_payload(decode=True), wav)
        self.assertEqual(fields["file"].get_filename(), "synthetic.wav")
        self.assertEqual(fields["language"].get_content(), "es")
        self.assertEqual(fields["response_format"].get_content(), "json")
        self.assertEqual(fields["translate"].get_content(), "false")
        self.assertNotIn("prompt", fields)

    def test_http_bypasses_proxies_and_rejects_redirects(self):
        opener = Mock()
        opener.open.return_value = io.BytesIO(b'{"status":"ok"}')
        with patch.object(baseline.urllib.request, "build_opener", return_value=opener) as build:
            self.assertEqual(baseline.local_json("/health"), {"status": "ok"})
        self.assertEqual(build.call_args.args[0].proxies, {})
        self.assertIsInstance(build.call_args.args[1], baseline.NoRedirect)
        self.assertEqual(opener.open.call_args.args[0].full_url, "http://127.0.0.1:8085/health")
        for code in (301, 302, 303, 307, 308):
            with self.subTest(code=code), self.assertRaisesRegex(baseline.BaselineError, "REDIRECT_REJECTED"):
                baseline.NoRedirect().redirect_request(None, None, code, "", {}, "https://remote.invalid")
        with self.assertRaisesRegex(baseline.BaselineError, "DOWNLOAD_INSECURE_REDIRECT"):
            baseline.HttpsRedirect().redirect_request(None, None, 302, "", {}, "http://remote.invalid")

    def test_http_failure_cases_do_not_expose_response_body(self):
        failures = [
            (urllib.error.HTTPError(baseline.BASE_URL, 500, "private transcript", {}, None), "STATUS_FAILED"),
            (urllib.error.URLError("private transcript"), "UNAVAILABLE_OR_TIMEOUT"),
            (socket.timeout("private transcript"), "UNAVAILABLE_OR_TIMEOUT"),
            (http.client.IncompleteRead(b"private transcript", 100), "UNAVAILABLE_OR_TIMEOUT"),
        ]
        for error, code in failures:
            opener = Mock()
            opener.open.side_effect = error
            with self.subTest(error=type(error).__name__), \
                    patch.object(baseline.urllib.request, "build_opener", return_value=opener):
                with self.assertRaisesRegex(baseline.BaselineError, "^LOCAL_HTTP_" + code + "$"):
                    baseline.local_json("/inference", data=b"synthetic", timeout=120)

    def test_http_rejects_invalid_oversize_or_nonobject_json(self):
        for body in (b"not json", b"[]", b"null", b"\xff", b"x" * (baseline.MAX_RESPONSE_BYTES + 1)):
            opener = Mock()
            opener.open.return_value = io.BytesIO(body)
            with self.subTest(size=len(body)), \
                    patch.object(baseline.urllib.request, "build_opener", return_value=opener):
                with self.assertRaises(baseline.BaselineError):
                    baseline.local_json("/inference")

    def test_transcribe_preserves_valid_text_but_rejects_empty_error_or_invalid(self):
        with patch.object(baseline, "local_json", return_value={"text": "  Texto sintético.\n"}):
            self.assertEqual(baseline.transcribe(b"wav"), "  Texto sintético.\n")
        for response in ({}, {"text": ""}, {"text": " \n"}, {"text": None}, {"text": 12},
                         {"text": "text", "error": "private error"}):
            with self.subTest(response=response), patch.object(baseline, "local_json", return_value=response):
                with self.assertRaisesRegex(baseline.BaselineError, "INFERENCE_EMPTY_OR_INVALID_TEXT"):
                    baseline.transcribe(b"wav")

    def fake_synthesis(self, directory, sample):
        path = directory / "normalized.wav"
        with wave.open(str(path), "wb") as audio:
            audio.setnchannels(1)
            audio.setsampwidth(2)
            audio.setframerate(16000)
            audio.writeframes(b"\0\0" * 1600)
        self.generated_directory = directory
        self.assertEqual(directory.stat().st_mode & 0o777, 0o700)
        return path

    def test_smoke_summary_redacts_text_and_removes_temporary_audio(self):
        output = io.StringIO()
        with patch.object(baseline, "owned_container", return_value=IDENTITY), \
                patch.object(baseline, "local_json", return_value={"status": "ok"}), \
                patch.object(baseline, "synthesize", side_effect=self.fake_synthesis), \
                patch.object(baseline, "transcribe", return_value="private transcript"), redirect_stdout(output):
            baseline.smoke_test()
        summary = json.loads(output.getvalue())
        self.assertEqual(summary["status"], "passed")
        self.assertEqual(summary["audio_seconds"], 0.1)
        self.assertNotIn("private", output.getvalue())
        self.assertFalse(self.generated_directory.exists())

    def test_smoke_cleanup_on_inference_failure_and_interrupt(self):
        for error in (baseline.BaselineError("LOCAL_HTTP_UNAVAILABLE_OR_TIMEOUT"), KeyboardInterrupt()):
            with self.subTest(error=type(error).__name__), \
                    patch.object(baseline, "owned_container", return_value=IDENTITY), \
                    patch.object(baseline, "local_json", return_value={"status": "ok"}), \
                    patch.object(baseline, "synthesize", side_effect=self.fake_synthesis), \
                    patch.object(baseline, "transcribe", side_effect=error):
                with self.assertRaises(type(error)):
                    baseline.smoke_test()
                self.assertFalse(self.generated_directory.exists())

    def test_smoke_cleanup_when_synthesis_fails(self):
        def failed_synthesis(directory, sample):
            self.generated_directory = directory
            (directory / "partial.wav").write_bytes(b"synthetic")
            raise baseline.BaselineError("SYNTHESIS_FAILED")
        with patch.object(baseline, "owned_container", return_value=IDENTITY), \
                patch.object(baseline, "local_json", return_value={"status": "ok"}), \
                patch.object(baseline, "synthesize", side_effect=failed_synthesis):
            with self.assertRaisesRegex(baseline.BaselineError, "SYNTHESIS_FAILED"):
                baseline.smoke_test()
        self.assertFalse(self.generated_directory.exists())

    def test_synthesis_uses_formant_voice_and_explicit_pcm_conversion(self):
        with patch.object(baseline, "run_tool") as run:
            path = baseline.synthesize(self.directory, self.config["sample"])
        self.assertEqual(path, self.directory / "normalized.wav")
        synth, conversion = [call.args[0] for call in run.call_args_list]
        self.assertEqual(synth[:3], ["espeak-ng", "-v", "es-419"])
        self.assertEqual(synth[-1], self.config["sample"]["text"])
        for flag, value in (("-ar", "16000"), ("-ac", "1"), ("-c:a", "pcm_s16le")):
            self.assertEqual(conversion[conversion.index(flag) + 1], value)
        self.assertIn("-nostdin", conversion)

    def test_smoke_rejects_wrong_audio_format_and_duration_with_cleanup(self):
        for channels, frames, code in ((2, 1600, "INVALID_SYNTHETIC_WAV"),
                                        (1, 0, "INVALID_SYNTHETIC_DURATION"),
                                        (1, 16000 * 31, "INVALID_SYNTHETIC_DURATION")):
            def synth(directory, sample):
                self.generated_directory = directory
                path = directory / "normalized.wav"
                with wave.open(str(path), "wb") as audio:
                    audio.setnchannels(channels)
                    audio.setsampwidth(2)
                    audio.setframerate(16000)
                    audio.writeframes(b"\0\0" * frames * channels)
                return path
            with self.subTest(channels=channels, frames=frames), \
                    patch.object(baseline, "owned_container", return_value=IDENTITY), \
                    patch.object(baseline, "local_json", return_value={"status": "ok"}), \
                    patch.object(baseline, "synthesize", side_effect=synth), \
                    patch.object(baseline, "transcribe") as inference:
                with self.assertRaisesRegex(baseline.BaselineError, code):
                    baseline.smoke_test()
                inference.assert_not_called()
                self.assertFalse(self.generated_directory.exists())

    def test_smoke_refuses_missing_or_unready_baseline(self):
        with patch.object(baseline, "owned_container", return_value=""), \
                patch.object(baseline, "synthesize") as synth:
            with self.assertRaisesRegex(baseline.BaselineError, "BASELINE_NOT_RUNNING"):
                baseline.smoke_test()
            synth.assert_not_called()
        with patch.object(baseline, "owned_container", return_value=IDENTITY), \
                patch.object(baseline, "local_json", return_value={"status": "loading model"}):
            with self.assertRaisesRegex(baseline.BaselineError, "SERVER_NOT_READY"):
                baseline.smoke_test()

    def test_cli_prints_only_stable_failure(self):
        with patch.object(baseline.sys, "argv", ["baseline.py", "smoke-test"]), \
                patch.object(baseline, "smoke_test", side_effect=baseline.BaselineError("SAFE_CODE")), \
                redirect_stderr(io.StringIO()) as output:
            self.assertEqual(baseline.main(), 1)
        self.assertEqual(output.getvalue(), "Baseline failed: SAFE_CODE\n")


if __name__ == "__main__":
    unittest.main()
