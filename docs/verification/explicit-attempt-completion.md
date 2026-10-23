# FARO-008 explicit attempt completion verification

Issue: [#25](https://github.com/stevdrey/faro/issues/25).

## Delivered behavior

One explicit **Listo** activation completes any current arrangement, including
zero moves. Existing activity reducers append one terminal `finishAttempt` and
deeply freeze their traces. The application now retains the original trace in
one frozen `CompletedAttempt` with its `exerciseId`, replacing the previous empty
completion/reset callbacks. Blocks and sequences keep action schema v1;
subtraction keeps v2, including exchanges. No event, timestamp or value is
rewritten, and no mathematical result is inferred.

The shared controller accepts completion only when the last event is
`finishAttempt`. Activity reducers remain responsible for trace construction,
ordering and deep immutability; this internal boundary does not decode untrusted
JSON. Repeated deliveries cannot replace the first snapshot. Callbacks associated
with a different exercise cannot complete or reset the selected exercise.

Restarting or selecting another activity/example clears the snapshot. Selecting
the active activity/example preserves it. Returning to a previous activity starts
with fresh actions; only the chosen sequence example is remembered during the
open page. Reloading or closing the page discards all state. There is no attempt
history in production.

Existing neutral **Terminaste** feedback, movement locks, focus handling,
double-click guards and held-key protections are preserved. The application
adds no grading, API request, browser storage, telemetry, audio, provider
integration, public wire contract or dependency. This remains a local demo.

## Reproduce

Use the repository's Node 24/npm 12.2.0 setup and already provisioned Chromium:

```bash
npm --prefix apps/web run test:unit
npm --prefix apps/web run test:e2e
npm --prefix apps/web run build
node .agents/scripts/pr-size-preflight.mjs
git diff --check
```

For a fresh setup, first use `npm --prefix apps/web ci --ignore-scripts` and the
explicit `test:install-browser` command documented in the root README. Browser
tests start Vite on loopback and require permission to launch Chromium. No API
server or real child data is needed. Screenshots, video and traces stay disabled.

## Results — 2026-10-22

- **61 unit tests passed**, including 10 new controller tests covering exercise
  association, exact trace retention, both schema versions, immutable envelopes,
  idempotent delivery, reset/selection lifecycle and incomplete/foreign callbacks.
- **73 Chromium browser tests passed**, including 21 new controller integration
  tests under React StrictMode. Coverage includes zero moves and corrections in
  every activity, blocks at 0/6/7/10, frozen snapshots, blocked later moves,
  repeated delivery, activity/example selection, reload, double clicks, Enter,
  Space, focus and touch at 360px. Existing mouse-drag, cancellation and layout
  regressions also pass.
- **Typecheck and production build passed.** Test observation and synthetic
  snapshot history live only in the browser harness, outside the production
  entry point.
- **PR size preflight: 11/40 changed files, passed.** Whitespace check passed.

Local verification used Node 24.21.0 and the existing npm 11.19.0 executable for
`npm run` only; no dependency installation or lockfile resolution was performed.
CI retains its pinned npm 12.2.0 installation and supply-chain checks. No
dependency or API source changed, so dependency audits and Java tests were not
repeated locally for this issue.

The first sandboxed browser run could not start its web server, and the sandboxed
preflight failed with `spawnSync git EPERM`. Both completed successfully with
the required execution permissions. Touch coverage is Chromium emulation;
physical devices, Firefox and WebKit were not tested. Snapshots are transient
and unavailable after restart/navigation/reload; persistence and backend attempt
submission remain future work.
