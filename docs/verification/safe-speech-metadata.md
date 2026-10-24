# Safe speech metadata verification (FARO-013 / issue #27)

## Delivered boundary

One `CompletionState.speechRecord` lives only in browser memory for the current
completed attempt. Its random `attemptId` survives duplicate completion delivery
and changes after restart. It is never sent to the speech API. The frozen
`CompletedAttempt` and its mathematical trace are not rewritten by voice updates.

Capture records distinguish ready media, permission denial, empty media and
other stable capture errors. ASR records retain either a validated unconfirmed
hypothesis or a stable error. `recordingDurationMs` measures capture elapsed
time; `audioDurationMs` measures normalized PCM, not inference time. Valid ASR
responses supply effective language, engine, model, revision and latency.
Failures do not invent provenance or latency; normalized duration is retained
only if normalization completed before the failure/deadline.

`POST /api/v1/speech/transcriptions` adds `audioDurationMs` to its existing v1
success payload. It comes from server-owned `SpeechRequest.audioDuration()`
truncated to milliseconds: 2 bytes at 16 kHz is 0 ms, 32 bytes is 1 ms, and
960,000 bytes is 30,000 ms. Empty/odd/oversized bodies still fail; silence with
valid samples can produce an `EMPTY` hypothesis. Errors still return only
`{code}` with `Cache-Control: no-store`. Deploy web and API together: the updated
client requires this duration and rejects malformed/old responses safely.

Record construction projects owned fields only, retaining no Blob/PCM, playback
URL, extra response property or original exception. A new recording/transcription
clears the previous record. Cancel/delete, restart, activity/example changes,
hidden/leaving, reload and unmount discard it; active reselection preserves it.
Attempt identity and controller generations prevent obsolete callbacks from
repopulating it, including when repeating the same exercise. The transcription
component cancels its own controller synchronously on hidden `visibilitychange`
and on `pagehide`, rather than relying on React to unmount it after the parent
discards capture. The event handler aborts HTTP and invalidates the generation
before a response continuation can write a new record; listeners are removed
on cleanup.

Audio remains solely in the existing transient capture/playback and bounded
inference path. There are no files, browser storage, retry history, database or
new logging. Durable session storage/deletion belongs to FARO-025. LOCAL_ONLY,
provider independence and default-disabled synthetic-only ASR remain in place;
this does not authorize a child pilot or implement protected adult review.

## Reproducible checks

Use Java 27, Gradle 9.8.1, Node 24 and npm 12.2.0. Install locked dependencies
with lifecycle scripts disabled if needed; provision Chromium explicitly using
the existing `test:install-browser` command. No test downloads model weights.

```bash
gradle -p services/api --no-daemon test
npm --prefix apps/web run test:unit
npm --prefix apps/web run build
npm --prefix apps/web run test:e2e
node .agents/scripts/pr-size-preflight.mjs
git diff --check
```

- Web units cover association/replacement/clearing, frozen records, both
  durations, response allowlisting and rejection of old attempts/results.
- Java tests cover PCM-derived duration including fractional milliseconds and
  maximum length, `EMPTY` versus invalid bodies, safe failures and captured logs
  without synthetic transcript/provider diagnostic markers. Existing contracts
  also verify redacted request/hypothesis string representations.
- Chromium covers permission/empty-media errors without uploads, ASR success and
  empty results, retries, unchanged snapshots, empty browser storage, console
  privacy and lifecycle cleanup with late responses. The default build stays
  capture-only; preview tests use synthetic audio and intercepted ASR responses.
  Two regressions dispatch `visibilitychange`/`pagehide` and resolve a pending
  response in the same JavaScript task, using a fetch stub that deliberately
  ignores abort. They wait for the response continuation and React to settle,
  then assert an empty record, synchronous abort, discarded clip, unchanged
  completed attempt and empty browser storage. Both failed before the fix and
  passed after it.
- The existing optional real whisper.cpp smoke remains separate and requires an
  explicitly started verified baseline; these changes need no model download.

## Local verification results

- Java 27 / Gradle 9.8.1: **552 passed**, zero failures/errors/skips; this includes
  all 10 controller cases. The Gradle distribution was verified against its
  official SHA-256 before use. This is the original implementation's backend
  verification; the lifecycle follow-up changes only browser code/tests and
  this guide, so the unchanged backend suite was not rerun for that follow-up.
- Node 24.21.0 / npm 12.2.0: **109 unit tests passed**; typecheck and production
  build passed again after the lifecycle fix. Temporary tooling was prepared outside the repository; no
  dependency manifest, lockfile or verification checksum changed.
- Chromium: the complete suite after the lifecycle fix had **141 passed**, zero
  failures and **1 skipped** (the optional real whisper.cpp smoke, because no
  explicitly started baseline/API was used). The two new regressions were also
  checked in isolation: both failed with retained ASR records before the fix,
  and both passed after synchronous lifecycle cancellation. Browser traces,
  screenshots and videos stayed off.
- PR preflight: **20/40 changed files**, within the preferred 30-file scope;
  `git diff --check` passed. Existing CI remains the authoritative gate.

Local tests needed execution outside the restricted sandbox for Node subprocesses,
Chromium/loopback servers and Gradle native services. No project configuration or
privacy control was weakened to run them.
