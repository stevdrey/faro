# Local ASR benchmark (FARO-014 / issue #39)

This standalone Python 3.11+ tool measures the installed, pinned whisper.cpp CPU
baseline. The CLI uses **synthetic Spanish only**; a separate opt-in browser
guide supports a consenting adult testing their own voice. No child recordings,
application endpoint, provider selection, grading or new dependency is added.
Explicit small-model setup and opt-in private WAV recovery are described below.
The supplied synthetic corpus's formant voice is not validated Costa Rican
or child speech. Its source phrases are intended references, not independently
audited human transcriptions of the generated waveforms.

## Reproduce

Use Linux amd64 with cgroup v2, a local Docker Unix socket, and the
[baseline requirements](../../infra/whisper/README.md) (eSpeak NG `es-419`, FFmpeg,
verified cached image/model). If caches are absent, perform the baseline's
explicit reviewed `setup` separately. The benchmark never installs artifacts.

From the repository root, with no other clients using the baseline:

```bash
python3 infra/whisper/baseline.py start
python3 tools/asr-benchmark/benchmark.py --repeats 3
python3 infra/whisper/baseline.py stop
```

Stop the baseline even after failure/interruption. The benchmark neither creates
nor removes containers, so it does not take ownership of a preexisting service.
For a fresh lifetime memory peak, stop any baseline you own before starting it.
Do not interrupt unrelated containers. Run on an otherwise idle host and record
power/load conditions when comparing future candidates.

`--repeats` accepts 1–5 (default 3). One warm-up precedes the scored runs; startup,
model verification, synthesis and warm-up are excluded from latency and CPU.
Exit status is 0 for a complete technical run (even with recognition errors), 1
for technical failure and 130 for interruption. Per-request technical failures
are counted with no automatic retry or engine fallback; a failed scored request
keeps its place in the number-accuracy denominator. Setup, warm-up, resource or
environment-probe failure aborts the run with a stable code and no report.

Stdout contains only an aggregate JSON report. It may be redirected initially to
an ignored location such as `tools/benchmark-results/private/summary.json`
(create that directory first), reviewed, then copied for publication. Do not
automatically upload benchmark output in CI. A reviewed synthetic result and its
interpretation live in [verification](../../docs/verification/local-asr-benchmark.md).

## Test with your own adult voice

```bash
python3 infra/whisper/baseline.py start
python3 tools/asr-benchmark/recorder.py
```

Open the exact random loopback URL printed by `recorder.py` in a browser with
microphone support. It serves only the recording guide, not the repository.
Confirm you are an adult using your own voice, then record each of the same
12 phrases at your natural pace. **Grabar** alone requests microphone access;
the indicator/elapsed time and **Detener**/**Cancelar grabación** remain visible.
Recording stops at 30 seconds. Listen locally, re-record if needed, and confirm
you spoke the displayed phrase exactly before accepting each clip. This reference
review is by the speaker, not an independent human annotator.

**Medir con mi voz** explicitly sends the 12 approved normalized WAVs to this
loopback-only guide, which measures the managed local Whisper service. It adds
one generated two-second silence control (not recorded room noise). Three passes
measure timing/stability; accuracy's denominator is **12 unique adult clips**,
not the synthetic corpus's 36. No artificial speech-rate control is applied.
These samples are a separate dataset; do not treat their percentages as a matched
human-versus-synthetic experiment or as population/child-speech evidence.

The guide also displays one **first-pass comparison card** per phrase, using the
same responses scored by the aggregate: the speaker-confirmed source reference,
exact unconfirmed Whisper text, expected numbers, the scorer's extracted ordered
numbers and list agreement. Each speech card can play its original browser-memory
WAV. The reference is what the speaker confirmed, not a second automatic claim
about what was actually said; compare playback when diagnosing discrepancies.
The generated silence control has its own card and no number-agreement verdict.
Technical failures carry `null` transcript/numbers/agreement, distinct from a
successful empty response with an empty number list. Warm-up and later passes
never overwrite the first-pass comparison.

First-pass cards also carry version-1 **diagnostic annotations**, with no automatic
correction. A small explicit allowlist recognizes square-bracket nonverbal labels
for music, applause, laughter, noise and silence in Spanish/English, plus
`[BLANK_AUDIO]`; matching ignores case/accents and preserves the exact raw tag.
`[Música]` alone is `nonverbal_only`; `[Música] seis` is `mixed`. Empty output stays
`empty`; unknown markers such as `[inaudible]` or `[seis]` remain
`speech_or_unclassified`. Ordinary words such as `música` without a recognized
bracket label are not discarded. These are text annotations, not evidence of
what sound actually occurred, and no nonblank output is called invented speech.

The numeric warning conservatively marks noncanonical tens-plus-unit text joined
by whitespace and `y`, such as `10 y 6`, `diez y seis`, `30 y seis` or `treinta y 6`.
It reports only the exact fragment and its separate integer components, never
their sum or a replacement interpretation. Canonical `treinta y seis` already
supported by the scorer is not flagged. Punctuation boundaries and digit decimal
fragments are excluded. This bounded warning is not a complete Spanish parser:
an enumeration can also match, so the adult must review the audio/context.
The raw hypothesis, extracted number list, number agreement, strict accuracy and
WER are unchanged; there is no academic assessment or automatic reclassification
of a mismatch as a correct answer.

These details travel only in the adult guide's `no-store` response and are rendered
as plain text, alongside browser-local audio players. They are excluded from the
aggregate JSON, synthetic CLI output and committed measurements. No comparison
file, download, browser storage, logging or export is added. Deletion, withdrawing
confirmation, hiding/leaving and starting a new measurement clear comparison DOM,
stop comparison playback and revoke playback URLs. Browser abort/generation checks
prevent a late response from restoring deleted text or players. The server releases
the response's transient references after the request, as with its audio buffers.

Native `MediaRecorder` audio is decoded and downmixed/resampled with browser
Web Audio to PCM16 LE mono at 16 kHz. Only WAV bytes and fixed fixture IDs are
sent; reference text never comes from ASR or an uploaded filename. The server
requires own-voice and reference-review declarations, exactly the 12 expected
IDs in order, bounded complete WAVs ≤30 seconds, a ≤16 MiB request and one active
measurement. It checks exact Host/Origin, a random per-launch URL token, JSON
content type and no chunked transfer. No CORS, external assets, access logs,
browser storage or automatic report export are used. The default mode writes no
audio files; only explicit recovery mode writes the private checkpoints below.
Responses are `no-store`; CSP restricts network access to the guide's origin.

In default mode, audio and hypotheses stay in browser/server memory. **Borrar todo**, withdrawing
confirmation, hiding or closing the page discards browser recordings/results
and releases microphone tracks; late permission/data callbacks are invalidated.
A measurement already accepted by the server may finish locally after browser
abort, bounded by the finite request loop and per-request timeout. Its buffers
are released on completion/failure or when the guide process stops. **Borrar
todo** does not promise to cancel inference already accepted. No adult recording,
voice hash or identifying transcript is committed or automatically published.

Stop the guide with Ctrl+C, then stop only your managed baseline:

```bash
python3 infra/whisper/baseline.py stop
```

This uses the standalone guide, **not** the Faro application's synthetic-only
API/preview; those gates stay unchanged. A checkbox is an operator declaration,
not guardian consent or authorization to use someone else's or a child's voice.
The microphone guide requires a browser; eSpeak/FFmpeg are not used on this path.

## Matched base versus small comparison

```bash
python3 tools/asr-benchmark/models.py setup-small
python3 tools/asr-benchmark/models.py verify-small
python3 infra/whisper/baseline.py start
python3 tools/asr-benchmark/models.py start-small
python3 tools/asr-benchmark/recorder.py --compare-small
```

Start each runtime only if absent. The explicit setup downloads only public
`ggml-small.bin`: **487,601,967 bytes**, SHA-256
`1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b`.
[small-artifact.json](small-artifact.json) pins the same immutable converted-model
revision as base, exact HTTPS URL, byte length, SHA-256 and independent MIT model
license sources. The hash/length were checked against the repository's LFS
metadata before download. Installation uses the existing bounded atomic downloader;
interrupted/unverified files are not installed, corrupt caches are rejected, and
starts reverify cached weights without downloading. The existing digest-pinned
official CPU image is reused with `--pull=never`.

Small runs separately as `faro-whisper-small`, on `127.0.0.1:8086`, with a distinct
Faro ownership label. Base remains on 8085, with its original configuration/model
and application provider untouched. Both use the same runtime image, Spanish,
zero-context command, four CPU threads, temperature 0 and translation disabled.
Both retain the baseline's unprivileged user, dropped capabilities, read-only
filesystem/model mount and disabled logging. The internal model mount keeps the
baseline's generic `/models/ggml-base.bin` destination, but small's source path,
bytes, manifest/model ID and checksum are independently verified as small.

**Comparar base y small** submits one validated recording set. The server admits
both installed adapters before sending any audio, canonicalizes each WAV once,
then passes the same immutable clip/byte objects to base followed by small.
Each gets one excluded warm-up, both run before any scored request so a small
warm-up failure aborts without discarding base results, then three measured
passes: 39 measured requests per model on 12 unique speech clips plus generated
silence. Admission hashes each model file once per guide process and re-hashes it
whenever its identity, size or modification time changes. Inference is serial;
the fixed order and shared host load can affect timings. Model loading is excluded,
and each memory peak is that container's lifetime peak, including previous use.
Two resident models also consume combined host resources beyond those per-model
figures. No audio fingerprint or private reference is published.

The page shows model-specific accuracy, WER, p50/p95, memory and technical failures,
plus a selector for each model's first-pass phrase comparisons. Raw hypotheses,
diagnostic fragments and comparison sets stay only in memory and are cleared by
the existing deletion/visibility boundaries. A missing/invalid small runtime
aborts admission; no alternate engine or cloud service is chosen. Technical
failures remain explicit, and no winner or mathematical judgment is inferred.

Stop only these managed services when finished:

```bash
python3 tools/asr-benchmark/models.py stop-small
python3 infra/whisper/baseline.py stop
```

## Optional temporary WAV recovery

The repository owner explicitly requested recoverable temporary recordings after
the original memory-only flow. This mode is **off by default**:

```bash
python3 tools/asr-benchmark/recorder.py --compare-small --recover-recordings
```

The guide announces recovery mode before enabling capture. Each accepted,
speaker-reviewed clip is checkpointed locally before advancing. The default
directory is `tools/benchmark-results/private/voice-recovery/`, already ignored
by Git: mode **0700**, normalized PCM WAVs and metadata **0600**. Only this adult's
fixed-fixture WAVs are written; no ASR transcript, result, exception, name or
browser storage is saved. The private manifest contains clip IDs, byte lengths,
SHA-256 integrity hashes, reference-fixture hash, a random generation and expiry.
These voice hashes stay private and do not enter published benchmark reports.

Writes use private staged files, fsync and atomic replacement. Only acknowledged
checkpoints are recoverable; an interruption before acceptance/acknowledgment may
lose that pending clip. A process lock prevents two guides from sharing the same
recovery directory. Model comparison uses the in-memory WAVs, not a runtime audio
file/conversion path. Recovery is a separate user-authorized file-retention layer.

After closing/reloading the page or restarting the guide, reconfirm own-voice
authorization and select **Recuperar grabaciones**. Restored IDs, format, duration,
lengths, checksums, reference version and contiguous order are validated. Restoring
opens no microphone and performs no ASR until comparison is explicitly chosen.
Recovery is disabled while microphone permission, recording or audio preparation
is active. The handler also rejects recovery during capture, preserving its
generation so Stop, Cancel and the 30-second limit can release the microphone.
Invalid/corrupt/symlink files fail closed without logging their contents. A failed
checkpoint leaves the pending browser recording available for retry.

Checkpoints expire **24 hours after the session's first retained clip**, without
extending the deadline on later saves (an empty session holds no audio). The
running guide purges expired files at most about one minute later and checks
expiry before every recovery/save. When an expiry rotates the session, the open
page adopts the new session on its next save or comparison and re-checkpoints its
accepted clips once. If the guide/machine is stopped, cleanup occurs on the next
start/access; this is not an OS-scheduled deletion guarantee while the machine is
off. Invalid metadata (including a changed reference fixture) still fails closed
and requires explicit operator cleanup, but known clip WAVs and staged files whose
modification time is 24 hours old are deleted on every start attempt and purge.
Replacing a checkpoint first shrinks the manifest to the preceding contiguous
clips, so an interruption never leaves a checksum that describes missing bytes;
identical re-saves write nothing.

Hiding/closing clears browser audio/results but intentionally preserves the
temporary disk checkpoint. **Borrar todo, incluido el respaldo** and withdrawing
authorization delete the checkpoint WAVs; the session generation rotates so late
writes cannot restore deleted data. Deletion also works while inference finishes
locally. Disk files are ordinary private, unencrypted WAVs; unlinking them is not
cryptographic erasure of storage media/backups. No upload/publication is added.
This mode never authorizes children's or another person's recordings.

## Matched corpus and scoring

[corpus.json](corpus.json) is versioned, Faro-authored CC0-1.0 text with ordered
critical-number annotations: 12 phrases × 3 speeds (120/140/170 words/minute),
one fixed `es-419` formant voice, pitch 50. One additional two-second PCM silence
control stays outside speech accuracy/WER denominators. Cases cover six/seven,
short utterances, addition, ascending/descending sequences, tens/unit exchange,
subtraction, correction, zero, repeated numbers and accented compounds.

All clips are synthesized once with the existing baseline helper, normalized to
PCM16 LE mono at 16 kHz, checked for positive duration ≤30 seconds and retained
only in memory for repeated requests. A private mode-0700 temporary directory
holds the two synthesis intermediates, overwritten per clip, and is removed on
success, error and Ctrl+C. Abrupt host termination can bypass cleanup; inspect
only `faro-benchmark-*` temporary directories belonging to your run. The report
includes a SHA-256 of the manifest and an ordered fingerprint of canonical WAV
bytes (each preceded by its eight-byte big-endian length). Record the synthesis
versions: another eSpeak/FFmpeg build can produce different bytes.

- **All-critical-numbers accuracy:** exact ordered number-list agreement / 36
  unique speech clips on the first pass. Missing, substituted, extra or reordered
  numbers fail the clip; repeated numbers must remain repeated. Accents/case are
  normalized for this scorer; `6` and `seis` agree. It handles Spanish 0–99
  (including `un/una`, `veintiún`, tens + `y` + units) and digit tokens. Negatives,
  fractions, decimals, hundreds in words and contextual number disambiguation
  are outside this fixture's scope. This is benchmark scoring, not FARO-018's
  application number interpreter.
- **WER:** total Levenshtein word edits / total reference words, on successful
  first-pass speech responses (including valid empty responses). Lowercase and
  NFC normalization, punctuation discarded, math symbols retained; accents and
  spoken-versus-digit spelling remain distinct. Insertions can make WER >100%.
  Report word and clip denominators plus technical failures; WER is undefined
  (`null`) when no reference words were scored. It is not number accuracy.
- **Silence:** count nonblank responses and technical failures independently;
  never reward an engine for matching two empty number lists.
  New runs additionally distinguish `silence_empty`, `silence_nonverbal_only`
  and `silence_speech_or_unclassified` (including mixed label/text output).
  The original `silence_nonempty` still counts every nonblank successful response,
  including labels; it is not a count of hallucinated words. Speech results also
  count unique first-pass clips with a numeric warning in
  `speech_numeric_ambiguities`. Aggregate counters contain no text/fragments.
  The earlier committed synthetic report predates these additive counters and
  is not backfilled from discarded responses.
- **Latency:** nearest-rank p50/p95 over all 37 × repeats sequential HTTP
  requests, including silence and technical failures. Time surrounds only the
  adapter call; transport, local inference and JSON decoding are included.
  This is not end-of-recording-to-feedback latency. Repeats measure stability
  and timing, not additional independent speech examples. A separate count
  reports clips whose later exact text/status differed from their first pass.
- **Resources:** cgroup v2 `cpu.stat` usage delta in CPU seconds (not wall time),
  `memory.current` before/after and `memory.peak` for the container's **entire
  lifetime**, including startup/warm-up and any earlier use. CPU includes minor
  resource-reader activity and idle time between requests. These counters cover
  the runtime container, not the Python harness, synthesis, host RAM or RSS.
  Model file size/checksum are separate from RAM. Missing counters fail the run.

## Safety, provenance and candidates

Before sending audio, the adapter verifies the cached model's size/SHA-256 and
the managed container's pinned image, model mount, exact CPU command/entrypoint,
loopback port binding and disabled log driver. Requests reuse the baseline's
fixed loopback endpoint, bypass proxies, reject redirects, bound response bytes
and enforce a 120-second socket timeout. Text is bounded to 16,000 characters;
blank output is a valid empty recognition, distinct from transport failure.
No source text/reference prompt is sent to inference. Raw responses, exceptions,
audio, clip IDs and per-clip transcripts never enter the aggregate report or
general logs. Only the adult guide's transient response carries the explicitly
requested per-phrase text comparison described above.
References/hypotheses/WAVs are transient Python objects, reclaimed by the runtime;
this is not cryptographic memory wiping or an OS egress firewall.

The only installed benchmark adapter is Whisper. Moonshine and Vosk are explicitly
`not_measured` with `adapter_not_installed`; package discovery alone cannot prove
reviewed model artifacts or a usable adapter. Optional engines are not downloaded,
imported or silently substituted. Their production adapters/license inventories
remain FARO-036/037/041. FARO-038 can call the provider-independent `measure`
function with new reviewed adapters and the **same prepared clips**, pinning
exact artifacts and using one identical report/scoring protocol. Until then,
there is no measured comparative ranking or justification for replacing Whisper.

Number agreement does not establish preserved mathematical meaning. Operators,
connectives, false-child-error attribution and assessment safety are **not
measured** here; they require human annotations and the future assessment flow.
FARO-013 is merged. FARO-024 (#34) is still open: this independent technical
benchmark does not complete retry/clarification or authorize pilot use. FARO-027
and FARO-038 own protected annotations and the broader multi-ASR audit. Even a
consented single-child benchmark could not support population generalization.

The runtime, weights and synthesis tools retain their independently documented
[licenses/provenance](../../infra/whisper/README.md#provenance-and-licenses).
No new package, image or voice is installed or redistributed here. Small's public
weights are downloaded only by the explicit reviewed setup above and stay ignored.

## Offline verification

```bash
python3 -W error::ResourceWarning -m unittest discover -s tools/asr-benchmark/tests -v
python3 -W error::ResourceWarning -m unittest discover -s infra/whisper/tests -v
git diff --check
node .agents/scripts/pr-size-preflight.mjs
```

CI runs offline tests on Python 3.11 in the existing ASR tooling job. It does not
generate audio, download models, run Docker inference or publish measurements.

The web CI job also runs this focused capture regression after installing its
existing pinned Chromium browser:

```bash
node tools/asr-benchmark/tests/recorder-capture.cjs
```

It serves the real guide assets through an in-memory browser route fixture and
uses Chromium's synthetic microphone. With a saved synthetic checkpoint, it
checks recovery is blocked during pending permission and active recording,
including direct handler invocation. Stop, Cancel and the actual 30-second limit
must release all tracks; subsequent recording and idle recovery must still work.
No running guide, ASR, physical microphone, private directory or audio file is used.

Optional browser integration check, with the baseline and guide running and
the repository's existing locked Playwright installation available:

```bash
node tools/asr-benchmark/tests/recorder-browser.cjs 'http://127.0.0.1:8765/REPLACE_WITH_GUIDE_TOKEN/'
```

Run only against a separate test guide. **Never use the disposable browser test
against a guide containing real recovery recordings**, because it exercises deletion.
Use a fresh temporary `RecoveryStore` and `recorder.serve` on a separate loopback
port for recovery tests; the tests do not change the user's checkpoint directory.
The check always uses Chromium's **synthetic** microphone, records/reviews 12 clips,
performs 39 local inference requests per enabled model,
checks 13 first-pass comparison cards and 12 browser-local audio players, checks
the separate aggregate report and erases the browser results. Recovery mode also
checks identical WAV hashes after reload, zero new microphone requests and deletion
of the synthetic checkpoints. No actual microphone, personal audio, screenshot,
video or trace is used or saved; synthetic recovery WAVs live only in the isolated
temporary test directory.
