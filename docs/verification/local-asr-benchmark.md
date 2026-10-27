# FARO-014 — Local ASR benchmark verification

[Issue #39](https://github.com/stevdrey/faro/issues/39) now has a standalone
[runner and methodology](../../tools/asr-benchmark/README.md), a versioned CC0
synthetic corpus, offline regression tests and a reviewed
[aggregate measurement](../../tools/asr-benchmark/results-synthetic-v1.json).
The synthetic run measures pinned Whisper base. A separate, authorized adult
run compares base and small on identical recordings; only its reviewed aggregate
results are published below. Optional Moonshine and Vosk adapters are not
installed and are explicitly unmeasured. No engine replacement or child-speech
validation is claimed. **Dependency #34 remains open.**

## Synthetic base run

Run on **2026-10-24, America/Costa_Rica** (report timestamp 06:31 UTC), against
`origin/main` at `2ec66f9` plus this change. Linux amd64, AMD Ryzen 7 7730U,
16 logical CPUs; Whisper configured for four CPU threads, no GPU. Python 3.14.8,
Docker 29.9.2, eSpeak NG 1.52.0 and FFmpeg 8.1.3. Other unrelated containers were
present; host power/load was not controlled. Timing is a local observation, not
a hardware-normalized performance guarantee.

The runtime image, source revision, multilingual base model revision, file size
(147,951,465 bytes) and checksum match the existing
[FARO-010 manifest](../../infra/whisper/artifacts.json). The benchmark reverified
the full cached model before inference; no package, model or image was downloaded.
Container checks confirmed the pinned command/entrypoint, read-only model mount,
loopback binding and `none` logging driver.

The corpus has 36 unique speech clips (12 phrases at three rates) and one
two-second silence control: **131.011 seconds** total. One warm-up was excluded,
then three sequential passes produced **111 measured requests**. All accuracy
and WER figures below use only the first pass; repetition does not increase the
independent sample size. The JSON records hashes of the exact manifest and
canonical generated audio bytes for reproduction without retaining audio.

| Measurement | Observed result | Denominator / scope |
|---|---:|---|
| All critical numbers correct | **4/36 (11.11%)** | Exact ordered number list, unique first-pass speech clips |
| WER | **248/219 (113.24%)** | Word edits / reference words in 36 successful first-pass responses; insertions can exceed 100% |
| Speech technical failures | **0/36** | First-pass speech requests |
| Nonblank response to silence | **1/1** | Separate first-pass negative control, excluded from speech accuracy/WER |
| All-pass technical failures | **0/111** | Speech plus silence, all passes |
| Repeat disagreement | **0/37** | Clips with later exact text/status different from first pass |
| HTTP latency p50 | **1,004.386 ms** | Nearest rank over all 111 measured requests |
| HTTP latency p95 | **1,406.895 ms** | Same requests, including silence |
| Container CPU | **435.166681 CPU seconds** | cgroup usage delta across measured passes; not elapsed time |
| Container memory before / after | **261,902,336 / 332,300,288 bytes** | cgroup `memory.current`; not process RSS or host RAM |
| Container lifetime memory peak | **332,304,384 bytes (316.91 MiB)** | Includes startup/warm-up; fresh baseline container for this run |

These results show poor critical-number agreement and a nonempty silence
hypothesis on this **single synthetic formant voice**. The baseline's earlier
smoke test already documented synthetic-number errors. Reference text is the
intended synthesis input, not an independently audited transcription, so the
metrics conflate synthesis quality with recognition behavior. Do not infer how
Whisper performs on Costa Rican children, extrapolate an error rate to people,
or compare these WER numbers with unrelated vendor datasets.

No actual child participated. A study of one child would also not generalize.
Mathematical-meaning changes, operator/connective errors, false-child-error
attribution and assessment safety are **not measured**; no assessment decision
is made. Better number agreement alone would not establish a safe replacement.

## Optional adult own-voice guide

At the owner's request, `recorder.py` adds a local browser guide for testing
their own adult voice against the same 12 source phrases. Capture, review and
re-record happen in browser memory; only explicit **Medir con mi voz** sends
approved PCM WAVs to the guide and managed local runtime. The server keeps no
audio/transcript files in default mode and no access logs, binds only loopback, and validates random
URL token, Host/Origin, body/clip bounds, own-voice confirmation and reference
review. Audio IDs must match the fixed corpus in order; arbitrary references
and file paths are not accepted. This is separate from the application's
synthetic-only endpoint and changes no privacy gate.

Adult results stay in that page by default, separate from the committed synthetic
report. Publishing the aggregate comparison below was explicitly authorized;
personal transcripts and per-phrase observations are excluded. Browser buffers clear on explicit deletion, consent
withdrawal, hiding/leaving; pending capture callbacks cannot restore them. An
already accepted server measurement may finish locally after browser abort and
then releases its transient buffers. The server can also be stopped with Ctrl+C.

The guide now returns transient first-pass comparisons separately from aggregate
metrics: confirmed reference, exact hypothesis, expected/extracted numbers and
number-list agreement. Speech cards also play their original in-memory browser
WAV; the silence control stays separate. Technical failure (`null` values) is
distinct from empty recognition. Tests verify the observer excludes warm-up and
later repeats, preserves whitespace, uses the same number scorer and keeps vendor
errors out of the response. The synthetic CLI/public report builder and committed
measurement remain aggregate-only. Comparisons are plain-text DOM with no export,
file, storage or logs; existing deletion/visibility/generation boundaries remove
text and players and revoke their URLs.

Version-1 diagnostic annotations now distinguish recognized bracket-only nonverbal
labels, mixed label/text output, empty output and speech/unclassified content.
Unknown markers remain unclassified. Numeric warnings retain exact tens/`y`/unit
fragments and separate components without calculating sums or changing the
original hypothesis, extracted numbers, strict accuracy or WER. Canonical Spanish
compounds, punctuation boundaries and decimal fragments have explicit regressions.
Technical failures have no diagnostics. The adult-only response carries text
annotations in memory; the aggregate contains only additive numeric counters for
silence categories and first-pass speech clips with warnings. Previously committed
results remain unchanged because their raw responses were discarded.

## Matched base/small and user-authorized WAV recovery

The owner subsequently requested both a base/small comparison and temporary
recoverable WAV files. The guide now exposes these as explicit opt-ins,
`--compare-small --recover-recordings`; default operation remains memory-only.
Public small weights were downloaded from the fixed converted-model revision,
then verified as **487,601,967 bytes**, SHA-256
`1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b`.
The independent [small artifact manifest](../../tools/asr-benchmark/small-artifact.json)
records the LFS metadata source and license provenance. A failed initial direct
download was not installed; the same pinned revision's explicit `?download=true`
link succeeded and the complete checksum was verified before use.

Small runs in a distinct ownership-scoped local container on 8086; base remains
on 8085. Both use the existing pinned CPU image and identical decoding settings.
The server validates/canonicalizes audio once and passes the same immutable WAV
objects to each admitted model, serially in base/small order, three passes each.
Per-model aggregate metrics and selectable first-pass comparisons stay distinct.
Missing small admission fails before either model receives audio. No application
provider, browser ASR preview gate, dependency or automatic cloud fallback changes.

Recovery writes only explicitly reviewed adult WAVs and minimal integrity/expiry
metadata into the already ignored `tools/benchmark-results/private/voice-recovery/`.
The directory is 0700 and files 0600. Checkpoints use fsync/atomic replacement,
a single-process lock, fixed corpus IDs, checksums and generation validation.
Expiry is fixed at 24 hours; cleanup runs periodically while the guide is active
and on next start/access if stopped. Hiding/leaving clears browser buffers while
keeping the requested checkpoint; explicit deletion or authorization withdrawal
removes checkpoint WAVs and rotates its generation. No transcript/result file is
written. The runbook explains pending-checkpoint loss, corruption, deletion limits
and the lack of an OS cleanup guarantee while the machine is off.

## Authorized adult base/small comparison

Measured on **2026-10-26, America/Costa_Rica**, on the same Linux amd64 host
(AMD Ryzen 7 7730U, 16 logical CPUs, Python 3.14.8, Docker 29.9.2). One consenting
adult recorded and reviewed the 12 fixed source phrases. These references are
confirmed intended readings, not independently audited human transcriptions.
The server supplied **identical canonical PCM16 mono 16 kHz WAV bytes** to both
models, plus the same generated two-second silence control. Three passes gave
**39 measured requests per model**, after one excluded warm-up per model.
Number accuracy and WER use only the 12 unique first-pass speech responses;
repeated requests do not increase the independent sample size.

| Measurement | Whisper base | Whisper small |
|---|---:|---:|
| All critical numbers correct | **8/12 (66.7%)** | **12/12 (100%)** |
| WER, successful first-pass speech responses | **65.8%** | **60.3%** |
| HTTP latency p50, all 39 measured requests | **0.73 s** | **2.71 s** |
| HTTP latency p95, all 39 measured requests | **0.98 s** | **3.22 s** |
| Container lifetime memory peak | **320.9 MiB** | **741.0 MiB** |
| Technical failures, all passes | **0/39** | **0/39** |
| Silence classified as nonverbal-only | **1/1** | **1/1** |

Values retain the browser summary's displayed precision; no additional timing,
memory or WER numerator precision is inferred. Number accuracy requires the
entire ordered list to match. WER uses the same case/punctuation normalization
as the synthetic runner; digit/spelled-number equivalence is not applied to WER.
Silence is excluded from speech scores. A nonverbal-only response is nonempty,
but is not classified as ordinary speech. Ambiguous numeric variants are flagged
without summing their components or changing strict scores.

Both models used the pinned runtime in the
[base manifest](../../infra/whisper/artifacts.json) and the independently verified
[small manifest](../../tools/asr-benchmark/small-artifact.json): four CPU threads,
no GPU, Spanish (`es`), temperature 0, translation disabled and no prior context.
Inference ran serially, base before small, with both containers resident. Host
power/load was uncontrolled. Latencies exclude model loading; memory figures
are cgroup container lifetime peaks including prior use/startup/warm-up, not
isolated-run process RSS or total host RAM. CPU deltas are available in the
transient report, but were not retained for this published adult summary.

Small improved critical-number agreement on these recordings and used more time
and memory. Qualitative local review also found better preservation of some
operator/relation wording, **with remaining action/negation meaning losses even
when all numbers matched**. No validated semantic-accuracy percentage, operator
error rate, false-child-error rate or assessment-safety result was measured.
Neither model is promoted to the application's provider on this evidence.

This is one adult, 12 phrases and one recording per phrase, not child-speech or
population evidence. The methodology, scoring, artifact pins and synthetic
corpus are reproducible; the exact private adult run cannot be independently
replayed from this PR because its recordings are intentionally not distributed.
The aggregate table contains no personal transcript, per-phrase excerpt, voice
fingerprint, session identifier or WAV. It does not modify or pool results with
the earlier synthetic experiment. Moonshine/Vosk remain **not measured**.

## Verification

**49/49 benchmark/guide/model/recovery offline tests** and **40/40 baseline
tests** passed with resource warnings promoted to errors. A separate
guide on another loopback port used a private temporary directory containing only
synthetic recordings. Chromium checkpointed 12 clips, reloaded, restored identical
WAV hashes without another microphone request, completed **39 local requests per
model**, selected both model views, exercised annotations/inert text and cleared
both browser results and the synthetic checkpoint, including delayed-response
cases. The real user's checkpoint directory was not used by this disposable test.
No raw personal recordings or ASR responses are included in this document or Git.
The browser check also verified 13 first-pass comparison cards, 12 audio players,
nonverbal/mixed annotations, ambiguous numeric components without summing, empty
versus failed recognition, inert HTML-like text and revoked playback URLs.
Final syntax and whitespace checks passed; PR-size preflight passed at
**24/40 changed files**. Remote CI is tracked separately on the PR.

## Commands and automated checks

From the repository root:

```bash
python3 -W error::ResourceWarning -m unittest discover -s tools/asr-benchmark/tests -v
python3 -W error::ResourceWarning -m unittest discover -s infra/whisper/tests -v
node --check tools/asr-benchmark/record.js
node --check tools/asr-benchmark/tests/recorder-browser.cjs
python3 infra/whisper/baseline.py start
python3 tools/asr-benchmark/benchmark.py --repeats 3
python3 infra/whisper/baseline.py stop
git diff --check
node .agents/scripts/pr-size-preflight.mjs
```

The measured command exited 0. The managed baseline was stopped afterward;
unrelated containers were preserved. Temporary synthesis files were removed.
The committed output contains aggregate metrics, artifact hashes and technical
environment only; no audio, hypothesis, per-clip result, personal identifier or raw error
is published. No dependencies, lockfiles, Java/TypeScript runtime or application
behavior changed.

Offline coverage includes Spanish compounds/accents/digits, all supported
integers, six-versus-seven, order/repetition/extra/missing numbers, WER denominators
and insertions, valid empty recognition versus technical failure, silence
denominators, nearest-rank percentiles, excluded warm-up, repeated-byte identity,
repeat disagreement, resource counters/reset detection, corpus annotations,
WAV normalization/duration, output redaction, local Docker admission, pinned
managed-container configuration and temporary-file cleanup on failure/Ctrl+C.
CI runs only these synthetic offline tests on Python 3.11, without Docker,
weights, audio synthesis or uploads. The same suite also covers adult confirmation,
review declarations, matched IDs, truncated/oversized/invalid WAVs, HTTP origin/
host/token admission, bounded concurrency, fixed-asset allowlist and redacted
responses. Local execution uses Python 3.14; remote CI and its Python 3.11 result
are reported on the PR. API/web checks were not rerun locally because no
application source or dependency changed.

## Open dependencies and next comparison

FARO-013 (#27, PR #74) is merged. [FARO-024 (#34)](https://github.com/stevdrey/faro/issues/34),
listed as a dependency of #39, remains **open**; this technical benchmark can be
reviewed independently, while retry/clarification and child-facing acceptance
remain pending. This change does
not complete or waive that dependency or authorize a pilot. The PR references
#39 without an automatic closing directive; its dependency resolution remains
pending. FARO-027 owns protected adult annotations.
FARO-036/037/041 own reviewed challenger adapters/artifacts/licenses;
FARO-038 owns the broader auditable multi-ASR A/B runner.

A future engine comparison must feed the same prepared bytes/references to every
installed adapter, retain identical scoring and denominator rules, record exact
artifact hashes and independent license reviews, and audit assessment safety
before ranking replacement candidates. Do not silently install or substitute
an optional provider to fill an empty table cell.
