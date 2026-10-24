# Initial architecture and evolution

**Status:** reference proposal. The implementation starts with a modular monolith and clear boundaries, not microservices or an all-in-one LLM.

```text
Child web app (TypeScript / Vite) ──────┐
Adult review (separate protected view) ├──> Spring Boot modular monolith
                                      │       ├─ exercises: deterministic MathEngine
                                      │       ├─ sessions: semantic ActionEvent traces
                                      │       ├─ speech: SpeechRecognizer port
                                      │       ├─ assessment: rule-based evidence
                                      │       ├─ feedback: brief interventions
                                      │       └─ pilot: local adult audit and metrics
                                      └──────────> local storage via StoragePort
                                                  speech ──> local whisper.cpp process
                                                        └─> future Moonshine / Vosk
```

## Bounded responsibilities

- `exercises` describes configured deterministic mathematical tasks. It knows neither ASR nor UI coordinates.
- `sessions` retains pseudonymous attempts and ordered semantic events (e.g., ADD_BLOCK, JUMP, REMOVE_TENS) rather than raw sprite positions.
- `speech` owns transient normalized audio, ASR status, timing, and the model provenance metadata; a transcription is **unconfirmed**.
- `assessment` consumes math facts, actions, and extracted verbal evidence without importing runtime or vendor SDKs.
- `feedback` translates states into a short, predictable child-facing action; it does not invent unsupported claims.
- `pilot` offers private adult review, distinct human annotations, aggregate statistics, and deletion.

## Stable conceptual contracts

`ExerciseDefinition(exerciseId, operands, visualModel, difficulty, expectedResult)` is backend-only; the family is derived from typed operands. `ExercisePayload.from(definition)` produces the public versioned contract with `exerciseId`, `schemaVersion`, `type`, `operands`, `visualModel`, and `difficulty`. Missing-addend payloads additionally carry an explicit `presentation` layout; the projection overload accepts the selected layout without changing the mathematical definition. The **backend retains expectedResult**, never exposing solutions in child-facing exercise payloads. See the [version 1 exercise contract and JSON fixtures](../contracts/exercises/v1/README.md).

The v1 action contract exposes five immutable domain variants: `PlaceBlock`, `RemoveBlock`, `JumpLeft`, `JumpRight`, and `FinishAttempt`. Every event carries `step` and `occurredAt`; movements also carry a positive `value` magnitude. `ActionTrace` preserves received order with consecutive steps from 1 and an optional terminal finish. `ActionReplay.replay(initialValue, trace)` deterministically returns `Result(finalValue, completed)` without grading or deriving the starting value from an exercise. Timestamps do not determine order. The [version 1 action contract and shared fixtures](../contracts/actions/v1/README.md) define strict JSON binding and matching web types separately from the pure Java domain.

`Attempt(id, exerciseId, sessionId, actionTrace, completionState)` remains a future concept; sessions, persistence, and attempt endpoints are not implemented by the action contract.

FARO-008 implements a narrower browser-local completion snapshot, extended by
FARO-013 to `CompletedAttempt(attemptId, exerciseId, trace)`.
A shared completion controller retains the deeply frozen trace supplied by the
active screen's reducer after an explicit `Listo` activation. One
`activity-selection` mapping chooses the shown exercise, and each screen's
callback names that `exerciseId`; the controller rejects any that differs from
the selected one. It accepts only a terminal `finishAttempt`, keeps the first delivery, and clears it on restart or
exercise selection changes. Re-selecting the current exercise is a no-op.
Blocks/sequences retain v1 and subtraction retains v2, without conversion. This
is one transient snapshot, with no history, session identity, endpoint, durable storage,
assessment or provider dependency. Reloading/unmounting forgets it. FARO-013 reads
only its random browser-local attempt identifier to attach a separate voice
record; neither the identifier nor the trace is sent to the backend. Sending the
completed trace remains future work.

FARO-007 adds a separate sealed `ExchangeEvent` hierarchy (`DecomposeTen`,
`ComposeTen`) through the opt-in [v2 action contract](../contracts/actions/v2/README.md).
`ActionEvent` keeps its five original variants; both extend `TraceEvent`, and an
`ExtendedActionTrace` holds either kind with the same ordering rules as
`ActionTrace`. V1 conversion and the scalar `ActionReplay` accept only
`ActionTrace`, so exchanges cannot reach them at compile time. `TensAndOnesReplay` reconstructs
available and removed tens/units, validates source pieces and quantity-preserving
exchanges, and never solves or grades an exercise. The local subtraction screen
emits v2; existing block/sequence screens still emit v1. No attempt endpoint or
storage is introduced.

`SpeechRecognizer` and its immutable `SpeechRequest`, `SpeechHypothesis(transcript, languageTag, engineId, modelId, modelVersion, latencyMs, status)`, capabilities and provider configuration are implemented in `speech`. A hypothesis is only what an engine thinks was said; it contains no segments or confidence scores. The fake returns empty hypotheses and is selected only by the `dev` profile or tests; configuration requires an explicit provider and selects explicitly registered adapter factories without fallback. FARO-012 adds a default-disabled synthetic-only whisper.cpp integration. FARO-040 now enforces local provider/endpoint admission and rechecks local capabilities before each transcription. The preview remains synthetic-only. See [ASR contracts and provider selection](05-provider-independence.md).

`AssessmentResult(actionMathState, strategyEvidenceState, nextAction, ruleIds, assessmentVersion)` is an immutable, provider-independent result with three independent state axes and minimal provenance. `assessment.api.AssessmentResultPayload` adds `schemaVersion: 1`; its strict codec and matching readonly web types are documented in the [version 1 assessment contract and shared fixtures](../contracts/assessments/v1/README.md). Empty rule lists are valid; the rule catalog, evidence references, evaluator, and feedback policy remain future work. Constructing or decoding a result does not choose states or enforce cross-axis policy.

FARO-011 adds a browser-local optional explanation after completion. A page-lifetime
adult confirmation enables the controls; only explicit recording activation
requests microphone permission. A provider-independent web capture controller
owns transient `RecordedClip(blob, mimeType, durationMs)` data in the browser's
native encoding. The panel lifetime associates that clip with the completed
attempt without extending `CompletedAttempt` or the semantic trace contracts.
Stop finalizes one clip; cancel, restart, context changes, hidden/leaving pages
and unmount discard it and release hardware, listeners, timers and playback URLs.
An application-scoped audio coordinator serializes guidance/clip playback and
excludes playback throughout microphone permission, capture and finalization.
Ordinary capture adds no audio transport, persistence or ASR. FARO-012 separately
provides an opt-in synthetic preview: Web Audio normalization → bounded PCM API →
`SpeechRecognizer` → local whisper.cpp WAV/multipart adapter. Only an explicit
transcription activation sends a clip. Results remain transient, unconfirmed and
outside assessment or the completed snapshot. The preview uses the FARO-040 admission gate merged in #73 and remains
synthetic-only; the policy is separate from consent and pilot authorization. See
[capture verification](verification/browser-voice-capture.md) and
[integration verification](verification/browser-asr-integration.md).

FARO-013 adds `CompletionState.speechRecord` as one immutable browser-memory
observation, outside `CompletedAttempt` and the semantic action contracts.
`stage: capture` records ready media duration or a stable capture error, including
`permission` and `empty`. `stage: asr` records either a validated hypothesis or a
stable transcription error. Recording duration and normalized PCM duration are
distinct; engine/model/revision/effective language and latency come only from
valid ASR responses. No audio, URL or raw exception is copied into this record.
There is no history or server store. Updates and clears must name the active
`attemptId`; capture/transcription generations reject obsolete work within that
attempt. New recording/transcription replaces the observation, and existing
cancel/delete/context/hidden/unmount boundaries discard it without changing the
mathematical snapshot. Durable storage is deferred to FARO-025. See
[safe speech metadata verification](verification/safe-speech-metadata.md).

## Request flow (planned)

1. API provides exercise presentation **without** the expected result.
2. Child moves visual items; browser tracks semantic actions without grading exploration.
3. Child confirms completion; browser sends a frozen action trace to backend.
4. If child intentionally starts and stops recording, audio is sent to a local, separately running ASR adapter.
5. Backend validates deterministic math and aggregates rule-based evidence. Contradiction/weak audio triggers clarification, not an academic error.
6. Adult can review pseudonymous evidence and independently annotate the original transcript without overwriting it.

Illustrative API routes (not implemented): `POST /api/v1/sessions`, `GET /api/v1/exercises/{id}`, `POST /api/v1/sessions/{id}/attempts`, `POST /api/v1/attempts/{id}/explanation`, and `POST /api/v1/attempts/{id}/clarifications`. `/api/v1/adult/reviews` requires authentication and authorization before network exposure.

## Evolution without premature infrastructure

- Now: one backend build, plain web components, local-only ASR process, SQLite or JSONL when needed, CI without real audio.
- Later: a Phaser visual client may reuse math/domain APIs; PostgreSQL may replace a storage adapter; another ASR may replace whisper.cpp; a local or hosted LLM may **propose structured evidence in shadow mode only**.
- Do not add Kubernetes, microservices, game engine dependencies, cloud accounts, or required LLM inference to the MVP without measurements.

**Boundary test direction:** `assessment` must not import a whisper.cpp class; `exercises` must not import speech; game clients must not know hidden answers. Spring Modulith verification is optional and can be introduced when packages have meaningful content.

## PR boundaries

Prefer one functional issue per PR, with acceptance tests. Split PRs that combine UI, privacy, ASR algorithms, and pedagogical policy. Tests must not embed children’s recordings or real identifiers.
