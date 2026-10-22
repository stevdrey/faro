# FARO-007 subtraction tens-and-ones verification

Issue: [#24](https://github.com/stevdrey/faro/issues/24).

## Delivered behavior

The activity picker adds **Restas**, preserving **Bloques** as the initial screen.
The screen uses the unchanged exercise fixture `subtraction-53-28` and renders
`53 − 28 = □` throughout. **Quedan** starts at five tens and three units;
**Quitaste** starts empty. Each tray displays its actual counts and total. A ten
is a segmented bar of ten cells. The trusted demo fixture guard supports
nonnegative subtraction operands up to 99 with subtrahend no larger than minuend,
bounding DOM size without constraining the signed 32-bit replay contract.

Mouse/touch drags and labeled buttons transfer one ten or unit between trays.
Buttons support Enter and Space; repeated keydown events represent one action.
Escape, pointer cancellation/capture loss, and drops outside the target cancel a
drag without adding evidence. Manual exchange buttons change one available ten
into ten available units, or the reverse. No automatic borrowing, normalization,
target-based restriction, prescribed order or grading is implemented. Removed
pieces cannot participate in an exchange until returned. All 53 can be removed.

After removing two tens, the screen shows 33 available; exchanging a ten shows
two tens and thirteen units, still 33; removing eight units shows 25 available
and 28 removed. These are observable quantities produced by the child's actions,
not a hidden solution or assessment. The equation remains blank.

**Listo** appends one terminal event and delivers one deeply frozen v2 trace
under StrictMode. All movement/exchange controls then reject further actions.
The neutral message is always **Terminaste**, with focus on **Empezar de nuevo**.
Reset also clears unfinished attempts and restarts step numbering; switching
activities or reloading discards all evidence. Separate stable finish/reset
buttons avoid held-key and double-click transitions into a new attempt.

The [v2 contract](../../contracts/actions/v2/README.md) records explicit
`decomposeTen`/`composeTen` events and preserves the existing five event shapes.
Java `TensAndOnesReplay` and the web replay reconstruct all four counts from the
same synthetic fixture. The web reducer shares its transition rules with replay.
V1 rejects exchanges, v2 envelopes and lossy downconversion. Scalar replay directs
exchange traces to the specific piece replay. No HTTP endpoint, persistence,
storage, logs, telemetry, audio, transcript, provider or dependencies are added.
Full attempt integration and mathematical/strategy assessment remain later work.

## Reproduce

Use Node 24, npm 12.2.0, JDK 27 and Gradle 9.8.1. Dependencies and Chromium may
reuse an existing locked installation. Installation scripts remain disabled.

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

## Results — 2026-10-22

- Web unit suite: **51 passed**, including all 40 earlier tests. Coverage includes
  shared-fixture prefixes, the 20-then-8 route, both exchanges, corrections,
  exhaustion, conservation across 500 deterministic exploratory operations,
  immutable snapshots, resets, timestamp ordering and invalid replay inputs.
- Browser suite: **52 passed**, including all 38 existing regressions. New checks
  cover exact v2 callback evidence, one completion, neutral incomplete/complete
  outcomes, mouse/touch dragging both piece kinds in both directions, cancelled
  gestures, buttons, keyboard repeats, exchanges, reset, switching/reload, and
  absence of API requests or browser-storage writes. At 320/360/700/1100 px all
  53 decomposed units and controls fit without page overflow; interactive targets
  are at least 48 px high.
- Typecheck (application, contract, unit and browser sources) and production
  build: **passed**, using Node 24.21.0 and the installed npm 12.2.0 CLI for build.
- Java suite: **470 passed**, zero failures/errors/skips, with JDK 27 and Gradle
  9.8.1, all five tasks executed with `--no-build-cache --rerun-tasks`. Includes
  strict v2 JSON checks using the v1 malformed-input matrix, legacy events in v2,
  exchange metadata, rejection of lossy v1 conversion, piece replay, corrections,
  conservation, quantity bounds and immutable evidence.
- Visual review: synthetic intermediate state (33 available, 20 removed, after
  exchanging a ten) inspected at 1100 and 360 px. Trays, segmented bars, counts,
  exchange controls and footer remain readable, with no page overflow or browser
  runtime errors. Captures remain outside the repository in `/tmp`.
- PR-size preflight: **28/40 changed files**, passed against `origin/main`, below
  the warning range. `git diff --check`: **passed**. Dependency manifests,
  lockfiles, install-script controls and CI configuration remain unchanged.

The first Java test run used the PATH Gradle 9.8.0 and exposed a test expectation
that incorrectly anticipated `.1Z` instead of Java's canonical `.100Z`. The
expectation was fixed and the full suite rerun successfully with required Gradle
9.8.1. The first build caught an unnecessary Node filesystem import in the browser
test; it now imports the shared JSON fixture directly and the build passes.
Sandbox restrictions prevented the initial browser-server start and Git preflight;
browser and Java checks subsequently ran outside the sandbox without weakening
repository checks. Physical devices, screen-reader software, Firefox and WebKit
are not tested; browser automation uses Chromium and synthetic data only.
