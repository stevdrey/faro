# FARO-011 — Explicit browser voice capture

## Delivered behavior

Blocks, both sequence examples and subtraction offer an optional explanation
panel after **Listo**, without changing the neutral completion or frozen action
trace. An adult checks the local confirmation and chooses **Habilitar grabación**.
Authorization lives only in the open page's memory, survives activity switches and
is forgotten on reload/close. It is neither authentication nor documented guardian
consent; the existing supervised-pilot prerequisites still apply.

Authorization alone never requests microphone access. **Grabar explicación**
requests browser permission and records only on explicit activation. This is a
tap-to-start/tap-to-stop interaction, consistent with the existing spoken prompts.
Controls support mouse, touch, Enter and Space, have 48 px minimum targets and
reject held-key repeat. One stable recording button retains keyboard focus across
start, stop and replacement; cancel/delete return focus to it.

The panel has a text/icon indicator, elapsed-time display, **Detener grabación**
and **Cancelar**. A 30-second timer stops and retains the clip. Stopping first
releases hardware, then waits for the final `dataavailable` and `stop` events.
Only one nonempty clip is retained, using the actual browser MIME/encoding and
measured duration. Browser event scheduling controls timer precision; this is not
sample-accurate PCM trimming or normalization.

**Escuchar mi grabación** and **Detener reproducción** review it locally.
**Borrar** drops it; **Grabar de nuevo** discards the old clip before requesting
another recording. There is no automatic playback, permission request or restart.
The existing recording-start guidance is replayable on request; the recording-stop
wording is visible while recording. No new speech asset is generated.

## Resource and privacy boundaries

- `CaptureController` is independent of React, model providers and action contracts.
  Its phases are available, requesting, recording, finalizing, ready and error.
  Duplicate starts/stops are ignored; generation checks reject stale permissions
  and media callbacks. A late permission grant releases every returned track.
- Cancel discards pending chunks even during permission/finalization. Reset,
  activity/example changes, hidden pages, `pagehide` and unmount stop capture and
  playback and discard the clip. Returning does not resume it. Selecting the
  unchanged activity/example preserves the current attempt/clip.
- Every terminal path stops tracks, cancels timers and removes media listeners.
  Playback cleanup removes the source, revokes the Blob URL and releases its clip
  reference. This releases application references; it does not promise physical
  overwriting of browser-managed memory.
- One application-scoped audio coordinator serializes guidance and recorded-clip
  playback. Permission requests, recording and finalization hold an exclusive
  capture lease. Starting capture synchronously stops playback; hidden/pagehide
  cleanup also stops playback before React unmount cleanup runs.
- Permission denial, missing/busy hardware, unsupported APIs/insecure context,
  recorder errors, interruptions and empty results show bounded neutral messages.
  Raw browser exception text is never displayed or logged. Failed clips are not
  retained, and visual exploration/completion remain usable.
- No backend, normalization, ASR, speech recognition API, upload, download,
  persistence, logging or telemetry is added. The transient clip belongs to the
  mounted completed attempt; `CompletedAttempt`, v1/v2 traces and Java ASR contracts
  remain unchanged. FARO-012 owns subsequent normalization/integration.

## Reproducible automated checks

Use the existing locked dependencies and explicitly provisioned Chromium:

```bash
npm --prefix apps/web run test:unit
npm --prefix apps/web run test:e2e -- recording.spec.ts
CI=true npm --prefix apps/web run test:e2e
npm --prefix apps/web run build
node .agents/scripts/pr-size-preflight.mjs
```

Unit tests inject synthetic streams/recorders and a controlled clock. They cover
multi-chunk finalization and MIME/duration, duration bounds, repeated activation,
cancel in every capture/result phase, stale permission grants/rejections, error
classification/recovery, construction/start/stop failures, stream mute/end,
unexpected stop, empty audio, cleanup/reuse and stale audio ownership leases.

The recording browser spec covers all four examples under React StrictMode,
adult enablement, keyboard focus, touch sizing at 360 px, replay/delete/replace,
coordination with guides, timer finalization, cancel while finalizing, all cleanup
contexts during active capture and playback, permission resolution after each
cleanup context, same-selection preservation, authorization lifecycle and neutral
error handling. Browser tests also verify unchanged completion traces.

One test uses the real Chromium `getUserMedia` and `MediaRecorder` APIs with
`--use-fake-device-for-media-stream` and `--use-fake-ui-for-media-stream`. It verifies
a playable captured clip, ended hardware tracks, no permission request before
explicit activation, no API/upload requests, same-origin GET requests only and
empty local/session storage. It uses no physical microphone or saved audio fixture.
Screenshots, traces and video remain disabled by default.

## Verification results — 2026-10-23

- Unit tests: **91/91 passed**, including 24 new capture/coordinator scenarios.
- Recording browser spec: **34/34 passed**.
- Complete browser suite with `CI=true`: **124/124 passed**, retries disabled.
- Typecheck and production build: **passed**; no dependency changes/install.
- Desktop and 360 px mobile recording panels: synthetic UI screenshots inspected.
- Git whitespace check: **passed**. PR-size preflight: **17/40 files** relative to
  `origin/main`, including this documentation and all new source/tests.

Execution used Node 24.21.0, npm 11.19.0 for existing scripts only and the installed
locked Playwright/Chromium. Fresh dependency installation/CI still requires the
repository's npm 12.2.0 and disabled lifecycle scripts. The filesystem sandbox
blocked loopback binding and Node's Git subprocess; browser tests and the read-only
preflight passed outside that sandbox. Java/API checks and dependency audits were
not rerun for this web-only change without dependency updates.

## Manual acceptance still required

Test Firefox/Safari, physical microphone permissions/disconnection, mobile browser
lifecycle and assistive-technology behavior before declaring those environments
supported. On a supported browser, an adult should finish an activity, confirm
supervision, optionally listen to the instructions, explicitly record a short
technical sample, stop, replay, delete and repeat. Confirm that the browser's
microphone indicator turns off on stop/cancel and after hiding/restarting/leaving.
Test denial and recovery without making visual work dependent on audio.

These checks establish capture/lifecycle behavior in Chromium; they do not validate
child comprehension, voice intelligibility, ASR accuracy or authorization for real
child-data use. No real child audio or unredacted pilot evidence was used or saved.
