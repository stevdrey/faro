# MVP backlog: epics and issues

**Source:** integral kickoff document dated 2026-10-16. Every FARO identifier is a stable planning identifier; GitHub issue numbers are assigned separately.

Priority meanings: **P0** blocker / critical-path work; **P1** needed for the pilot; **P2** beyond MVP 0.1. Every issue should yield one small reviewable PR with acceptance evidence. Do not duplicate the optional `ARCH-01…06` suggestions: their work is included in FARO issues.

## Epic A — Foundations and domain contracts

| ID | Priority | Work | Depends on |
|---|---|---|---|
| FARO-001 | P0 | Initialize minimal monorepo, launch scripts, health endpoint | — |
| FARO-002 | P0 | Define exercise contract and JSON fixtures | 001 |
| FARO-003 | P0 | Semantic action event contract and deterministic replay | 002 |
| FARO-004 | P0 | Math/evidence/feedback assessment states | 002, 003 |
| FARO-031 | P0 | Replaceable ASR port, canonical data objects, and fake | 001 |

## Epic B — Visual interaction without speech

| ID | Priority | Work | Depends on |
|---|---|---|---|
| FARO-005 | P0 | Missing-addend block interface | 002 |
| FARO-006 | P1 | Number line for sequences | 003 |
| FARO-007 | P1 | Tens/ones model for subtraction | 003 |
| FARO-008 | P0 | Explicit finish-attempt flow | 005, 003 |
| FARO-009 | P1 | Short, replayable spoken instructions | 005 |

## Epic C — Local speech recognition

| ID | Priority | Work | Depends on |
|---|---|---|---|
| FARO-010 | P0 | Launch whisper.cpp baseline locally | 001 |
| FARO-011 | P0 | Push-to-talk browser recording | 001 |
| FARO-012 | P0 | Browser audio normalization → ASR integration | 010, 011, **031** (mandatory prerequisite) |
| FARO-013 | P1 | Transcription metadata without default audio retention | 012 |
| FARO-014 | P1 | Comparative local ASR benchmark | 013, 024 |

## Epic D — Mathematical engine and evidence

| ID | Priority | Work | Depends on |
|---|---|---|---|
| FARO-015 | P0 | Deterministic missing-addend solver | 002 |
| FARO-016 | P1 | Deterministic sequences | 002 |
| FARO-017 | P1 | Validate subtraction by decomposition | 002 |
| FARO-018 | P0 | Spanish transcribed number normalizer | 012 |
| FARO-019 | P0 | Bounded spoken-explanation pattern interpreter | 018 |
| FARO-020 | P0 | Conservative evidence aggregator | 004, 015, 019 |
| FARO-021 | P1 | Traceable rule IDs and decision versions | 020 |

## Epic E — Accessible, safe feedback

| ID | Priority | Work | Depends on |
|---|---|---|---|
| FARO-022 | P0 | Spoken/visual ambiguity clarification | 009, 020 |
| FARO-023 | P1 | Brief non-punitive feedback policy | 020 |
| FARO-024 | P1 | Non-penalizing speech retries and graceful fallback | 012, 022 |

## Epic F — Pilot, telemetry, privacy

| ID | Priority | Work | Depends on |
|---|---|---|---|
| FARO-025 | P0 | Minimal pseudonymous session storage and deletion | 003, 013 |
| FARO-026 | P1 | Protected separate adult view | 021, 025 |
| FARO-027 | P1 | Separate human transcript/assessment annotations | 026 |
| FARO-028 | P1 | Pilot metrics with denominators | 027 |
| FARO-029 | P1 | Consent and before/after protocol | 002 |
| FARO-030 | P1 | Evidence-based MVP decision report | 014, 028, 029 |

## Epic G — After the MVP (non-blocking)

| ID | Priority | Work | Depends on |
|---|---|---|---|
| FARO-032 | P2 | Optional local LLM shadow assessment | 027 |
| FARO-033 | P2 | Second visual context for a stable concept | 020 |
| FARO-034 | P2 | Phaser adventure prototype reusing math engine | 030 |
| FARO-035 | P2 | Invited-family alpha and per-session cost study | 030 |

## Epic H — Model independence and benchmark expansion

| ID | Priority | Work | Depends on |
|---|---|---|---|
| FARO-036 | P1 | Moonshine Spanish Tiny Streaming ASR adapter | 031, 012 |
| FARO-037 | P1 | Vosk small Spanish ASR adapter | 031, 012 |
| FARO-038 | P1 | Auditable multi-ASR A/B runner and report | 014, 027, and **036 OR 037** |
| FARO-039 | P2 | SDK-independent optional LLM interpreter port | 019, 027 |
| FARO-040 | P0 | `LOCAL_ONLY` enforcement and configuration tests | 031 |
| FARO-041 | P1 | Model/weight/license provenance inventory | **036 OR 037** |

## Recommended implementation order

- ASR safety: `001 → 031 → 040 → 010 / 011 → 012 → 013`, then benchmarking.
- Visual: `001 → 002 → 003 → 005 → 008` and `002 → 015`.
- Assessment: `004 + 015 + 018 → 019 → 020 → 022`.
- Adult audit and protocol before widening the pilot; optional LLM, cloud, and Phaser should **not** block the initial scope.

### Regression cases that every implementation must preserve

1. Child adds 6, says 6 clearly → correct action + coherent strategy if evidence supports it.
2. Child adds 6, ASR says 7 → `CONFLICTO_ASR`, never a child error.
3. Child adds 6, says 'because' → correct action + insufficient strategy evidence.
4. `53−28` decomposed `53−20=33`, then incorrect `33−8=26` → distinguish math step from decomposition strategy.
5. Sequence `30,28,□` with 26 and explanation of decrement by 2 → correct.
6. Microphone permission denied → visual activity remains available; no academic penalty.
7. Empty/unavailable ASR → non-evaluable, can retry/cancel.
8. Too many clarifications → allow session end without penalty.
9. Human transcript revision → preserve original ASR hypothesis and append annotation.
10. Correct result by guesswork → do not claim verified comprehension.

**Excluded from the initial backlog:** billing, hosted student accounts, diagnostic claims, freeform LLM-generated exercises, automatic child audio training, and fully animated gameplay.
