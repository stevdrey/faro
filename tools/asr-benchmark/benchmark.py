#!/usr/bin/env python3
"""FARO-014 local ASR benchmark; synthetic CLI and opt-in adult voice recorder."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import platform
import sys
import tempfile
import time
import wave

from metrics import Scores, numbers, percentile


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
spec = importlib.util.spec_from_file_location("faro_whisper_baseline", ROOT / "infra/whisper/baseline.py")
baseline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(baseline)


class BenchmarkError(Exception):
    """Stable first-party diagnostics, without raw engine output."""


def corpus():
    raw = (HERE / "corpus.json").read_bytes()
    config = json.loads(raw)
    samples = config["samples"]
    if (config["schema_version"] != 1 or not samples or len(samples) > 60
            or len({s["id"] for s in samples}) != len(samples)
            or any(not s["critical_numbers"] or numbers(s["text"]) != s["critical_numbers"]
                   for s in samples)
            or not config["rates"] or len(set(config["rates"])) != len(config["rates"])
            or any(type(rate) is not int or not 80 <= rate <= 220 for rate in config["rates"])
            or not 0 < config["silence_seconds"] <= 30):
        raise BenchmarkError("CORPUS_INVALID")
    return config, hashlib.sha256(raw).hexdigest()


def wav_bytes(pcm):
    output = io.BytesIO()
    with wave.open(output, "wb") as audio:
        audio.setparams((1, 2, 16000, 0, "NONE", "not compressed"))
        audio.writeframes(pcm)
    return output.getvalue()


def prepare(config, directory):
    """Generate once; all repeats consume identical PCM/WAV bytes in memory."""
    clips = []
    fingerprint = hashlib.sha256()
    for sample in config["samples"]:
        for rate in config["rates"]:
            path = baseline.synthesize(directory, {
                **sample, "voice": config["voice"], "pitch": config["pitch"], "words_per_minute": rate})
            with wave.open(str(path), "rb") as audio:
                if ((audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getcomptype())
                        != (1, 2, 16000, "NONE") or not 0 < audio.getnframes() <= 480000):
                    raise BenchmarkError("INVALID_SYNTHETIC_WAV")
                pcm = audio.readframes(audio.getnframes())
            clips.append({**sample, "kind": "speech", "wav": wav_bytes(pcm),
                          "audio_seconds": len(pcm) / 32000})
    clips.append({"kind": "silence", "text": "", "critical_numbers": [],
                  "wav": wav_bytes(bytes(round(config["silence_seconds"] * 32000))),
                  "audio_seconds": config["silence_seconds"]})
    for clip in clips:
        fingerprint.update(len(clip["wav"]).to_bytes(8, "big"))
        fingerprint.update(clip["wav"])
    return clips, fingerprint.hexdigest()


class Whisper:
    """Runtime transport stays here; no changes to app providers or assessment."""
    runtime = baseline

    def __init__(self, *, runtime=None):
        self.runtime = runtime or baseline
        runtime = self.runtime
        host = os.environ.get("DOCKER_HOST", "")
        context = runtime.run_tool(["docker", "context", "inspect", "--format",
                                     "{{.Endpoints.docker.Host}}"], code="DOCKER_UNAVAILABLE").strip()
        if (host and not host.startswith("unix:///")) or not context.startswith("unix:///"):
            raise BenchmarkError("LOCAL_DOCKER_REQUIRED")
        self.config = runtime.manifest()
        runtime.verify_model(runtime.MODEL_PATH, self.config["model"])
        self.identity = runtime.owned_container()
        if not self.identity:
            raise BenchmarkError("BASELINE_NOT_RUNNING")
        info = json.loads(runtime.run_tool(["docker", "inspect", self.identity]))[0]
        expected = runtime.docker_start_args(self.config, "unused")
        image_pos = expected.index(self.config["runtime"]["image"])
        if (info["Config"]["Image"] != self.config["runtime"]["image"]
                or info["Config"]["Cmd"] != expected[image_pos + 1:]
                or info["Config"]["Entrypoint"] != ["/app/build/bin/whisper-server"]
                or info["HostConfig"]["LogConfig"]["Type"] != "none"
                or info["HostConfig"]["PortBindings"] != {
                    "8080/tcp": [{"HostIp": "127.0.0.1", "HostPort": runtime.BASE_URL.rsplit(":", 1)[1]}]}
                or not any(m["Destination"] == "/models/ggml-base.bin" and not m["RW"]
                           and m["Source"] == str(runtime.MODEL_PATH) for m in info["Mounts"])):
            raise BenchmarkError("BASELINE_CONFIGURATION_MISMATCH")
        if runtime.local_json("/health").get("status") != "ok":
            raise BenchmarkError("SERVER_NOT_READY")

    def transcribe(self, wav):
        data, headers = self.runtime.multipart(wav)
        try:
            result = self.runtime.local_json("/inference", data=data, headers=headers,
                                             timeout=self.runtime.INFERENCE_TIMEOUT)
        except self.runtime.BaselineError:
            raise BenchmarkError("PROVIDER_ERROR") from None
        text = result.get("text")
        if "error" in result or not isinstance(text, str) or len(text) > 16000:
            raise BenchmarkError("PROVIDER_ERROR")
        return text

    def resources(self):
        raw = self.runtime.run_tool(["docker", "exec", self.identity, "cat",
                                 "/sys/fs/cgroup/cpu.stat", "/sys/fs/cgroup/memory.current",
                                 "/sys/fs/cgroup/memory.peak"], code="RESOURCE_COUNTERS_UNAVAILABLE")
        lines = raw.strip().splitlines()
        try:
            cpu = dict(line.split() for line in lines[:-2])
            return {"cpu_usec": int(cpu["usage_usec"]), "memory_bytes": int(lines[-2]),
                    "memory_peak_bytes": int(lines[-1])}
        except (ValueError, KeyError, IndexError):
            raise BenchmarkError("RESOURCE_COUNTERS_INVALID") from None


def measure(clips, adapter, repeats, *, on_first_pass=None):
    """First pass scores unique clips; later passes measure latency/repeat stability."""
    adapter.transcribe(clips[0]["wav"])  # One warm-up, excluded from all scored counts.
    scores = Scores()
    first = []
    changed = set()
    latency = []
    failures = Counter()
    resources_before = adapter.resources()
    peak = resources_before["memory_peak_bytes"]
    for repeat in range(repeats):
        for index, clip in enumerate(clips):
            started = time.monotonic()
            try:
                text = adapter.transcribe(clip["wav"])
                failed = False
            except (baseline.BaselineError, BenchmarkError):
                # No retry or alternate engine; failures remain in the accuracy denominator.
                text, failed = "", True
                failures["technical_failure"] += 1
            elapsed = (time.monotonic() - started) * 1000
            latency.append(elapsed)
            observation = (failed, text)
            if repeat == 0:
                first.append(observation)
                scores.add(clip, text, failed=failed)
                if on_first_pass is not None:
                    on_first_pass(clip, text, failed)
            elif observation != first[index]:
                changed.add(index)
        current = adapter.resources()
        peak = max(peak, current["memory_peak_bytes"])
    if current["cpu_usec"] < resources_before["cpu_usec"]:
        raise BenchmarkError("RESOURCE_COUNTER_RESET")
    return {
        "scores": scores.summary(),
        "requests": len(latency), "repeats": repeats, "warmup_requests": 1,
        "technical_failures_all_passes": sum(failures.values()),
        "clips_with_repeat_disagreement": len(changed),
        "latency_ms": {"p50": percentile(latency, 50), "p95": percentile(latency, 95),
                       "scope": "all requests, HTTP round-trip; excludes synthesis, warm-up and resource probes"},
        "resources": {
            "cpu_seconds": (current["cpu_usec"] - resources_before["cpu_usec"]) / 1e6,
            "memory_before_bytes": resources_before["memory_bytes"],
            "memory_after_bytes": current["memory_bytes"],
            "container_lifetime_peak_bytes": peak,
            "scope": "Linux cgroup v2 container; peak includes startup/warm-up; CPU excludes startup/warm-up",
        },
    }


def environment(*, synthesis=True):
    cpu = "unknown"
    info = Path("/proc/cpuinfo")
    if info.exists():
        cpu = next((line.split(":", 1)[1].strip() for line in info.read_text().splitlines()
                    if line.startswith("model name")), cpu)
    result = {"os": platform.system(), "kernel": platform.release(), "machine": platform.machine(),
            "cpu_model": cpu, "logical_cpus": os.cpu_count(), "python": platform.python_version(),
            "docker": baseline.run_tool(["docker", "version", "--format", "{{.Server.Version}}"]).strip()}
    if synthesis:
        result.update(
            espeak_ng=baseline.run_tool(["espeak-ng", "--version"]).splitlines()[0].split("  Data at:")[0],
            ffmpeg=baseline.run_tool(["ffmpeg", "-version"]).splitlines()[0])
    return result


def run(repeats):
    config, digest = corpus()
    adapter = Whisper()
    with tempfile.TemporaryDirectory(prefix="faro-benchmark-") as directory:
        clips, fingerprint = prepare(config, Path(directory))
        result = measure(clips, adapter, repeats)
    dataset = {"id": config["id"], "kind": "synthetic-only", "manifest_sha256": digest,
                    "audio_sha256": fingerprint, "unique_clips": len(clips),
                    "audio_seconds": round(sum(c["audio_seconds"] for c in clips), 3),
                    "voice": config["voice"], "rates": config["rates"], "pitch": config["pitch"]}
    return make_report(adapter, dataset, result, synthesis=True)


def candidate_result(adapter, result):
    return {"engine": "whisper.cpp", "status": "measured",
            "runtime_image": adapter.config["runtime"]["image"],
            "runtime_revision": adapter.config["runtime"]["source_revision"],
            "model_id": adapter.config["model"]["id"],
            "model_revision": adapter.config["model"]["revision"],
            "model_sha256": adapter.config["model"]["sha256"],
            "model_size_bytes": adapter.config["model"]["size_bytes"],
            "language": "es", "threads": 4, **result}


def make_report(adapter, dataset, result, *, synthesis):
    return {
        "schema_version": 1, "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset": dataset,
        "environment": environment(synthesis=synthesis),
        "candidates": [candidate_result(adapter, result),
                       {"engine": "moonshine", "status": "not_measured", "reason": "adapter_not_installed"},
                       {"engine": "vosk", "status": "not_measured", "reason": "adapter_not_installed"}],
        "limitations": [("Single synthetic formant voice; no child-speech or population inference." if synthesis
                          else "Single consenting adult's own voice; no child-speech or population inference."),
                        "No measured challenger; no replacement ranking.",
                        "Scoring handles nonnegative integers 0–99 in Spanish and digit tokens only.",
                        "Number agreement is not mathematical meaning, assessment safety or false-child-error attribution.",
                        "FARO-024 retry/clarification dependency and FARO-027 adult annotation remain open."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repeats", type=int, choices=range(1, 6), default=3, metavar="1..5")
    args = parser.parse_args()
    try:
        report = run(args.repeats)
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 1 if report["candidates"][0]["technical_failures_all_passes"] else 0
    except (baseline.BaselineError, BenchmarkError) as error:
        print(f"Benchmark failed: {error}", file=sys.stderr)
    except (OSError, ValueError, KeyError, TypeError, IndexError, wave.Error, EOFError):
        print("Benchmark failed: LOCAL_OPERATION_FAILED", file=sys.stderr)
    except KeyboardInterrupt:
        print("Benchmark interrupted.", file=sys.stderr)
        return 130
    return 1


if __name__ == "__main__":
    sys.exit(main())
