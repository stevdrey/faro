# Assessment traceability (FARO-021)

The standalone aggregator exposes `missingAddendTraced`,
`missingAddendHypothesisTraced`, and `confirmedSubtractionStepsTraced` alongside
its existing methods. Each returns the existing state(s) and an immutable
`AssessmentTrace`, version `faro-aggregation-1`. Rules are ordered by execution:
the action rule first, the evidence rule second, and, when attempted, the
standalone-number rule last. The latter can override the preliminary `no-claims`
result with a conflict. IDs below are stable; input references are enum categories,
not identifiers or values. The rule itself is the structured reason. The source
catalog exposes the corresponding input categories for each rule.

| Rule ID | Reason |
|---|---|
| `action.unfinished.v1` | No visual answer was supplied. |
| `action.correct.v1` / `action.incorrect.v1` | The visual answer passed / failed the missing-addend check. |
| `speech.unavailable.v1` | Speech was unavailable or the hypothesis contained no letters or digits. |
| `evidence.visual-unfinished.v1` | Available speech cannot be compared to an unfinished visual answer. |
| `evidence.no-claims.v1` | Available speech produced no extracted claims. |
| `evidence.added-conflict.v1` | The sum of explicit added claims differs from the visual answer. |
| `evidence.coherent-account.v1` | A correct answer and complete ordered literal account match the operands. |
| `evidence.other-account.v1` | Extracted claims neither establish that account nor an added-quantity conflict. |
| `evidence.standalone-conflict.v1` | A recognized standalone number differs from the visual answer. |
| `evidence.standalone-no-conflict.v1` | The standalone-number check did not establish a conflict, whether a matching number or no number was recognized. |
| `subtraction.incomplete-or-invalid.v1` | Confirmed steps do not form a complete valid connected decomposition. |
| `subtraction.arithmetic-error.v1` | A valid decomposition contains a false confirmed equation. |
| `subtraction.coherent.v1` | A valid decomposition has only true confirmed equations. |

The trace does not retain audio, transcript, numbers, offsets, personal data,
freeform text, or individual observation identifiers. It explains which rule
applied and which input categories mattered, but cannot by itself replay an
attempt or identify an individual source record. A future authorized review
flow must correlate protected evidence and versioned decisions separately, define
retention/deletion and access controls, and add traceable rules for its actual
clarification and hint policy. No such policy, adult interface, persistence,
logging, endpoint or correlation exists here. Confirmed subtraction steps remain
subject to the caller's independent confirmation requirement. The v1 assessment
wire contract remains unchanged, and its opaque `ruleIds` must not be treated as
a complete trace or a chosen next action.

For verification with JDK 27 and the repository's Gradle configuration:

```bash
gradle -p services/api --offline --no-daemon test --tests '*AssessmentTraceTests' --tests '*EvidenceAggregatorTests'
gradle -p services/api --offline --no-daemon test
node .agents/scripts/pr-size-preflight.mjs
```

Synthetic tests cover rule branches, unfinished and unavailable evidence,
standalone hypotheses, connected and disconnected subtraction, determinism,
immutability and text exclusion. They do not establish adult review, real ASR
accuracy, child usability, or pilot readiness.
