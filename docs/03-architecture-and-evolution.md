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

`ExerciseDefinition(exerciseId, operands, visualModel, difficulty, expectedResult)` is backend-only; the family is derived from typed operands. `ExercisePayload.from(definition)` produces the public versioned contract with `exerciseId`, `schemaVersion`, `type`, `operands`, `visualModel`, and `difficulty`. The **backend retains expectedResult**, never exposing solutions in child-facing exercise payloads. See the [version 1 exercise contract and JSON fixtures](../contracts/exercises/v1/README.md).

`ActionEvent(kind, value, step, occurredAt)` and `Attempt(id, exerciseId, sessionId, actionTrace, completionState)`. Replaying the same semantic trace must yield the same interpretation.

`SpeechHypothesis(transcript, languageTag, engineId, modelId, modelVersion, latencyMs, status, segments)` is only what an engine thinks was said. Confidence scores must not be trusted without calibration.

`AssessmentResult(actionMathState, strategyEvidenceState, nextAction, ruleIds, assessmentVersion)` separates observable mathematical correctness from strategic explanation evidence.

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
