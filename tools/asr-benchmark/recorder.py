#!/usr/bin/env python3
"""Loopback-only guided recording of the consenting adult's own voice, in memory."""

import argparse
import base64
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import json
import secrets
import sys
import threading
import wave

import benchmark as bench
from metrics import transcript_diagnostics
from recovery import RecoveryError, RecoveryStore


MAX_BODY = 16 * 1024 * 1024


class LocalServer(ThreadingHTTPServer):
    def handle_error(self, request, client_address):
        pass  # Includes disconnected clients: do not emit raw request/exception diagnostics.


def adult_clips(payload):
    config, digest = bench.corpus()
    samples = config["samples"]
    if (not isinstance(payload, dict) or payload.get("adult_own_voice") is not True
            or payload.get("references_confirmed") is not True
            or not isinstance(payload.get("clips"), list) or len(payload["clips"]) != len(samples)):
        raise bench.BenchmarkError("ADULT_CONFIRMATION_OR_CORPUS_INVALID")
    clips = []
    for sample, item in zip(samples, payload["clips"]):
        if not isinstance(item, dict) or item.get("id") != sample["id"]:
            raise bench.BenchmarkError("ADULT_CORPUS_INVALID")
        encoded = item.get("wav_base64")
        if not isinstance(encoded, str) or len(encoded) > 1_300_000:
            raise bench.BenchmarkError("AUDIO_LIMIT")
        raw = base64.b64decode(encoded, validate=True)
        if len(raw) > 1_000_000:
            raise bench.BenchmarkError("AUDIO_LIMIT")
        with wave.open(io.BytesIO(raw), "rb") as audio:
            frames = audio.getnframes()
            if ((audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getcomptype())
                    != (1, 2, 16000, "NONE") or not 0 < frames <= 480000):
                raise bench.BenchmarkError("INVALID_ADULT_WAV")
            pcm = audio.readframes(frames)
            if len(pcm) != frames * 2:
                raise bench.BenchmarkError("INVALID_ADULT_WAV")
        clips.append({**sample, "kind": "speech", "wav": bench.wav_bytes(pcm),
                      "audio_seconds": frames / 16000})
    clips.append({"kind": "silence", "text": "", "critical_numbers": [],
                  "wav": bench.wav_bytes(bytes(round(config["silence_seconds"] * 32000))),
                  "audio_seconds": config["silence_seconds"]})
    dataset = {"id": "adult-own-voice-local", "kind": "adult-own-voice",
               "reference_manifest_sha256": digest, "unique_clips": len(clips),
               "audio_seconds": round(sum(c["audio_seconds"] for c in clips), 3),
               "reference_review": "speaker confirmed exact reading after local playback",
               "silence_control": "generated PCM silence, not a microphone recording"}
    return clips, dataset


def observer(comparisons):
    def observe(clip, text, failed):
        extracted = None if failed else bench.numbers(text)
        comparisons.append({
            "ordinal": len(comparisons) + 1, "kind": clip["kind"],
            "reference_text": clip["text"],
            "transcript": None if failed else text,
            "status": "technical_failure" if failed else ("transcribed" if text.strip() else "empty"),
            "expected_numbers": list(clip["critical_numbers"]),
            "extracted_numbers": extracted,
            "numbers_match": (extracted == clip["critical_numbers"]
                              if not failed and clip["kind"] == "speech" else None),
            "transcript_diagnostics": None if failed else transcript_diagnostics(text),
        })
    return observe


def adult_report(payload, *, compare_small=False, prepared=None):
    clips, dataset = prepared or adult_clips(payload)
    adapter = bench.Whisper()
    small = None
    if compare_small:
        try:
            from models import small_runtime
            small = bench.Whisper(runtime=small_runtime())  # Admit both models before any audio is sent.
        except Exception:
            raise bench.BenchmarkError("SMALL_RUNTIME_UNAVAILABLE") from None
    # Warm every admitted model before scoring, so a later warm-up failure discards nothing.
    for model in (adapter, small):
        if model is not None:
            bench.warm_up(clips, model)
    comparisons = []
    result = bench.measure(clips, adapter, 3, on_first_pass=observer(comparisons), warmed=True)
    report = bench.make_report(adapter, dataset, result, synthesis=False)
    # Only the no-store adult response receives first-pass details; never the CLI/report builder.
    report["phrase_comparisons"] = comparisons
    if small is not None:
        small_comparisons = []
        result = bench.measure(clips, small, 3, on_first_pass=observer(small_comparisons), warmed=True)
        report["candidates"].insert(1, bench.candidate_result(small, result))
        report["small_phrase_comparisons"] = small_comparisons
        report["comparison"] = {"matched_inputs": "identical canonical WAV bytes from one validated request",
                                "order": ["base-multilingual", "small-multilingual"],
                                "repeats_per_model": 3, "parallel_inference": False}
        report["limitations"] = [item for item in report["limitations"] if item != "No measured challenger; no replacement ranking."]
        report["limitations"].append("Fixed model order on one adult corpus; no population or child-speech inference.")
    return report


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Never retain request paths, raw errors, audio or transcripts in access logs.

    def respond(self, status, body, content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Permissions-Policy", "microphone=(self)")
        self.send_header("Content-Security-Policy", "default-src 'none'; script-src 'self'; "
                         "style-src 'unsafe-inline'; connect-src 'self'; media-src blob:; "
                         "frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
        self.end_headers()
        self.wfile.write(body)

    def route(self):
        if self.headers.get("Host") != self.server.origin.removeprefix("http://"):
            return None
        prefix = "/" + self.server.token + "/"
        return self.path[len(prefix):] if self.path.startswith(prefix) else None

    def do_GET(self):
        route = self.route()
        if route in ("", "record.js"):
            filename = "recorder.html" if route == "" else route
            body = (bench.HERE / filename).read_bytes()
            self.respond(200, body, "text/html; charset=utf-8" if route == "" else "text/javascript; charset=utf-8")
        elif route == "corpus":
            config, _ = bench.corpus()
            self.respond(200, json.dumps(config["samples"], ensure_ascii=False).encode())
        elif route == "recovery" and getattr(self.server, "recovery", None) is not None:
            try:
                self.respond(200, json.dumps(self.server.recovery.restore()).encode())
            except Exception:
                self.respond(409, b'{"code":"RECOVERY_UNAVAILABLE"}')
        elif route == "settings":
            store = getattr(self.server, "recovery", None)
            self.respond(200, json.dumps({"compare_small": getattr(self.server, "compare_small", False),
                                          "recovery": store.info() if store else {"enabled": False}}).encode())
        else:
            self.respond(404, b'{"code":"NOT_FOUND"}')

    def do_POST(self):
        if (self.route() not in ("measure", "recovery-clear", "recovery-save") or self.headers.get("Origin") != self.server.origin
                or self.headers.get("Content-Type") != "application/json"
                or self.headers.get("Transfer-Encoding") is not None):
            self.respond(403, b'{"code":"REQUEST_REJECTED"}')
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            size = 0
        if not 0 < size <= MAX_BODY:
            self.respond(413, b'{"code":"AUDIO_LIMIT"}')
            return
        measuring = self.route() == "measure"
        if measuring and not self.server.busy.acquire(blocking=False):
            self.respond(409, b'{"code":"BUSY"}')
            return
        try:
            self.connection.settimeout(30)
            raw = self.rfile.read(size)
            if len(raw) != size:
                raise ValueError()
            payload = json.loads(raw)
            store = getattr(self.server, "recovery", None)
            if self.route() == "recovery-clear":
                self.respond(200, json.dumps(store.clear() if store is not None else {"enabled": False}).encode())
                return
            if self.route() == "recovery-save":
                if (store is None or payload.get("adult_own_voice") is not True
                        or payload.get("references_confirmed") is not True
                        or not isinstance(payload.get("recovery_session_id"), str)):
                    raise RecoveryError("RECOVERY_CONFIRMATION_REQUIRED")
                item = payload["clip"]
                encoded = item["wav_base64"]
                if not isinstance(encoded, str) or len(encoded) > 1_300_000:
                    raise RecoveryError("RECOVERY_AUDIO_INVALID")
                raw_wav = base64.b64decode(encoded, validate=True)
                result = store.save([{"id": item["id"], "kind": "speech", "wav": raw_wav}],
                                    session_id=payload["recovery_session_id"])
                self.respond(200, json.dumps(result).encode())
                return
            if store is not None and not isinstance(payload.get("recovery_session_id"), str):
                raise RecoveryError("RECOVERY_CONFIRMATION_REQUIRED")
            prepared = None
            if store is not None:
                prepared = adult_clips(payload)  # Decoded once; reused for the checkpoint and measurement.
                store.save(prepared[0], session_id=payload["recovery_session_id"])
            report = adult_report(payload, compare_small=getattr(self.server, "compare_small", False),
                                  prepared=prepared)
            if store is not None:
                report["recovery"] = store.info()
            self.respond(200, json.dumps(report, ensure_ascii=False).encode())
        except RecoveryError:
            self.respond(409, b'{"code":"RECOVERY_UNAVAILABLE"}')
        except (bench.BenchmarkError, bench.baseline.BaselineError):
            self.respond(503, b'{"code":"LOCAL_BENCHMARK_UNAVAILABLE"}')
        except Exception:
            self.respond(400, b'{"code":"INVALID_AUDIO_OR_REQUEST"}')
        finally:
            if measuring:
                self.server.busy.release()
            # Request/decoded PCM/hypotheses have no persistent store; all references end here.


def serve(port, *, token=None, store=None, compare_small=False):
    with LocalServer(("127.0.0.1", port), Handler) as server:
        server.token = token or secrets.token_urlsafe(24)
        server.origin = f"http://127.0.0.1:{port}"
        server.busy = threading.Lock()
        server.recovery = store
        server.compare_small = compare_small
        expiration_stop = threading.Event()
        if store is not None:
            def expire():
                while not expiration_stop.wait(60):
                    try:
                        store.purge()
                    except Exception:
                        pass
            threading.Thread(target=expire, daemon=True).start()
        print(f"Own-voice recording: {server.origin}/{server.token}/", flush=True)
        print("Private 24-hour WAV recovery enabled." if store else "Audio/hypotheses stay in memory.", flush=True)
        try:
            server.serve_forever()
        finally:
            expiration_stop.set()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--recover-recordings", action="store_true", help="Opt in to private 24-hour WAV recovery")
    parser.add_argument("--compare-small", action="store_true", help="Compare base and installed small on identical WAVs")
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error("port must be 1024..65535")
    store = None
    try:
        if args.recover_recordings:
            config, digest = bench.corpus()
            store = RecoveryStore(bench.ROOT / "tools/benchmark-results/private/voice-recovery",
                                  [sample["id"] for sample in config["samples"]], digest)
        serve(args.port, store=store, compare_small=args.compare_small)
    except KeyboardInterrupt:
        return 0
    except (OSError, RecoveryError):
        print("Recorder failed: LOCAL_SERVER_UNAVAILABLE", file=sys.stderr)
        return 1
    finally:
        if store is not None:
            store.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
