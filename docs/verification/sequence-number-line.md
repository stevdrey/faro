# FARO-006 sequence number-line verification

Issue: [#23](https://github.com/stevdrey/faro/issues/23).

## Delivered behavior

The local activity picker preserves blocks as the initial screen and adds
**Secuencias**. The descending exercise uses the unchanged shared v1 fixture
`30, 28, □`; a synthetic local ascending example presents `10, 12, □`. The
exercise's last visible term supplies the trace's initial position (28 or 12).
No expected answer or grading rule is introduced.

Native radio controls choose 1, 2, 5, or 10, initially 1. Large labeled native
buttons jump left/right using mouse, touch, Enter, or Space. Choosing a size
does not move or enter the trace. Every accepted jump stays visible as a numbered
arrowed arc and an ordered textual description of its origin, destination,
direction, and size. Reversals, repeated paths, and negative positions remain
part of the history. The displayed range includes visible terms and all visited
positions; it expands rather than clipping movement to a correctness-based range.
Only signed 32-bit arithmetic bounds reject movements, preserving v1 replay limits.

The diagram has a named image and a focusable, labeled horizontally scrollable
region on small screens. A live status announces the current position; the full
history provides text equivalents without relying on color or drawing coordinates.
No jump rows or arcs are collapsed or discarded during an attempt.

The pure reducer emits existing v1 `jumpLeft`/`jumpRight` events with positive
`value`, consecutive `step`, and supplied UTC `occurredAt`. Drawing and history
derive from that trace. `Listo` appends exactly one terminal `finishAttempt`,
freezes the snapshot deeply, invokes the local completion callback once under
React StrictMode, and disables later movements. Completion at any position uses
only **Terminaste**. Focus moves to **Empezar de nuevo**. Repeated Enter/Space
keydown events cannot activate reset after completion, and double-clicking
`Listo` preserves the reset focus. Finish and reset use separate stable buttons.

**Empezar de nuevo** also clears unfinished attempts, restores the initial
position and size 1, and restarts step numbering. Changing examples remounts the
screen using its exercise ID; changing activity or reloading discards the attempt.
The example selector warns that switching examples clears jumps. The screen's
`onComplete(ActionTracePayload)` and `onReset()` callbacks are local integration
seams for FARO-008, with no new public wire contract or backend changes.

No API, persistence, browser storage, telemetry, audio, transcripts, provider
integration, or dependencies are introduced. Test evidence is synthetic and
available only in the existing non-production browser harness. The complete
attempt flow and deterministic sequence evaluation remain separate backlog work.

## Reproduce

Use Node 24, npm 12.2.0, JDK 27, and Gradle 9.8.1:

```bash
npm --prefix apps/web ci --ignore-scripts
npm --prefix apps/web run test:install-browser
npm --prefix apps/web run test:unit
npm --prefix apps/web run test:e2e
npm --prefix apps/web run build
gradle -p services/api --no-daemon --no-build-cache --rerun-tasks test
node .agents/scripts/pr-size-preflight.mjs
git diff --check
```

Dependency lifecycle scripts remain disabled. Browser installation is explicit;
an existing locked-version Chromium installation may be reused. Screenshots,
video, and traces remain disabled in automated suites. Local visual checks use
synthetic examples only and keep captures outside the repository.

## Results — 2026-10-22

- Unit suite: **35 passed** (19 existing plus 16 sequence tests), covering both
  directions and all sizes, shared-fixture preservation, replay-derived history,
  corrections, negative positions, 32-bit bounds, timestamp-order independence,
  immutable completion, and reset before/after completion.
- Browser suite: **36 passed** (24 block regressions plus 12 sequence tests).
  Sequence coverage includes production navigation, both examples, size changes,
  exact callback evidence under StrictMode, single completion, double-clicks,
  held Enter/Space, neutral outcomes, mouse/keyboard/touch operation, no API or
  browser-storage writes, and reset on switching/reload. At 320/360/700/1100px,
  controls fit, touch targets are at least 48px high, and eight repeated/negative
  jumps retain their complete history and remain inside the diagram bounds.
- Typecheck (application, contract, unit, and browser projects) and production
  build: **passed** with Node 24.21.0 and the existing npm 12.2.0 CLI.
- API regression suite: **382 passed**, zero failures/errors/skips, with JDK 27
  and the existing Gradle 9.8.1 distribution. All five build tasks executed with
  `--no-build-cache --rerun-tasks`; results were not reused from the test cache.
- Visual review: desktop 1100px and mobile 360px, with three synthetic jumps in
  both directions. Labels, arcs, controls, and history fit; document widths equal
  their viewport widths, and neither page reported runtime errors. Captures stayed
  in `/tmp`. Physical devices, screen-reader software, Firefox, and WebKit were
  not tested; automated browser coverage uses Chromium.
- PR preflight: **13/40 changed files**, passed against `origin/main`.
  `git diff --check`: **passed**. No manifests, lockfiles, or CI configuration changed.

The first browser run caught held-Enter reset and double-click focus regressions;
both were corrected before the final complete suite passed. Sandbox restrictions
prevented starting the initial browser server, invoking Git from Node, and loading
Gradle's native library. Those checks subsequently passed outside the sandbox;
no repository checks were weakened or skipped.
