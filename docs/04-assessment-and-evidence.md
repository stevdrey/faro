# Conservative assessment and evidence

**Golden rule:** not enough evidence does **not** mean the child does not understand.

## Independent state axes

| Axis | Canonical values |
|---|---|
| Action mathematical correctness | `CORRECTA`, `INCORRECTA`, `SIN_TERMINAR` |
| Explanation evidence | `COHERENTE`, `ERROR_ARITMETICO_IDENTIFICABLE`, `INSUFICIENTE`, `CONFLICTO_ASR`, `NO_EVALUABLE` |
| Child-facing next step | `CONTINUAR`, `PISTA`, `PEDIR_ACLARACION`, `REPETIR_AUDIO` |

The canonical enumerations above retain their original Spanish identifiers for cross-document compatibility; English code-facing enum names can be adopted through a separately versioned contract decision. Adult output must **not** say the child 'does not understand'.

FARO-004 implements these values in the provider-independent Java `AssessmentResult` and the [version 1 assessment contract](../contracts/assessments/v1/README.md), with strict JSON binding, readonly TypeScript types, and six shared synthetic fixtures. All three axes remain independent; the contract preserves supplied states rather than inferring them. Required `ruleIds` (possibly empty) and nonblank `assessmentVersion` provide minimal provenance. FARO-021 adds a separate domain trace and rule catalog; the v1 wire contract remains unchanged.

FARO-019 extracts bounded literal explanation evidence. FARO-020 adds standalone conservative aggregation for missing-addend visual counts and a separate check of confirmed subtraction equations; see [verification](verification/evidence-aggregation.md). FARO-021 traces those aggregation decisions with minimal input categories; see [traceability verification](verification/assessment-traceability.md). The complete processing pipeline, feedback selection, attempt correlation, confirmation and retries remain future integration.

## Inputs and evidence processing

`assess(exercise, actionTrace, speechHypothesis?, clarifications[]) -> AssessmentResult` includes versioned `ruleIds`; the separate aggregation trace uses input categories, which are not part of the version 1 result. The deterministic math engine checks outcomes and steps; an `ActionInterpreter` interprets semantic moves; a bounded `ExplanationInterpreter` extracts only supported phrases and numbers; an `EvidenceAggregator` resolves uncertainty; a `FeedbackPolicy` chooses a bounded response.

## Safety constraints

1. Do not coerce the ASR text into the expected result or silently rewrite it.
2. Correct actions conflicting with speech → flag possible ASR conflict / insufficiency, **not** an incorrect mathematical action.
3. Correct result with vague explanation → mark explanation insufficient, without inventing proof of a strategy.
4. Recognize a specific arithmetic error only when the step is stated unambiguously.
5. Empty or faulty ASR → not evaluable and retry/continue options.
6. Bound retries and clarifications; allow the adult to review rather than trap the child in an audio loop.
7. Record minimal rule IDs, timestamps, and versions for reproducible review; log neither raw audio nor sensitive transcript text.

## Mandatory regression examples

- `18 = 12 + □` with six blocks and 'twelve plus six is eighteen': coherent when clear.
- Six blocks with ASR 'seven': conflict and visual six-or-seven disambiguation.
- Six blocks with 'because': math correct, strategy evidence insufficient.
- `53 − 28`: separating twenty then eight may be strategically sound even if a final numeric claim says 26 instead of 25.
- `30, 28, □` → 26 via steps of −2.
- Permission denied, blank audio, provider offline, and repeated clarification requests must never become academic penalties.

**No model or LLM is authorized to issue the final pedagogical decision.**
