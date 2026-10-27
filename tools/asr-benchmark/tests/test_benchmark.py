"""Offline scorer, boundary and privacy regressions; no model/voice/Docker needed."""

from contextlib import redirect_stderr, redirect_stdout
import copy
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
import wave


HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location("faro_benchmark", HERE / "benchmark.py")
bench = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bench)
from metrics import Scores, edits, numbers, percentile, transcript_diagnostics, words


class DiagnosticTests(unittest.TestCase):
    def test_known_nonverbal_tags_case_accents_and_multiple_tags(self):
        raw = " [MÚSICA] [ Music ] [Aplausos]. "
        diagnostic = transcript_diagnostics(raw)
        self.assertEqual(diagnostic["content_kind"], "nonverbal_only")
        self.assertEqual(diagnostic["nonverbal_labels"], ["[MÚSICA]", "[ Music ]", "[Aplausos]"])
        self.assertEqual(diagnostic["numeric_ambiguities"], [])
        self.assertEqual(transcript_diagnostics("  \n")["content_kind"], "empty")

    def test_mixed_text_and_unknown_tags_are_not_silently_treated_as_nonverbal(self):
        self.assertEqual(transcript_diagnostics("[Música] tengo seis")["content_kind"], "mixed")
        for raw in ("música", "[inaudible]", "[seis]", "[Música y seis]", "[[Música]]", "..."):
            with self.subTest(raw=raw):
                diagnostic = transcript_diagnostics(raw)
                self.assertEqual(diagnostic["content_kind"], "speech_or_unclassified")
                self.assertEqual(diagnostic["nonverbal_labels"], [])
        self.assertEqual(numbers("[seis]"), [6])

    def test_ambiguities_preserve_literal_components_and_never_sum_them(self):
        raw = "10 y 6 más 1 da 10 y 7"
        self.assertEqual(transcript_diagnostics(raw)["numeric_ambiguities"], [
            {"text": "10 y 6", "components": [10, 6]},
            {"text": "10 y 7", "components": [10, 7]}])
        self.assertEqual(numbers(raw), [10, 6, 1, 10, 7])
        for raw in ("diez y seis", "20 y tres", "treinta y 6", "30 y seis"):
            self.assertEqual(len(transcript_diagnostics(raw)["numeric_ambiguities"]), 1)

    def test_no_warnings_for_canonical_compounds_or_cross_punctuation_and_decimals(self):
        for raw in ("dieciséis", "treinta y seis", "treinta y un", "6 y 7", "10, y 6",
                    "10. y 6", "10 más 6", "10 y 6.5", "1.10 y 6", "120 y 6", "10y6"):
            with self.subTest(raw=raw):
                self.assertEqual(transcript_diagnostics(raw)["numeric_ambiguities"], [])
        raw = "[Música] 10\n y 6"
        self.assertEqual(transcript_diagnostics(raw)["numeric_ambiguities"][0]["text"], "10\n y 6")

    def test_silence_breakdown_keeps_nonempty_metric_without_calling_tags_speech(self):
        scores = Scores()
        for text in ("[Música]", "[Music]", "[Risas] seis", "[inaudible]", "seis", "  "):
            scores.add({"kind": "silence"}, text)
        scores.add({"kind": "silence"}, "PRIVATE error", failed=True)
        result = scores.summary()
        self.assertEqual(result["silence_clips"], 7)
        self.assertEqual(result["silence_nonempty"], 5)
        self.assertEqual(result["silence_nonverbal_only"], 2)
        self.assertEqual(result["silence_speech_or_unclassified"], 3)
        self.assertEqual(result["silence_empty"], 1)
        self.assertEqual(result["silence_technical_failures"], 1)
        self.assertNotIn("PRIVATE", json.dumps(result))

    def test_ambiguous_annotations_do_not_change_strict_accuracy_or_wer(self):
        scores = Scores()
        scores.add({"kind": "speech", "text": "dieciséis", "critical_numbers": [16]}, "10 y 6")
        result = scores.summary()
        self.assertEqual(result["critical_number_accuracy"], 0)
        self.assertEqual(result["speech_numeric_ambiguities"], 1)
        self.assertEqual(result["word_edits"], 3)
        self.assertEqual(result["reference_words"], 1)
        self.assertNotIn("10 y 6", json.dumps(result))


class ScoringTests(unittest.TestCase):
    def test_spanish_accents_digits_compounds_and_repetition(self):
        self.assertEqual(numbers("Cero, DIECISÉIS, veintidós, treinta y uno, 53, seis, seis."),
                         [0, 16, 22, 31, 53, 6, 6])
        self.assertEqual(numbers("Una decena, un bloque y veintiún objetos."), [1, 1, 21])
        self.assertEqual(numbers("noventa y nueve, veinte y diez"), [99, 20, 10])
        self.assertEqual(numbers("sin números"), [])

    def test_every_bounded_integer_has_consistent_digit_and_spoken_forms(self):
        from metrics import UNITS, TENS
        spoken = {value: key for key, value in UNITS.items()}
        for value in range(100):
            if value < 30:
                phrase = spoken[value]
            else:
                tens = value // 10 * 10
                phrase = next(k for k, v in TENS.items() if v == tens)
                if value % 10:
                    phrase += " y " + spoken[value % 10]
            self.assertEqual(numbers(phrase), numbers(str(value)))

    def test_six_seven_order_omissions_insertions_and_repetitions_fail(self):
        clip = {"kind": "speech", "text": "Seis, seis y siete.", "critical_numbers": [6, 6, 7]}
        for hypothesis in ("siete seis siete", "seis siete", "seis seis siete ocho", "", "seis siete seis"):
            scores = Scores()
            scores.add(clip, hypothesis)
            self.assertEqual(scores.summary()["critical_number_accuracy"], 0)
        scores = Scores()
        scores.add(clip, "6, 6 y 7")
        self.assertEqual(scores.summary()["critical_number_accuracy"], 1)
        self.assertGreater(scores.summary()["wer"], 0)  # WER does not normalize numeric spelling.

    def test_wer_edits_micro_denominator_and_failures_are_explicit(self):
        scores = Scores()
        clip = {"kind": "speech", "text": "Agrego seis", "critical_numbers": [6]}
        scores.add(clip, "agrego siete")  # substitution
        scores.add(clip, "")  # valid empty hypothesis: two deletions
        scores.add(clip, "private vendor failure", failed=True)
        result = scores.summary()
        self.assertEqual((result["speech_clips"], result["speech_technical_failures"]), (3, 1))
        self.assertEqual((result["word_edits"], result["reference_words"], result["wer_clips"]), (3, 4, 2))
        self.assertEqual(result["wer"], 0.75)
        self.assertEqual(edits(["a"], ["a", "b", "c"]), 2)
        self.assertEqual(words("¡DIECISÉIS, más uno!"), ["dieciséis", "más", "uno"])

    def test_silence_does_not_inflate_critical_number_accuracy(self):
        scores = Scores()
        clip = {"kind": "silence"}
        scores.add(clip, "")
        scores.add(clip, "texto inventado")
        scores.add(clip, "", failed=True)
        result = scores.summary()
        self.assertIsNone(result["critical_number_accuracy"])
        self.assertIsNone(result["wer"])
        self.assertEqual((result["silence_clips"], result["silence_nonempty"],
                          result["silence_technical_failures"]), (3, 1, 1))

    def test_nearest_rank_percentiles(self):
        self.assertIsNone(percentile([], 95))
        self.assertEqual(percentile([40, 10, 20, 30], 50), 20)
        self.assertEqual(percentile(range(1, 101), 95), 95)


class RunnerTests(unittest.TestCase):
    def clip(self):
        return {"kind": "speech", "text": "Agrego seis", "critical_numbers": [6],
                "wav": b"synthetic audio"}

    def adapter(self):
        adapter = Mock()
        adapter.transcribe.return_value = "Agrego seis"
        adapter.resources.side_effect = [
            {"cpu_usec": 1000, "memory_bytes": 100, "memory_peak_bytes": 200},
            {"cpu_usec": 2000, "memory_bytes": 150, "memory_peak_bytes": 250},
            {"cpu_usec": 3000, "memory_bytes": 140, "memory_peak_bytes": 250}]
        return adapter

    def test_warmup_exclusion_same_bytes_repeat_disagreement_and_aggregate_privacy(self):
        adapter = self.adapter()
        adapter.transcribe.side_effect = ["warmup secret", "Agrego seis", "Agrego siete"]
        with patch.object(bench.time, "monotonic", side_effect=[0, .01, 1, 1.03]):
            result = bench.measure([self.clip()], adapter, 2)
        self.assertEqual(result["scores"]["speech_clips"], 1)
        self.assertEqual(result["scores"]["all_critical_numbers_correct"], 1)
        self.assertEqual(result["requests"], 2)
        self.assertEqual(result["clips_with_repeat_disagreement"], 1)
        self.assertEqual(result["latency_ms"]["p95"], 30)
        self.assertEqual(result["resources"]["cpu_seconds"], .002)
        self.assertEqual(result["resources"]["container_lifetime_peak_bytes"], 250)
        self.assertEqual([c.args[0] for c in adapter.transcribe.call_args_list], [b"synthetic audio"] * 3)
        serialized = json.dumps(result)
        for forbidden in ("Agrego", "warmup secret", "synthetic audio", "transcript"):
            self.assertNotIn(forbidden, serialized)

    def test_failed_request_is_counted_without_retry_fallback_or_exception_leak(self):
        adapter = self.adapter()
        adapter.transcribe.side_effect = ["warmup", bench.baseline.BaselineError("PRIVATE vendor body")]
        result = bench.measure([self.clip()], adapter, 1)
        self.assertEqual(adapter.transcribe.call_count, 2)
        self.assertEqual(result["technical_failures_all_passes"], 1)
        self.assertEqual(result["scores"]["critical_number_accuracy"], 0)
        self.assertEqual(result["scores"]["wer_clips"], 0)
        self.assertNotIn("PRIVATE", json.dumps(result))

    def test_warmup_failure_aborts_without_publishing_measurements(self):
        adapter = self.adapter()
        adapter.transcribe.side_effect = bench.BenchmarkError("PROVIDER_ERROR")
        with self.assertRaises(bench.BenchmarkError):
            bench.measure([self.clip()], adapter, 1)
        adapter.resources.assert_not_called()

    def test_observer_sees_only_scored_first_pass_and_no_vendor_error(self):
        adapter = self.adapter()
        adapter.transcribe.side_effect = ["warmup secret", bench.baseline.BaselineError("PRIVATE vendor body"),
                                         "later pass"]
        observed = Mock()
        clip = self.clip()
        result = bench.measure([clip], adapter, 2, on_first_pass=observed)
        observed.assert_called_once_with(clip, "", True)
        self.assertNotIn("PRIVATE", json.dumps(result))
        self.assertNotIn("later pass", json.dumps(result))

    def test_counter_reset_aborts_instead_of_negative_cpu(self):
        adapter = self.adapter()
        adapter.resources.side_effect = [
            {"cpu_usec": 2000, "memory_bytes": 100, "memory_peak_bytes": 200},
            {"cpu_usec": 1000, "memory_bytes": 150, "memory_peak_bytes": 250}]
        with self.assertRaisesRegex(bench.BenchmarkError, "RESOURCE_COUNTER_RESET"):
            bench.measure([self.clip()], adapter, 1)

    def test_committed_corpus_annotations_rates_and_size(self):
        config, digest = bench.corpus()
        self.assertEqual(len(config["samples"]) * len(config["rates"]), 36)
        self.assertEqual(len(digest), 64)
        bad = copy.deepcopy(config)
        bad["samples"][0]["critical_numbers"] = [99]
        with patch.object(Path, "read_bytes", return_value=json.dumps(bad).encode()):
            with self.assertRaisesRegex(bench.BenchmarkError, "CORPUS_INVALID"):
                bench.corpus()

    def test_normalization_fingerprint_and_silence_bytes(self):
        config, _ = bench.corpus()
        config["samples"] = config["samples"][:1]
        config["rates"] = [140]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.wav"
            path.write_bytes(bench.wav_bytes(bytes(32000)))
            with patch.object(bench.baseline, "synthesize", return_value=path):
                clips, digest = bench.prepare(config, Path(directory))
                _, repeated_digest = bench.prepare(config, Path(directory))
            self.assertEqual(digest, repeated_digest)
            self.assertEqual(len(clips), 2)
            with wave.open(io.BytesIO(clips[1]["wav"]), "rb") as wav:
                self.assertEqual(wav.getparams()[:3], (1, 2, 16000))
                self.assertEqual(wav.readframes(wav.getnframes()), bytes(64000))

    def test_invalid_audio_is_rejected(self):
        config, _ = bench.corpus()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "invalid.wav"
            for channels, rate, frames in ((2, 16000, 16000), (1, 8000, 8000),
                                           (1, 16000, 0), (1, 16000, 480001)):
                with self.subTest(channels=channels, rate=rate, frames=frames):
                    with wave.open(str(path), "wb") as audio:
                        audio.setparams((channels, 2, rate, 0, "NONE", "not compressed"))
                        audio.writeframes(bytes(frames * channels * 2))
                    with patch.object(bench.baseline, "synthesize", return_value=path):
                        with self.assertRaisesRegex(bench.BenchmarkError, "INVALID_SYNTHETIC_WAV"):
                            bench.prepare(config, Path(directory))

    def test_reviewed_measurement_matches_corpus_and_contains_no_raw_observations(self):
        config, digest = bench.corpus()
        report = json.loads((HERE / "results-synthetic-v1.json").read_text())
        self.assertEqual(report["dataset"]["manifest_sha256"], digest)
        self.assertEqual(report["dataset"]["unique_clips"], len(config["samples"]) * len(config["rates"]) + 1)
        def check(value):
            if isinstance(value, dict):
                self.assertTrue(set(value).isdisjoint({"text", "transcript", "wav", "samples", "clip_id"}))
                for child in value.values(): check(child)
            elif isinstance(value, list):
                for child in value: check(child)
        check(report)

    def test_cli_returns_failure_for_report_with_technical_failures(self):
        report = {"candidates": [{"technical_failures_all_passes": 1}]}
        with patch.object(sys, "argv", ["benchmark.py"]), \
                patch.object(bench, "run", return_value=report), redirect_stdout(io.StringIO()):
            self.assertEqual(bench.main(), 1)

    def test_temporary_audio_is_removed_after_failure_and_interrupt(self):
        for failure in (bench.BenchmarkError("PROVIDER_ERROR"), KeyboardInterrupt()):
            paths = []
            def prepare(config, directory):
                paths.append(directory)
                (directory / "private.wav").write_bytes(b"private audio")
                return [self.clip()], "digest"
            with patch.object(bench, "Whisper"), patch.object(bench, "prepare", side_effect=prepare), \
                    patch.object(bench, "measure", side_effect=failure):
                with self.assertRaises(type(failure)):
                    bench.run(1)
            self.assertFalse(paths[0].exists())

    def test_cli_redacts_unexpected_exception_and_returns_failure(self):
        output, error = io.StringIO(), io.StringIO()
        with patch.object(sys, "argv", ["benchmark.py"]), \
                patch.object(bench, "run", side_effect=ValueError("PRIVATE text")), \
                redirect_stdout(output), redirect_stderr(error):
            self.assertEqual(bench.main(), 1)
        self.assertEqual(output.getvalue(), "")
        self.assertEqual(error.getvalue(), "Benchmark failed: LOCAL_OPERATION_FAILED\n")


class AdapterTests(unittest.TestCase):
    def test_reject_remote_docker_without_model_access(self):
        with patch.dict(bench.os.environ, {"DOCKER_HOST": "tcp://remote:2375"}), \
                patch.object(bench.baseline, "run_tool", return_value="unix:///var/run/docker.sock"), \
                patch.object(bench.baseline, "verify_model") as verify:
            with self.assertRaisesRegex(bench.BenchmarkError, "LOCAL_DOCKER_REQUIRED"):
                bench.Whisper()
            verify.assert_not_called()

    def test_managed_configuration_is_checked_before_any_audio(self):
        config = bench.baseline.manifest()
        expected = bench.baseline.docker_start_args(config, "unused")
        info = {"Config": {"Image": config["runtime"]["image"],
                           "Cmd": expected[expected.index(config["runtime"]["image"]) + 1:],
                           "Entrypoint": ["/app/build/bin/whisper-server"]},
                "HostConfig": {"LogConfig": {"Type": "none"},
                               "PortBindings": {"8080/tcp": [{"HostIp": "127.0.0.1", "HostPort": "8085"}]}},
                "Mounts": [{"Destination": "/models/ggml-base.bin", "RW": False,
                            "Source": str(bench.baseline.MODEL_PATH)}]}
        for field in ("valid", "image", "logging", "port", "mount", "command", "entrypoint"):
            candidate = copy.deepcopy(info)
            if field == "image": candidate["Config"]["Image"] = "mutable:latest"
            if field == "logging": candidate["HostConfig"]["LogConfig"]["Type"] = "json-file"
            if field == "port": candidate["HostConfig"]["PortBindings"]["8080/tcp"][0]["HostIp"] = "0.0.0.0"
            if field == "mount": candidate["Mounts"][0]["RW"] = True
            if field == "command": candidate["Config"]["Cmd"] += ["--convert"]
            if field == "entrypoint": candidate["Config"]["Entrypoint"] = ["sh"]
            with self.subTest(field=field), patch.dict(bench.os.environ, {"DOCKER_HOST": ""}), \
                    patch.object(bench.baseline, "run_tool", side_effect=["unix:///socket", json.dumps([candidate])]), \
                    patch.object(bench.baseline, "verify_model"), \
                    patch.object(bench.baseline, "owned_container", return_value="a" * 64), \
                    patch.object(bench.baseline, "local_json", return_value={"status": "ok"}) as http:
                if field == "valid":
                    bench.Whisper()
                else:
                    with self.assertRaisesRegex(bench.BenchmarkError, "BASELINE_CONFIGURATION_MISMATCH"):
                        bench.Whisper()
                    http.assert_not_called()

    def test_empty_is_scored_but_invalid_or_oversized_response_is_failure(self):
        adapter = object.__new__(bench.Whisper)
        for response in ({"text": ""}, {"text": "   "}, {"text": "seis"},
                         {"text": 1}, {"text": "x" * 16001}, {"error": "private body", "text": "six"}):
            with patch.object(bench.baseline, "local_json", return_value=response) as http:
                if "error" not in response and isinstance(response["text"], str) and len(response["text"]) <= 16000:
                    self.assertEqual(adapter.transcribe(b"audio"), response["text"])
                else:
                    with self.assertRaisesRegex(bench.BenchmarkError, "^PROVIDER_ERROR$"):
                        adapter.transcribe(b"audio")
                self.assertEqual(http.call_args.args, ("/inference",))
                self.assertEqual(http.call_args.kwargs["timeout"], 120)

    def test_resource_parse_and_missing_cgroup_failure(self):
        adapter = object.__new__(bench.Whisper)
        adapter.identity = "a" * 64
        with patch.object(bench.baseline, "run_tool", return_value="usage_usec 123\nuser_usec 100\n456\n789\n"):
            self.assertEqual(adapter.resources(), {"cpu_usec": 123, "memory_bytes": 456, "memory_peak_bytes": 789})
        with patch.object(bench.baseline, "run_tool", return_value="private diagnostic"):
            with self.assertRaisesRegex(bench.BenchmarkError, "RESOURCE_COUNTERS_INVALID"):
                adapter.resources()


if __name__ == "__main__":
    unittest.main()
