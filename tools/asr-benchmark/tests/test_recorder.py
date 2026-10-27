"""Own-voice boundaries and transient first-pass comparison, without a microphone."""

import base64
import copy
from email.message import Message
import io
import json
from pathlib import Path
import sys
import threading
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import wave

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import recorder


def payload():
    config, _ = recorder.bench.corpus()
    wav = recorder.bench.wav_bytes(bytes(32000))
    return {"adult_own_voice": True, "references_confirmed": True,
            "clips": [{"id": s["id"], "wav_base64": base64.b64encode(wav).decode()}
                      for s in config["samples"]]}


class AdultCorpusTests(unittest.TestCase):
    def test_base_small_receive_identical_prepared_wav_objects_and_keep_results_separate(self):
        base, small = Mock(), Mock()
        base.config = recorder.bench.baseline.manifest()
        small.config = copy.deepcopy(base.config)
        small.config["model"].update(id="small-multilingual", sha256="a" * 64)
        calls = []
        def measure(clips, adapter, repeats, *, on_first_pass, warmed):
            self.assertTrue(warmed)
            self.assertEqual(base.transcribe.call_count + small.transcribe.call_count, 2)
            calls.append((clips, [clip["wav"] for clip in clips], adapter, repeats))
            for clip in clips:
                on_first_pass(clip, "seis" if adapter is base else "siete", False)
            return {"scores": {"speech_clips": 12}}
        with patch.object(recorder.bench, "Whisper", side_effect=[base, small]), \
                patch("models.small_runtime", return_value=Mock()), \
                patch.object(recorder.bench, "measure", side_effect=measure), \
                patch.object(recorder.bench, "environment", return_value={}):
            report = recorder.adult_report(payload(), compare_small=True)
        self.assertIs(calls[0][0], calls[1][0])
        for one, two in zip(calls[0][1], calls[1][1]): self.assertIs(one, two)
        self.assertEqual([call[3] for call in calls], [3, 3])
        self.assertEqual(report["candidates"][1]["model_id"], "small-multilingual")
        self.assertEqual(report["phrase_comparisons"][0]["transcript"], "seis")
        self.assertEqual(report["small_phrase_comparisons"][0]["transcript"], "siete")
        self.assertFalse(report["comparison"]["parallel_inference"])
        self.assertNotIn("transcript", json.dumps(report["candidates"]))

    def test_missing_small_aborts_before_either_model_receives_audio(self):
        with patch.object(recorder.bench, "Whisper", side_effect=[Mock(), RuntimeError("PRIVATE")]), \
                patch("models.small_runtime", return_value=Mock()), \
                patch.object(recorder.bench, "measure") as measure:
            with self.assertRaisesRegex(recorder.bench.BenchmarkError, "^SMALL_RUNTIME_UNAVAILABLE$"):
                recorder.adult_report(payload(), compare_small=True)
            measure.assert_not_called()

    def test_small_warmup_failure_aborts_before_base_scoring(self):
        base, small = Mock(), Mock()
        small.transcribe.side_effect = recorder.bench.BenchmarkError("PROVIDER_ERROR")
        with patch.object(recorder.bench, "Whisper", side_effect=[base, small]), \
                patch("models.small_runtime", return_value=Mock()), \
                patch.object(recorder.bench, "measure") as measure:
            with self.assertRaisesRegex(recorder.bench.BenchmarkError, "PROVIDER_ERROR"):
                recorder.adult_report(payload(), compare_small=True)
        self.assertEqual(base.transcribe.call_count, 1)  # Warm-up only; no scored audio.
        measure.assert_not_called()

    def test_silence_control_follows_corpus_configuration(self):
        config, digest = recorder.bench.corpus()
        with patch.object(recorder.bench, "corpus", return_value=({**config, "silence_seconds": 1.5}, digest)):
            clips, dataset = recorder.adult_clips(payload())
        with wave.open(io.BytesIO(clips[-1]["wav"]), "rb") as wav:
            self.assertEqual(wav.getnframes(), 24000)
        self.assertEqual(clips[-1]["audio_seconds"], 1.5)

    def test_same_phrase_references_canonical_pcm_and_separate_silence(self):
        clips, dataset = recorder.adult_clips(payload())
        self.assertEqual(len(clips), 13)
        self.assertEqual(dataset["kind"], "adult-own-voice")
        self.assertEqual(dataset["audio_seconds"], 14)
        self.assertNotIn("audio_sha256", dataset)
        self.assertNotIn("voice", dataset)
        self.assertEqual(clips[-1]["kind"], "silence")
        with wave.open(io.BytesIO(clips[0]["wav"]), "rb") as wav:
            self.assertEqual(wav.getparams()[:3], (1, 2, 16000))

    def test_consent_reference_review_cardinality_order_and_duplicates(self):
        original = payload()
        variants = [None, [], {**original, "adult_own_voice": False},
                    {**original, "references_confirmed": False},
                    {**original, "clips": original["clips"][:-1]},
                    {**original, "clips": list(reversed(original["clips"]))},
                    {**original, "clips": original["clips"][:1] * 12}]
        for value in variants:
            with self.subTest(value_type=type(value)), self.assertRaises(recorder.bench.BenchmarkError):
                recorder.adult_clips(value)

    def test_audio_size_format_duration_and_truncation(self):
        for raw in (b"invalid audio", recorder.bench.wav_bytes(b""),
                    recorder.bench.wav_bytes(bytes(960002)),
                    recorder.bench.wav_bytes(bytes(32000))[:-4]):
            value = payload()
            value["clips"][0]["wav_base64"] = base64.b64encode(raw).decode()
            with self.assertRaises((recorder.bench.BenchmarkError, wave.Error, EOFError)):
                recorder.adult_clips(value)
        value = payload()
        value["clips"][0]["wav_base64"] = "a" * 1_300_001
        with self.assertRaisesRegex(recorder.bench.BenchmarkError, "AUDIO_LIMIT"):
            recorder.adult_clips(value)

    def test_adult_uses_same_measurement_without_synthesis(self):
        adapter = Mock()
        with patch.object(recorder.bench, "Whisper", return_value=adapter), \
                patch.object(recorder.bench, "measure", return_value={"aggregate": 1}) as measure, \
                patch.object(recorder.bench, "make_report", return_value={"aggregate": 1}) as report:
            self.assertEqual(recorder.adult_report(payload()), {"aggregate": 1, "phrase_comparisons": []})
        self.assertEqual(len(measure.call_args.args[0]), 13)
        self.assertEqual(measure.call_args.args[2], 3)
        self.assertEqual(report.call_args.kwargs, {"synthesis": False})

    def test_comparison_is_first_pass_exact_text_scoring_and_separate_from_aggregates(self):
        adapter = Mock()
        exact = "  Tengo 12 y agrego seis a dieciocho.\n"
        adapter.transcribe.side_effect = ["private warmup", exact, "  ",
            recorder.bench.baseline.BaselineError("PRIVATE vendor error"),
            *(["seis"] * 8), "10 y 6 más 1 da 10 y 7", "[Música]", *(["later pass"] * 26)]
        adapter.resources.return_value = {"cpu_usec": 1000, "memory_bytes": 100, "memory_peak_bytes": 200}
        with patch.object(recorder.bench, "Whisper", return_value=adapter), \
                patch.object(recorder.bench, "make_report", return_value={"aggregate": 1}) as aggregate:
            result = recorder.adult_report(payload())
        rows = result["phrase_comparisons"]
        self.assertEqual(len(rows), 13)
        self.assertEqual(rows[0]["transcript"], exact)
        self.assertEqual(rows[0]["expected_numbers"], [12, 6, 18])
        self.assertEqual(rows[0]["extracted_numbers"], [12, 6, 18])
        self.assertTrue(rows[0]["numbers_match"])
        self.assertEqual(rows[1]["transcript"], "  ")
        self.assertEqual(rows[1]["extracted_numbers"], [])
        self.assertEqual(rows[1]["status"], "empty")
        self.assertFalse(rows[1]["numbers_match"])
        self.assertEqual(rows[2]["status"], "technical_failure")
        for field in ("transcript", "extracted_numbers", "numbers_match", "transcript_diagnostics"):
            self.assertIsNone(rows[2][field])
        self.assertEqual(rows[-2]["extracted_numbers"], [10, 6, 1, 10, 7])
        self.assertFalse(rows[-2]["numbers_match"])
        self.assertEqual(len(rows[-2]["transcript_diagnostics"]["numeric_ambiguities"]), 2)
        self.assertEqual(rows[-1]["kind"], "silence")
        self.assertIsNone(rows[-1]["numbers_match"])
        self.assertEqual(rows[-1]["transcript_diagnostics"]["content_kind"], "nonverbal_only")
        self.assertEqual(rows[-1]["transcript_diagnostics"]["nonverbal_labels"], ["[Música]"])
        self.assertNotIn("PRIVATE", json.dumps(rows))
        self.assertNotIn("private warmup", json.dumps(rows))
        self.assertNotIn("later pass", json.dumps(rows))
        # The CLI/public report builder still receives only aggregate metrics.
        summary = json.dumps(aggregate.call_args.args[2])
        for forbidden in ("transcript", "reference_text", "phrase_comparisons", "Tengo"):
            self.assertNotIn(forbidden, summary)


class HttpTests(unittest.TestCase):
    def test_delete_can_complete_while_measurement_is_busy(self):
        handler = self.handler("/secret/recovery-clear", **{"Content-Length": "2"})
        handler.server.recovery = Mock()
        handler.server.recovery.clear.return_value = {"enabled": True, "clip_count": 0}
        handler.server.busy.acquire()
        handler.do_POST()
        self.assertEqual(handler.respond.call_args.args[0], 200)
        self.assertTrue(handler.server.busy.locked())
        handler.server.busy.release()

    def handler(self, path="/secret/measure", **headers):
        handler = object.__new__(recorder.Handler)
        handler.path = path
        handler.headers = Message()
        defaults = {"Host": "127.0.0.1:8765", "Origin": "http://127.0.0.1:8765",
                    "Content-Type": "application/json"}
        for key, value in {**defaults, **headers}.items(): handler.headers[key] = value
        handler.server = SimpleNamespace(origin="http://127.0.0.1:8765", token="secret", busy=threading.Lock())
        handler.connection = Mock()
        handler.rfile = io.BytesIO(b"{}")
        handler.respond = Mock()
        return handler

    def test_origin_host_token_content_type_and_transfer_encoding_admission(self):
        for overrides in ({"Origin": "https://remote.example"}, {"Host": "localhost:8765"},
                          {"Content-Type": "text/plain"}, {"Transfer-Encoding": "chunked"}):
            handler = self.handler(**overrides)
            with patch.object(recorder, "adult_report") as measure:
                handler.do_POST()
                self.assertEqual(handler.respond.call_args.args[0], 403)
                measure.assert_not_called()
        handler = self.handler("/wrong/measure")
        handler.do_POST()
        self.assertEqual(handler.respond.call_args.args[0], 403)

    def test_size_limit_busy_and_redacted_failure(self):
        for length in ("0", "-1", "invalid", str(recorder.MAX_BODY + 1)):
            handler = self.handler(**{"Content-Length": length})
            handler.do_POST()
            self.assertEqual(handler.respond.call_args.args[0], 413)
        handler = self.handler(**{"Content-Length": "2"})
        handler.server.busy.acquire()
        handler.do_POST()
        self.assertEqual(handler.respond.call_args.args[0], 409)
        handler.server.busy.release()
        for error in (ValueError("PRIVATE speech"), recorder.bench.BenchmarkError("PRIVATE audio")):
            handler = self.handler(**{"Content-Length": "2"})
            with patch.object(recorder, "adult_report", side_effect=error): handler.do_POST()
            self.assertNotIn(b"PRIVATE", handler.respond.call_args.args[1])
            self.assertFalse(handler.server.busy.locked())

    def test_recovery_measure_decodes_once_and_reuses_clips(self):
        body = json.dumps({**payload(), "recovery_session_id": "a" * 32}).encode()
        handler = self.handler(**{"Content-Length": str(len(body))})
        handler.rfile = io.BytesIO(body)
        handler.server.recovery = Mock()
        handler.server.recovery.info.return_value = {"enabled": True}
        with patch.object(recorder, "adult_clips", wraps=recorder.adult_clips) as decode, \
                patch.object(recorder.bench, "Whisper", return_value=Mock()), \
                patch.object(recorder.bench, "measure", return_value={"aggregate": 1}), \
                patch.object(recorder.bench, "make_report", return_value={"candidates": []}):
            handler.do_POST()
        self.assertEqual(handler.respond.call_args.args[0], 200)
        self.assertEqual(decode.call_count, 1)
        saved = handler.server.recovery.save.call_args.args[0]
        self.assertEqual(len(saved), 13)

    def test_success_does_not_echo_request(self):
        handler = self.handler(**{"Content-Length": "2"})
        with patch.object(recorder, "adult_report", return_value={"aggregate": 1}): handler.do_POST()
        self.assertEqual(handler.respond.call_args.args, (200, b'{"aggregate": 1}'))
        self.assertFalse(handler.server.busy.locked())

    def test_assets_are_only_fixed_allowlist_and_security_headers(self):
        handler = self.handler("/secret/../corpus.json")
        handler.do_GET()
        self.assertEqual(handler.respond.call_args.args[0], 404)
        handler = self.handler("/secret/corpus")
        handler.do_GET()
        self.assertEqual(len(json.loads(handler.respond.call_args.args[1])), 12)
        handler = self.handler()
        del handler.respond
        handler.send_response = Mock(); handler.send_header = Mock(); handler.end_headers = Mock()
        handler.wfile = io.BytesIO()
        handler.respond(200, b"{}")
        headers = dict(call.args for call in handler.send_header.call_args_list)
        self.assertEqual(headers["Cache-Control"], "no-store")
        self.assertNotIn("Access-Control-Allow-Origin", headers)
        self.assertIn("connect-src 'self'", headers["Content-Security-Policy"])


if __name__ == "__main__":
    unittest.main()
