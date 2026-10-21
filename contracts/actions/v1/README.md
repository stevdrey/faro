# Semantic action contract, version 1

FARO-003 / [issue #14](https://github.com/stevdrey/faro/issues/14) defines semantic interaction events and deterministic replay. It introduces no HTTP endpoint, persistence, UI, or assessment rules.

## Payload

| Field | JSON type | Meaning |
|---|---|---|
| `schemaVersion` | integer | Exactly `1`. Independent of the exercise contract version. |
| `initialValue` | integer | Explicit starting quantity or number-line position, supplied by the caller. |
| `events` | array | Semantic events in received order; may be empty. |

Each event has a `kind` discriminator, a positive integer `step`, and an `occurredAt` timestamp string. Movement events additionally require `value`, a positive integer magnitude. `finishAttempt` does not accept `value`, including `null` or zero.

| `kind` | Meaning during replay |
|---|---|
| `placeBlock` | Add `value` to the current quantity. Represents units, such as 1 or 10, without identifying individual pieces. |
| `removeBlock` | Subtract `value`; reject if more is removed than is currently available. |
| `jumpLeft` | Subtract `value` from the current position; negative positions are valid. |
| `jumpRight` | Add `value` to the current position. |
| `finishAttempt` | Mark completion without changing the current value. |

All documented fields are required and non-null. Unknown fields and event kinds are rejected. JSON integers use Java's signed 32-bit range (`-2147483648` through `2147483647`); strings, booleans, and fractional numeric tokens (including `1.0`) are not coerced into integers. Movement magnitudes and steps must also be greater than zero. There are no coordinates, block identifiers, exercise answers, session identifiers, audio, or transcripts in this payload.

`occurredAt` must be an ISO 8601 UTC timestamp ending in uppercase `Z`, for example `2026-01-01T00:00:00Z` or `2026-01-01T00:00:00.123456789Z`. The shape is exactly `YYYY-MM-DDTHH:MM:SS[.fraction]Z` with a four-digit year, uppercase `T`, seconds, and one to nine fraction digits. Epoch numbers, dates without times, zone-free strings, numeric timezone offsets, impossible dates, hour `24`, and leap second `60` are rejected. Wire timestamps are strings; the adapter converts them to domain `Instant` values and stores the canonical `Instant.toString()` form, so equivalent spellings such as `.100Z` and `.1Z` decode to equal payloads.

## Ordering and replay

Steps must be exactly `1, 2, 3, ...` in the received array. Duplicate steps, gaps, and unsorted arrays are rejected; the trace is never automatically sorted. Timestamps are evidence only: equal or decreasing timestamps do not affect validity or replay order.

`finishAttempt` is optional, unique, and must be the last event. A trace without it is incomplete, including an empty trace. Finishing without any movements is valid. Completion records the explicit action; it does not imply mathematical correctness.

`ActionReplay.replay(initialValue, trace)` returns an immutable `Result(finalValue, completed)`. It uses no exercise definition, expected answer, wall clock, randomness, or external service. The same initial value and trace always produce the same result. The caller chooses the baseline: for example, 0 for building an unknown addend, 28 for the next step after the visible sequence `30, 28`, or 53 for removing quantities from `53 − 28`.

All intermediate arithmetic stays within the signed 32-bit range. Overflow throws `ArithmeticException`; removing more blocks than are available throws `IllegalArgumentException`. Replay returns no partial result on failure and never mutates its input. Even a trace ending in `finishAttempt` can fail replay. Payload decoding validates event structure, ordering, and finalization; arithmetic validity is checked by replay.

Events operate on one scalar value. This contract does not enforce which event kinds a particular exercise family or visual model may emit, identify individual blocks, implement regrouping, or grade exploration. Those policies belong to later integrations and mathematical rules.

## Shared fixtures

| Fixture | Initial value | Final value | Completed |
|---|---|---|---|
| [blocks.json](fixtures/blocks.json) | 0 | 6 | yes |
| [number-line.json](fixtures/number-line.json) | 28 | 26 | yes |
| [subtraction.json](fixtures/subtraction.json) | 53 | 25 | yes |
| [incomplete.json](fixtures/incomplete.json) | 0 | 1 | no |

All fixtures are synthetic. The block example includes exploration (place 10, remove 4); the number-line example includes right/left moves, equal timestamps, and a clock adjustment. The subtraction example removes 20 and then 8.

## Boundaries, versioning, and verification

The Java `sessions` domain contains immutable `ActionEvent` variants, `ActionTrace`, and `ActionReplay`; it imports only JDK classes. `sessions.api.ActionTracePayload` owns the versioned DTO variants and explicit domain conversion. Wire names use `kind` values, never implementation class names.

Incoming JSON must be read through `ActionTracePayloadJson`. Its strict mapper rejects unknown fields, duplicate JSON keys, trailing JSON values, missing/null fields, unsupported versions, and scalar coercions without changing the application-wide mapper. A root JSON `null` is also rejected. A future endpoint must explicitly use this codec or an equivalent converter and add HTTP binding tests; a plain `@RequestBody ActionTracePayload` would bypass codec strictness.

The web exports readonly types from `apps/web/src/contracts/action.ts`. TypeScript checks event shape and discriminator narrowing, but does not enforce integer ranges, positive magnitudes, timestamps, ordering, or finalization at runtime. These types alone do not validate untrusted JSON. Fixture type checks cover envelope/event fields and primitive shapes; backend tests verify literal event kinds, version values, and replay behavior.

Version 1 accepts only the documented shape. Changes to fields, event kinds, or their interpretation require a new version and explicit migration. No released action-contract consumers or migrations exist yet. This change adds no dependencies and leaves the exercise contract unchanged.

```bash
gradle -p services/api --no-daemon test
npm --prefix apps/web run typecheck
npm --prefix apps/web run build
```

Use Gradle 9.8.1, JDK 27, Node 24, and npm 12.2.0 as specified by the repository. Java tests cover replay, ordering, validity, immutability, serialization, and strict decoding; the web checks run through the existing `typecheck` and `build` scripts and CI.
