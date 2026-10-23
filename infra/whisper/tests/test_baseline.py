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
REAL_MANIFEST = baseline.manifest


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

    def fast_clock(self):
        """Advance monotonic time one second per read and skip real sleeps."""
        ticks = iter(range(10_000))
        patch.object(baseline.time, "monotonic", side_effect=lambda: next(ticks)).start()
        return patch.object(baseline.time, "sleep").start()

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

    def test_download_honors_environment_proxies_and_has_no_total_deadline(self):
        with self.fake_download(self.weights) as build, \
                patch.object(baseline.time, "monotonic", side_effect=AssertionError("deadline")):
            baseline.download_model(self.model, self.config["model"])
        handlers = build.call_args.args
        self.assertFalse(any(isinstance(handler, baseline.urllib.request.ProxyHandler)
                             for handler in handlers))
        self.assertIsInstance(handlers[0], baseline.RedirectGuard)
        self.assertTrue(handlers[0].allow_https)
        self.assertEqual(build.return_value.open.call_args.kwargs["timeout"], 60)

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
        self.model.chmod(0o600)
        with patch.object(baseline, "download_model") as download, \
                patch.object(baseline, "run_tool") as run, redirect_stdout(io.StringIO()):
            baseline.setup()
        download.assert_not_called()
        self.assertEqual(self.model.stat().st_mode & 0o777, 0o644)
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
        args = baseline.docker_start_args(self.config, "c" * 32)
        self.assertIn("io.github.stevdrey.faro.launch=" + "c" * 32, args)
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
                patch.object(baseline, "owned_container", return_value=IDENTITY), \
                patch.object(baseline, "wait_ready") as ready, redirect_stdout(io.StringIO()):
            baseline.start()
        ready.assert_called_once_with(IDENTITY)

    def launch_client(self, outcome, *, existing=False, replacement=False, cleanup_fails=False,
                      late=False):
        """Simulate a daemon that creates a service before its client fails."""
        owner_key, owner_value = baseline.OWNER_LABEL.split("=", 1)
        launch_key = "io.github.stevdrey.faro.launch"
        foreign_labels = {owner_key: owner_value, launch_key: "another-invocation"}
        containers = {IDENTITY: foreign_labels} if existing else {}
        removed = []
        pending = []
        listings = []

        def client(args, **kwargs):
            if args[:2] == ["docker", "run"]:
                if existing:
                    raise subprocess.CalledProcessError(125, args, stderr=b"name occupied")
                labels = dict(args[index + 1].split("=", 1) for index, value in enumerate(args)
                              if value == "--label")
                if late:
                    pending.append(labels)
                    raise outcome
                containers[IDENTITY] = labels
                if replacement:
                    containers.clear()
                    containers["b" * 64] = foreign_labels
                if isinstance(outcome, BaseException):
                    raise outcome
                return subprocess.CompletedProcess(args, 0, stdout=outcome)
            if args[:3] == ["docker", "container", "ls"]:
                if pending:
                    listings.append(args)
                    if len(listings) >= 3:  # The daemon finishes the create after two empty lists.
                        containers[IDENTITY] = pending.pop()
                labels = [args[index + 1][len("label="):].split("=", 1)
                          for index, value in enumerate(args)
                          if value == "--filter" and args[index + 1].startswith("label=")]
                identities = [identity for identity, present in containers.items()
                              if all(present.get(key) == value for key, value in labels)]
                return subprocess.CompletedProcess(args, 0, stdout="\n".join(identities).encode())
            if args[:3] == ["docker", "rm", "--force"]:
                if cleanup_fails:
                    raise subprocess.CalledProcessError(1, args, stderr=b"private diagnostic")
                removed.append(args[3])
                containers.pop(args[3])
                return subprocess.CompletedProcess(args, 0, stdout=b"")
            raise AssertionError(f"Unexpected simulated Docker command: {args[:3]}")

        return client, containers, removed

    def test_launch_failures_remove_service_created_before_id_returned(self):
        self.install_model()
        self.fast_clock()
        failures = [subprocess.TimeoutExpired("docker", 60), KeyboardInterrupt(),
                    subprocess.CalledProcessError(125, "docker", stderr=b"private diagnostic"),
                    b"invalid-container-id", b"\xff", b"b" * 64]
        for failure in failures:
            client, containers, removed = self.launch_client(failure)
            expected = KeyboardInterrupt if isinstance(failure, KeyboardInterrupt) else baseline.BaselineError
            with self.subTest(failure=type(failure).__name__), \
                    patch.object(baseline.subprocess, "run", side_effect=client), \
                    patch.object(baseline, "wait_ready") as ready:
                with self.assertRaises(expected):
                    baseline.start()
                ready.assert_not_called()
                self.assertEqual(containers, {})
                self.assertEqual(removed, [IDENTITY])

    def test_cleanup_waits_for_container_created_after_client_was_killed(self):
        self.install_model()
        sleep = self.fast_clock()
        for failure in (subprocess.TimeoutExpired("docker", 60), KeyboardInterrupt()):
            client, containers, removed = self.launch_client(failure, late=True)
            with self.subTest(failure=type(failure).__name__), \
                    patch.object(baseline.subprocess, "run", side_effect=client):
                with self.assertRaises((baseline.BaselineError, KeyboardInterrupt)):
                    baseline.start()
                self.assertEqual(containers, {})
                self.assertEqual(removed, [IDENTITY])
        self.assertTrue(sleep.called)

    def test_cleanup_grace_is_bounded_when_nothing_was_created(self):
        self.install_model()
        self.fast_clock()
        client, _, removed = self.launch_client(subprocess.TimeoutExpired("docker", 60))
        listings = []

        def no_create(args, **kwargs):
            if args[:2] == ["docker", "run"]:
                raise subprocess.TimeoutExpired("docker", 60)
            listings.append(args)
            return client(args, **kwargs)

        with patch.object(baseline.subprocess, "run", side_effect=no_create):
            with self.assertRaisesRegex(baseline.BaselineError, "^DOCKER_START_FAILED$"):
                baseline.start()
        self.assertGreater(len(listings), 1)
        self.assertLessEqual(len(listings), baseline.CLEANUP_GRACE + 1)
        self.assertEqual(removed, [])

    def test_launch_failure_preserves_preexisting_and_replacement_services(self):
        self.install_model()
        self.fast_clock()
        for existing, replacement in ((True, False), (False, True)):
            client, containers, removed = self.launch_client(
                subprocess.TimeoutExpired("docker", 60), existing=existing, replacement=replacement)
            with self.subTest(existing=existing, replacement=replacement), \
                    patch.object(baseline.subprocess, "run", side_effect=client):
                with self.assertRaises(baseline.BaselineError):
                    baseline.start()
                self.assertEqual(len(containers), 1)
                self.assertEqual(removed, [])

    def test_launch_cleanup_failure_uses_stable_error(self):
        self.install_model()
        self.fast_clock()
        client, containers, removed = self.launch_client(
            subprocess.TimeoutExpired("docker", 60), cleanup_fails=True)
        with patch.object(baseline.subprocess, "run", side_effect=client):
            with self.assertRaisesRegex(baseline.BaselineError, "^START_FAILED_CLEANUP_FAILED$"):
                baseline.start()

    def test_launch_tokens_separate_successive_attempts(self):
        self.install_model()
        client, containers, removed = self.launch_client(IDENTITY.encode())
        with patch.object(baseline.subprocess, "run", side_effect=client) as run, \
                patch.object(baseline, "wait_ready"), redirect_stdout(io.StringIO()):
            baseline.start()
            containers.clear()  # Simulate stopping the first service before the next start.
            baseline.start()
        launches = [call.args[0] for call in run.call_args_list if call.args[0][:2] == ["docker", "run"]]
        tokens = [next(arg for arg in args if arg.startswith("io.github.stevdrey.faro.launch="))
                  for args in launches]
        self.assertEqual(len(tokens), 2)
        self.assertNotEqual(tokens[0], tokens[1])
        self.assertEqual(removed, [])

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

    def test_start_does_not_remove_already_removed_container(self):
        self.install_model()
        with patch.object(baseline, "run_tool", return_value=IDENTITY) as run, \
                patch.object(baseline, "owned_container", side_effect=[IDENTITY, ""]), \
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

    def test_readiness_distinguishes_removed_container_from_docker_failure(self):
        inspect_failed = baseline.BaselineError("DOCKER_UNAVAILABLE")
        for present, code in (("", "^CONTAINER_EXITED$"), (IDENTITY, "^DOCKER_UNAVAILABLE$")):
            with self.subTest(present=bool(present)), \
                    patch.object(baseline, "run_tool", side_effect=inspect_failed), \
                    patch.object(baseline, "owned_container", return_value=present):
                with self.assertRaisesRegex(baseline.BaselineError, code):
                    baseline.wait_ready(IDENTITY)
        with patch.object(baseline, "run_tool", side_effect=inspect_failed), \
                patch.object(baseline, "owned_container",
                             side_effect=baseline.BaselineError("DOCKER_UNAVAILABLE")):
            with self.assertRaisesRegex(baseline.BaselineError, "^DOCKER_UNAVAILABLE$"):
                baseline.wait_ready(IDENTITY)

    def test_readiness_calls_never_outlive_the_start_deadline(self):
        with patch.object(baseline.time, "monotonic", side_effect=[0, 119.5]), \
                patch.object(baseline, "run_tool", return_value="true") as run, \
                patch.object(baseline, "local_json", return_value={"status": "ok"}) as http:
            baseline.wait_ready(IDENTITY)
        self.assertEqual(run.call_args.kwargs["timeout"], 1)
        self.assertEqual(http.call_args.kwargs["timeout"], 0.5)
        with patch.object(baseline.time, "monotonic", side_effect=[0, 100]), \
                patch.object(baseline, "run_tool", return_value="true") as run, \
                patch.object(baseline, "local_json", return_value={"status": "ok"}):
            baseline.wait_ready(IDENTITY)
        self.assertEqual(run.call_args.kwargs["timeout"], 20)

    def test_readiness_detects_exit_and_deadline(self):
        with patch.object(baseline, "run_tool", return_value="false"):
            with self.assertRaisesRegex(baseline.BaselineError, "CONTAINER_EXITED"):
                baseline.wait_ready(IDENTITY)
        with patch.object(baseline.time, "monotonic", side_effect=[0, 121]):
            with self.assertRaisesRegex(baseline.BaselineError, "SERVER_START_TIMEOUT"):
                baseline.wait_ready(IDENTITY)

    def test_stop_is_owned_idempotent_and_forcibly_removes_any_state(self):
        with patch.object(baseline, "run_tool", side_effect=[IDENTITY, ""]) as run, \
                patch.object(baseline.time, "sleep") as sleep, redirect_stdout(io.StringIO()):
            baseline.stop()
        listing = run.call_args_list[0].args[0]
        self.assertIn("name=^/faro-whisper-baseline$", listing)
        self.assertIn("label=" + baseline.OWNER_LABEL, listing)
        self.assertIn("--all", listing)  # Created/exited containers are also removed.
        self.assertEqual(run.call_args_list[1].args[0], ["docker", "rm", "--force", IDENTITY])
        self.assertEqual(run.call_count, 2)
        sleep.assert_not_called()
        with patch.object(baseline, "run_tool", return_value="") as run, \
                redirect_stdout(io.StringIO()):
            baseline.stop()
        self.assertEqual(run.call_count, 1)

    def test_stop_removal_failure_is_not_reported_as_success(self):
        with patch.object(baseline, "run_tool",
                          side_effect=[IDENTITY, baseline.BaselineError("DOCKER_STOP_FAILED")]):
            with self.assertRaisesRegex(baseline.BaselineError, "^DOCKER_STOP_FAILED$"):
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
        self.assertIsInstance(build.call_args.args[1], baseline.RedirectGuard)
        self.assertFalse(build.call_args.args[1].allow_https)
        self.assertEqual(opener.open.call_args.args[0].full_url, "http://127.0.0.1:8085/health")
        for code in (301, 302, 303, 307, 308):
            with self.subTest(code=code), self.assertRaisesRegex(baseline.BaselineError, "REDIRECT_REJECTED"):
                baseline.RedirectGuard("LOCAL_HTTP_REDIRECT_REJECTED").redirect_request(
                    None, None, code, "", {}, "https://remote.invalid")
        with self.assertRaisesRegex(baseline.BaselineError, "DOWNLOAD_INSECURE_REDIRECT"):
            baseline.RedirectGuard("DOWNLOAD_INSECURE_REDIRECT", allow_https=True).redirect_request(
                None, None, 302, "", {}, "http://remote.invalid")

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

    def test_invalid_manifest_uses_stable_code(self):
        valid = json.loads((MODULE_PATH.parent / "artifacts.json").read_text())
        missing_key = copy.deepcopy(valid)
        del missing_key["model"]["sha256"]
        cases = {"invalid json": "{\"runtime\": {},}", "not object": "[]",
                 "missing section": json.dumps({"runtime": valid["runtime"]}),
                 "missing key": json.dumps(missing_key), "absent file": None}
        for name, content in cases.items():
            directory = self.directory / name
            directory.mkdir()
            if content is not None:
                (directory / "artifacts.json").write_text(content)
            with self.subTest(case=name), patch.object(baseline, "HERE", directory):
                with self.assertRaisesRegex(baseline.BaselineError, "^MANIFEST_INVALID$"):
                    REAL_MANIFEST()
        with patch.object(baseline, "HERE", self.directory / "invalid json"), \
                patch.object(baseline, "manifest", REAL_MANIFEST), \
                patch.object(baseline.sys, "argv", ["baseline.py", "start"]), \
                redirect_stderr(io.StringIO()) as output:
            self.assertEqual(baseline.main(), 1)
        self.assertEqual(output.getvalue(), "Baseline failed: MANIFEST_INVALID\n")

    def test_cli_prints_only_stable_failure(self):
        with patch.object(baseline.sys, "argv", ["baseline.py", "smoke-test"]), \
                patch.object(baseline, "smoke_test", side_effect=baseline.BaselineError("SAFE_CODE")), \
                redirect_stderr(io.StringIO()) as output:
            self.assertEqual(baseline.main(), 1)
        self.assertEqual(output.getvalue(), "Baseline failed: SAFE_CODE\n")


if __name__ == "__main__":
    unittest.main()
