# FARO-009 — Short, replayable spoken instructions

## Delivered behavior

The four current exercise examples have a fixed instruction naming their numbers
and explaining how to manipulate the visuals and finish with **Listo**. After
completion, a separate neutral clip explains restarting or choosing another
activity, regardless of the child's answer. No clip supplies the solution.

**Escuchar guía** (its accessible name matches the visible label) starts or restarts
a clip from zero; the square **Detener guía** control stops and rewinds it. Both support keyboard and touch and have 48 px minimum
height. Guidance is opt-in, without autoplay on entry, completion or restart.
**Ver instrucciones** offers the exact transcript. Existing concise screen
instructions and all visual controls remain available without audio.

Finishing/restarting, changing activity/example and unmounting stop the old
player, including a pending `play()` promise. Hiding the page stops it too,
without automatic resumption. Reselecting an unchanged activity preserves it.
An unavailable asset, playback error or browser rejection shows a neutral retry
message without blocking exploration or completion. Playback uses one audio
element per active screen. Unsupported operands do not select a clip that names
different numbers. Existing v1/v2 action traces and completion focus are retained.

## Assets and boundaries

Seven committed, mono 24 kbps Ogg Opus files (about 20–37 KB) last approximately 7–13 seconds: four exercise
clips, one next-action clip and two future recording prompts. They were generated
locally using eSpeak NG 1.52.0, the built-in `es-419` formant voice, FFmpeg 8.1.3 `libopus` and Faro-authored
Costa Rican Spanish wording. See the [asset manifest and provenance](../../apps/web/public/audio/README.md).
The generator is an explicit developer command, never an install/build hook or
runtime dependency. No npm dependency was added or changed.

The web server serves same-origin assets with `preload="none"`; only explicit
playback requests load them. No microphone, runtime speech synthesis, ASR/model,
API call, storage, telemetry or external service is introduced. No real voice
recordings or private child evidence were used. `recordingStart`/`recordingStop`
are catalog entries for FARO-011; recording, consent, permission and stop/cancel
controls remain that issue's work. They are not shown in today's demos.

## Automated verification

From the repository root, with installed locked dependencies and the provisioned
Playwright Chromium:

```bash
npm --prefix apps/web run test:unit
npm --prefix apps/web run test:e2e
npm --prefix apps/web run build
node .agents/scripts/pr-size-preflight.mjs
```

Unit tests check fixture-to-clip selection, unsupported operands, exact
transcript/manifest agreement, SHA-256 hashes, valid mono Ogg Opus pages, a 64 KiB size bound and the
15-second clip limit. Browser tests cover replay/stop, keyboard repeat, touch
layout, natural completion events, changing contexts, hidden-page cancellation,
late playback promises, rejected playback, media errors and retries. They also
check unchanged semantic completion, reset focus, no automatic playback, storage,
API or external requests, and real Chromium playback/decoding of shipped assets.

Local verification on 2026-10-22: **67 unit tests passed**, **85 browser tests
passed**, typecheck/production build passed, and PR-size preflight passed at
**21/40 files**. Mobile and desktop screenshots were inspected with synthetic
fixtures. Execution used Node 24.21.0, the existing locked dependencies, installed
Playwright Chromium and npm 11.19.0 for running scripts only; no dependency
installation was performed. Fresh installs/CI still require the documented npm
12.2.0 and disabled lifecycle scripts. Java/API checks were not rerun for this
web-only change.

## PR #69 CI and review corrections

The initial CI run failed when the touch test returned a block to an off-screen
reserve. The helper scrolled only the source into view and sent a browser touch
to the target's off-screen center. The same failure was reproduced locally after
expanding the guidance and scrolling upward before returning the block. The
helper now scrolls both elements into view, asserts their visibility, and then
reads fresh coordinates. The regression covers 700 px and 1000 px viewport
heights, corrections, cancellation and tap alternatives without adding retries.

The Codex review also identified a queued `ended` event from earlier playback
disabling **Detener guía** after replay. The handler now checks the element's
current `ended` state before changing playback state. Two regression cases
failed before the fix and now preserve stop/replay while a new play request is
either resolved or pending; actual media completion remains covered.

The six affected touch/audio scenarios passed five repetitions each: **30/30**,
with retries still disabled. The complete browser suite with `CI=true` passed
**88/88**; unit tests passed **67/67**, typecheck/build passed, and PR-size
preflight passed at **22/40 files**. No dependencies, assets, domain contracts, or API
code changed in this correction.

## PR #69 code-review corrections

A follow-up review led to these changes:
- **Detener guía** now uses `aria-disabled`, so keyboard focus stays on it after it stops the clip.
- Pauses from outside the page (media keys, OS interruptions, a lost headset) return the controls to idle; resuming from outside shows playback again.
- The button's accessible name now matches its visible label.
- Only failures are announced through the live region, so a screen reader does not talk over the clip.
- Assets moved from 3.3 MB of WAV to about 200 KB of Ogg Opus.
- Clip selection compares against the exercise fixtures instead of copying their numbers.
- The screens share `useExerciseGuidance`.
- The generator uses one settings object, stages all clips before replacing them, and produces identical checksums on reruns.

Local results on 2026-10-22: unit **67/67**, browser suite with `CI=true` **90/90**,
the guidance spec repeated five times with retries disabled **80/80**, typecheck/build
passed, and PR-size preflight passed at **22/40 files**. Opus playback was verified
in Chromium only; Safari/iOS before 18.4 cannot decode it and falls back to the retry
message.

## Manual acceptance and limits

The browser tests verify playback and interaction, not intelligibility or a
child's independent comprehension. No child usability session has been run.
The generic Latin American synthetic accent is not a validated Costa Rican
voice. Before a supervised pilot, an adult should listen to every clip, verify
number pronunciation, voseo and comfortable volume/pace, then try a basic attempt
using the speaker icon, visual controls, **Listo**, and the loop-arrow restart.
Record only a concise verification summary, never private recordings.

If listening reveals a problem, replace the fixed assets and update their source
text, provenance and checksums before pilot use. The existing recording and pilot
privacy prerequisites still apply.
