# FARO-005 missing-addend screen verification

Issue: [#15](https://github.com/stevdrey/faro/issues/15).

## Delivered behavior

The shared `missing-addend-known-first.json` fixture supplies `12 + □ = 18`.
Twelve fixed blocks remain visible; ten movable units are shared between the
reserve and answer. Mouse/touch dragging and labeled +/− buttons support 0–10
answer blocks and corrections. Keyboard users can operate the same buttons.
Canceled gestures, lost pointer capture, Escape, short taps, and outside drops do
not produce semantic movements. Accepted actions produce consecutive, UTC-stamped
`placeBlock`/`removeBlock` events with unit magnitude and an initial value of zero.

The equation, drop-area styling, and neutral status never change based on
correctness. `Listo` accepts every quantity, appends one `finishAttempt`, delivers
a deeply frozen trace to the container, and disables further movement. Its only
confirmation is `Terminaste`. The finish/reset control ignores the extra clicks
of a multi-click within 400 ms of a transition, so double-clicking either `Listo`
or `Intentar de nuevo` performs one action, while later taps still work.
`Intentar de nuevo` clears the component and container state and starts
a new trace. Reload also starts empty. The component honors all three existing
missing-addend equation presentations.

Holding Enter or Space on either finish/reset control cannot trigger the next
action after focus moves: repeated activation keydowns are suppressed, and a fresh
press after releasing the key still works. The +/− buttons use `aria-disabled`
at 0/10 and after completion, so a keyboard user keeps focus at either limit.
The equation and both drop areas are labeled groups. The one-column layout applies through
700px so both fixed-size block grids fit before switching to two columns.

No API request, grading, browser storage, audio, transcript, or telemetry is
introduced. The production build contains only the application entry point;
the synthetic test harness lives under `tests/e2e` and is not a production entry.
Full attempt integration and spoken instructions remain FARO-008 and FARO-009.

## Reproduce

Use Node 24 and npm 12.2.0, then run:

```bash
npm --prefix apps/web ci --ignore-scripts
npm --prefix apps/web run test:install-browser
npm --prefix apps/web test
npm --prefix apps/web run build
npm --prefix apps/web audit signatures
npm --prefix apps/web audit --audit-level=high
gradle -p services/api --no-daemon test
node .agents/scripts/pr-size-preflight.mjs
```

Browser provisioning is an explicit, reviewed command; lifecycle scripts stay
disabled. `test:install-browser` installs Chromium and OS libraries; on systems
with the libraries already installed, the local Playwright CLI may instead run
`install chromium`. The browser suite starts Vite on loopback and needs no API.
It uses Chromium; the touch test dispatches browser-level touch input through
Chromium's protocol. Real hardware and Firefox/WebKit remain unverified.

## Results — 2026-10-22

- Unit: **19 tests passed**: the pure reducer (every quantity, corrections, ignored
  boundary operations, immutable snapshots, unique terminal events, reset, and
  timestamp ordering independence) and fixture narrowing (all three shared
  missing-addend fixtures narrow unchanged; invalid fields are rejected).
- Browser: **24 tests passed**, covering the real entry point, mouse drag/return,
  outside drops and cancellation, keyboard/tap controls, neutral completion at
  0/6/7/10, double-click protection, callback delivery under React StrictMode,
  frozen traces, reset, all equation layouts, mobile touch, and storage/network
  boundaries. The additional regressions cover held Enter/Space across both
  finish/reset transitions and horizontal fit at 320/360/640/641/695/700/701/768/1100px
  with zero and ten blocks, including the completion controls. The second review
  round added double-click on reset, rapid taps after the guard window, keyboard
  focus at both quantity limits, and accessible group names.
- Typecheck and production build: **passed**, including the existing contract
  checks, browser test/harness checks, and the new `tsconfig.unit.json` check of
  unit tests (Node types are scoped to that project only).
- Dependency signatures: **29 verified**, with **17 verified attestations**.
  Vulnerability audit: **0 vulnerabilities**.
- Clean lockfile installation with `ci --ignore-scripts`: **passed**.
- PR size preflight: **20/40 changed files**, passed; whitespace check passed.
- API verification with JDK 27 and checksum-verified Gradle 9.8.1: **passed** on
  2026-10-21; not re-run for the second review round, which changes no API source
  or dependency.
- Visual inspection (2026-10-21, not repeated for the second round): desktop 1100px and mobile 360px checked with synthetic data;
  no clipping or overlapping controls. A 320px viewport also fit without horizontal
  overflow; no browser page errors were observed.

Playwright 1.64.1 was published on 2026-10-14, exceeding the three-day intake
minimum when installed. The only new package chain is `@playwright/test` →
`playwright` → `playwright-core`, all exactly 1.64.1, registry-integrity pinned,
Apache-2.0 licensed, with no package lifecycle scripts. Browser binaries and visual
review captures are local artifacts and are not committed. CI installs the browser
explicitly and runs both web suites; screenshot/video/trace capture stays off by
default.

`@types/node` 24.19.2 (published 2026-10-09, matching the Node 24 runtime) was
added as an exact-pinned devDependency for unit-test type checking. Its only
dependency is `undici-types` 7.24.6 (published 2026-03-25). Both are MIT licensed,
registry-integrity pinned, have no lifecycle scripts, and were installed with
npm 12.2.0 and `--ignore-scripts`.

## PR #65 review regressions

Before changing the implementation, the new held-Enter regression failed because
six answer blocks became zero, and the 641px layout regression failed because
the document width was 664px. Both tests pass after the fixes, along with the full
web suite and typecheck/build. Visual inspection at 641px and 701px also passed
on either side of the new breakpoint. The corrections touch only the screen, its existing
stylesheet/browser tests, and this verification document; no dependencies,
contracts, or API code changed. The PR remains at 17 changed files.

## PR #65 second review round

A high-effort review reported ten findings. New regressions failed first:
double-clicking `Intentar de nuevo` finished a new empty attempt; a third rapid
tap after the guard window was swallowed by the old click-count guard; focus fell
to the document when +/− reached a limit; and the equation/drop-area labels had
no role. All pass after the fixes. The other changes are structural: the shared
fixture is narrowed instead of overridden, unit tests are type checked, dragging
moves the preview without re-rendering the screen, and dead state, a redundant
focus call and duplicated cancel logic were removed. The PR is at 20 changed files.
