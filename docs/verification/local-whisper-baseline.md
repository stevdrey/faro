# FARO-010 — Standalone local whisper.cpp baseline

## Delivered behavior

[Issue #11](https://github.com/stevdrey/faro/issues/11) adds explicit Python
`setup`, `start`, `stop` and `smoke-test` commands for a Docker CPU baseline.
The runtime image and source revision are pinned; the multilingual base weights
are downloaded from an immutable revision and verified by size and SHA-256 before
installation and every start. Image/model provenance and independent license
sources are recorded in the [artifact manifest](../../infra/whisper/artifacts.json).

The service publishes only `127.0.0.1:8085`, runs as an unprivileged user with a
read-only filesystem/model and tmpfs, and retains no Docker logs. Local requests
bypass proxies and reject redirects. The generated WAV is PCM16 little-endian,
mono, 16 kHz, at most 30 seconds; it and intermediate synthesis files are deleted
on completion/failure/interruption. HTTP error bodies and transcripts are not
included in CLI output. See the [runbook](../../infra/whisper/README.md) for
requirements, commands, cleanup, licenses and failure codes.

## Automated checks

From the repository root:

```bash
python3 -m unittest discover -s infra/whisper/tests -v
git diff --check
node .agents/scripts/pr-size-preflight.mjs
```

The offline suite covers incomplete, oversized, interrupted and corrupt downloads;
atomic installation and cache reuse; missing/tampered/symlink model rejection;
container privilege, CPU, loopback and log settings; health polling and startup
failure cleanup; ownership-scoped idempotent stop; proxy bypass and redirect
rejection; multipart bytes/fields; HTTP errors, timeouts, malformed/oversized JSON
and empty/error text; WAV format/duration; redacted summaries; and temporary-file
cleanup after synthesis/inference errors and interruption.

The `whisper-baseline` CI job runs the suite using standard-library Python only.
It does not download weights, pull Docker images, synthesize audio or contact a
real inference service. Application API/web jobs remain configured as before.

## Local acceptance results

Verification on **2026-10-22 (America/Costa_Rica)** used Linux amd64, Python
3.14.8, Docker Engine 29.9.2, eSpeak NG 1.52.0 (`es-419`) and FFmpeg 8.1.3.
FFmpeg's installed build enables GPL components; it is used only as a local
developer tool, not redistributed by this change.

- Explicit setup downloaded the pinned model, verified all 147,951,465 bytes and
  its SHA-256, and pulled the exact image recorded in the manifest.
- Startup reached `/health` with `status=ok`. Actual Docker inspection confirmed
  the pinned image, UID/GID 65534, read-only root and model mount, tmpfs, CPU-only
  execution, `none` log driver and the sole published binding `127.0.0.1:8085`.
- The real `/inference` smoke test returned valid nonempty Spanish text: a
  **4.597-second** generated WAV, **904 ms** inference time and **60 transcript
  characters** in the recorded run. These are one-run observations, not a latency
  benchmark or an accuracy score. No raw audio or response was saved in Git.
- A separate in-memory content review found substantial misrecognitions of the
  synthetic phrase, including critical numbers. Three slower/shorter synthetic
  variants also had errors; the original fixed sample was retained. Successful
  transport and nonempty recognition must not be interpreted as faithful math
  transcription or validated Costa Rican Spanish recognition.

The upstream README lists `--no-context`, but the actual pinned executable
rejects that argument and exits. Startup was corrected to the supported
`--max-context 0` and verified against the real runtime. Failed startup's container
was automatically removed. Documentation uses the image's actual CLI behavior.

Final verification: **31/31 offline tests passed**, including a run with Python
resource warnings promoted to errors. Whitespace checks passed; PR-size preflight
passed at **11/40 changed files** against origin/main. Git confirmed that weights
and Python caches are ignored. No application dependencies or lockfiles changed.

Repeated setup reused verified cached weights. An initial combined restart
reported DOCKER_START_FAILED; an immediate diagnostic cycle passed. Stop now waits
for automatic removal to release the name before returning, with regression tests
for delayed removal and timeout. The final real start/stop/start/smoke/stop/stop
cycle passed, with **831 ms** for the same 4.597-second WAV and 60-character result.
The service was left stopped. Final inspection confirmed no managed container,
no health response and no generated audio directories. Public image/model caches
remain local for reuse.

## Boundaries

This delivers a reproducible local technical baseline, not a child-speech
benchmark. No Java/TypeScript contracts, ASR provider selection, browser flow,
assessment logic or application audio endpoint changed; the fake remains the dev
provider. Backend integration and audio normalization remain FARO-012, privacy
enforcement FARO-040, broader artifact inventory FARO-041 and accuracy benchmarking
FARO-014/038. Java/web tests are not rerun for this standalone tooling change.
The full CI workflow still needs execution on GitHub; local tests do not claim a
remote CI result. Docker bridge networking is not backend `LOCAL_ONLY` enforcement.
