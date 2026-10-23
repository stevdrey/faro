#!/usr/bin/env python3
"""Explicit, standalone FARO-010 developer commands; no third-party Python packages."""

import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import wave


HERE = Path(__file__).resolve().parent
MODEL_PATH = HERE / "models" / "ggml-base.bin"
CONTAINER = "faro-whisper-baseline"
OWNER_LABEL = "io.github.stevdrey.faro.baseline=whisper"
LAUNCH_LABEL = "io.github.stevdrey.faro.launch"
BASE_URL = "http://127.0.0.1:8085"
START_TIMEOUT = 120
INFERENCE_TIMEOUT = 120
MAX_RESPONSE_BYTES = 1024 * 1024


class BaselineError(Exception):
    """Only stable first-party codes are printed; never vendor output or response bodies."""


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if fp is not None:
            fp.close()
        raise BaselineError("LOCAL_HTTP_REDIRECT_REJECTED")


class HttpsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not newurl.startswith("https://"):
            if fp is not None:
                fp.close()
            raise BaselineError("DOWNLOAD_INSECURE_REDIRECT")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def manifest():
    return json.loads((HERE / "artifacts.json").read_text(encoding="utf-8"))


def run_tool(args, *, timeout=30, code="TOOL_FAILED"):
    try:
        result = subprocess.run(args, capture_output=True, check=True, timeout=timeout)
        return result.stdout.decode("utf-8")
    except (OSError, subprocess.SubprocessError, UnicodeError):
        raise BaselineError(code) from None


def verify_model(path, spec):
    if not path.is_file() or path.is_symlink():
        raise BaselineError("MODEL_MISSING_OR_SYMLINK")
    if path.stat().st_size != spec["size_bytes"]:
        raise BaselineError("MODEL_SIZE_MISMATCH")
    digest = hashlib.sha256()
    with path.open("rb") as model:
        for chunk in iter(lambda: model.read(1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != spec["sha256"]:
        raise BaselineError("MODEL_CHECKSUM_MISMATCH")


def download_model(path, spec):
    """Stage alongside the destination; never publish incomplete/unverified weights."""
    path.parent.mkdir(parents=True, exist_ok=True)
    staged = None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), HttpsRedirect())
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, suffix=".part", delete=False) as output:
            staged = Path(output.name)
            with opener.open(spec["url"], timeout=60) as response:
                received = 0
                deadline = time.monotonic() + 300
                while chunk := response.read(1024 * 1024):
                    received += len(chunk)
                    if received > spec["size_bytes"] or time.monotonic() > deadline:
                        raise BaselineError("DOWNLOAD_SIZE_OR_TIME_LIMIT")
                    output.write(chunk)
        verify_model(staged, spec)
        # The unprivileged container needs read access; weights are public, not audio.
        staged.chmod(0o644)
        os.replace(staged, path)
    except urllib.error.HTTPError as error:
        error.close()
        raise BaselineError("MODEL_DOWNLOAD_FAILED") from None
    except (OSError, urllib.error.URLError, http.client.HTTPException):
        raise BaselineError("MODEL_DOWNLOAD_FAILED") from None
    finally:
        if staged is not None:
            staged.unlink(missing_ok=True)


def setup():
    spec = manifest()
    if MODEL_PATH.exists() or MODEL_PATH.is_symlink():
        verify_model(MODEL_PATH, spec["model"])
    else:
        download_model(MODEL_PATH, spec["model"])
    run_tool(["docker", "pull", "--platform", spec["runtime"]["platform"],
              spec["runtime"]["image"]], timeout=900, code="IMAGE_PULL_FAILED")
    print("Setup ready: pinned CPU image and verified base-multilingual model.")


def docker_start_args(spec, launch_token):
    # Docker's volume parser treats ':' as a separator even without a shell.
    if ":" in str(MODEL_PATH):
        raise BaselineError("UNSUPPORTED_MODEL_PATH")
    return [
        "docker", "run", "--detach", "--rm", "--pull=never",
        "--platform", spec["runtime"]["platform"], "--name", CONTAINER,
        "--label", OWNER_LABEL, "--label", f"{LAUNCH_LABEL}={launch_token}",
        "--user", "65534:65534", "--read-only",
        "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
        "--log-driver", "none", "--publish", "127.0.0.1:8085:8080",
        # Z privately labels this one public model file on SELinux hosts.
        "--volume", f"{MODEL_PATH}:/models/ggml-base.bin:ro,Z",
        "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m,mode=1777", "--workdir", "/tmp",
        "--entrypoint", "/app/build/bin/whisper-server", spec["runtime"]["image"],
        "--model", "/models/ggml-base.bin", "--language", "es",
        "--host", "0.0.0.0", "--port", "8080", "--no-gpu", "--max-context", "0",
        "--threads", "4", "--no-timestamps",
    ]


def local_json(path, *, data=None, headers=None, timeout=5):
    # Ignore HTTP_PROXY/HTTPS_PROXY and reject redirects, including for POST audio.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    request = urllib.request.Request(BASE_URL + path, data=data, headers=headers or {})
    try:
        with opener.open(request, timeout=timeout) as response:
            body = response.read(MAX_RESPONSE_BYTES + 1)
            if len(body) > MAX_RESPONSE_BYTES:
                raise BaselineError("LOCAL_HTTP_RESPONSE_TOO_LARGE")
            result = json.loads(body)
            if not isinstance(result, dict):
                raise BaselineError("LOCAL_HTTP_INVALID_JSON")
            return result
    except urllib.error.HTTPError as error:
        error.close()
        raise BaselineError("LOCAL_HTTP_STATUS_FAILED") from None
    except (OSError, urllib.error.URLError, http.client.HTTPException):
        raise BaselineError("LOCAL_HTTP_UNAVAILABLE_OR_TIMEOUT") from None
    except (ValueError, UnicodeError):
        raise BaselineError("LOCAL_HTTP_INVALID_JSON") from None


def owned_container(launch_token=None):
    args = [
        "docker", "container", "ls", "--all", "--no-trunc",
        "--filter", f"name=^/{CONTAINER}$", "--filter", f"label={OWNER_LABEL}",
    ]
    if launch_token is not None:
        args.extend(["--filter", f"label={LAUNCH_LABEL}={launch_token}"])
    args.extend(["--format", "{{.ID}}"])
    identity = run_tool(args, code="DOCKER_UNAVAILABLE").strip()
    if identity and not re.fullmatch(r"[0-9a-f]{64}", identity):
        raise BaselineError("DOCKER_INVALID_CONTAINER_ID")
    return identity


def wait_ready(identity):
    deadline = time.monotonic() + START_TIMEOUT
    while time.monotonic() < deadline:
        running = run_tool(["docker", "inspect", "--format", "{{.State.Running}}", identity],
                           code="CONTAINER_EXITED").strip()
        if running != "true":
            raise BaselineError("CONTAINER_EXITED")
        try:
            if local_json("/health", timeout=1).get("status") == "ok":
                return
        except BaselineError:
            pass
        time.sleep(0.5)
    raise BaselineError("SERVER_START_TIMEOUT")


def start():
    spec = manifest()
    verify_model(MODEL_PATH, spec["model"])
    launch_token = uuid.uuid4().hex
    try:
        identity = run_tool(docker_start_args(spec, launch_token), timeout=60,
                            code="DOCKER_START_FAILED").strip()
        if not re.fullmatch(r"[0-9a-f]{64}", identity) or owned_container(launch_token) != identity:
            raise BaselineError("DOCKER_INVALID_CONTAINER_ID")
        wait_ready(identity)
    except (BaselineError, KeyboardInterrupt):
        # Recover by invocation label even if docker run never returned a usable ID.
        # A preexisting/replacement service has a different token and is left alone.
        try:
            abandoned = owned_container(launch_token)
            if abandoned:
                run_tool(["docker", "rm", "--force", abandoned], code="START_FAILED_CLEANUP_FAILED")
        except BaselineError:
            raise BaselineError("START_FAILED_CLEANUP_FAILED") from None
        raise
    print(f"Server ready: {BASE_URL}; CPU, base-multilingual, Spanish (es).")


def stop():
    identity = owned_container()
    if identity:
        run_tool(["docker", "stop", "--time", "10", identity], timeout=30,
                 code="DOCKER_STOP_FAILED")
        # Docker can acknowledge stop before --rm has released the container name.
        deadline = time.monotonic() + 30
        while owned_container() == identity:
            if time.monotonic() >= deadline:
                raise BaselineError("DOCKER_REMOVE_TIMEOUT")
            time.sleep(0.25)
    print("Baseline stopped (or already absent).")


def synthesize(directory, sample):
    original = directory / "synthetic.wav"
    normalized = directory / "normalized.wav"
    run_tool(["espeak-ng", "-v", sample["voice"], "-s", str(sample["words_per_minute"]),
              "-p", str(sample["pitch"]), "-w", str(original), sample["text"]],
             code="SYNTHESIS_FAILED")
    run_tool(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
              "-i", str(original), "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le",
              str(normalized)], code="NORMALIZATION_FAILED")
    return normalized


def multipart(wav):
    boundary = "faro-" + uuid.uuid4().hex
    parts = []
    for name, value in (("language", "es"), ("response_format", "json"),
                        ("temperature", "0.0"), ("translate", "false")):
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"'
                     f'\r\n\r\n{value}\r\n'.encode("ascii"))
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
                 'filename="synthetic.wav"\r\nContent-Type: audio/wav\r\n\r\n'.encode("ascii"))
    parts.extend([wav, f"\r\n--{boundary}--\r\n".encode("ascii")])
    return b"".join(parts), {"Content-Type": f"multipart/form-data; boundary={boundary}"}


def transcribe(wav):
    data, headers = multipart(wav)
    result = local_json("/inference", data=data, headers=headers, timeout=INFERENCE_TIMEOUT)
    text = result.get("text")
    if "error" in result or not isinstance(text, str) or not text.strip():
        raise BaselineError("INFERENCE_EMPTY_OR_INVALID_TEXT")
    return text


def smoke_test():
    if not owned_container():
        raise BaselineError("BASELINE_NOT_RUNNING")
    if local_json("/health").get("status") != "ok":
        raise BaselineError("SERVER_NOT_READY")
    spec = manifest()
    with tempfile.TemporaryDirectory(prefix="faro-whisper-") as directory:
        wav_path = synthesize(Path(directory), spec["sample"])
        with wave.open(str(wav_path), "rb") as audio:
            if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(),
                    audio.getcomptype()) != (1, 2, 16000, "NONE"):
                raise BaselineError("INVALID_SYNTHETIC_WAV")
            duration = audio.getnframes() / audio.getframerate()
        if not 0 < duration <= 30:
            raise BaselineError("INVALID_SYNTHETIC_DURATION")
        started = time.monotonic()
        text = transcribe(wav_path.read_bytes())
        summary = {"status": "passed", "model": spec["model"]["id"],
                   "language_requested": "es", "audio_seconds": round(duration, 3),
                   "inference_ms": round((time.monotonic() - started) * 1000),
                   "transcript_characters": len(text)}
    print(json.dumps(summary))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("setup", "start", "stop", "smoke-test"))
    command = parser.parse_args().command
    try:
        {"setup": setup, "start": start, "stop": stop, "smoke-test": smoke_test}[command]()
        return 0
    except BaselineError as error:
        print(f"Baseline failed: {error}", file=sys.stderr)
    except (OSError, wave.Error, EOFError):
        print("Baseline failed: LOCAL_FILE_OPERATION_FAILED", file=sys.stderr)
    except KeyboardInterrupt:
        print("Baseline interrupted.", file=sys.stderr)
        return 130
    return 1


if __name__ == "__main__":
    sys.exit(main())
