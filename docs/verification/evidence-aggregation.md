# Conservative evidence aggregation (FARO-020)

`EvidenceAggregator` is a standalone, pure domain function. For a missing-addend
exercise it checks the supplied completed visual count with `MissingAddendMath`;
a null count means unfinished. The visual state is independent of speech. An
unavailable recognition result, or a hypothesis without any letter or digit
(blank, `.` or `...`), is `NO_EVALUABLE`; an available but unrecognized or vague
hypothesis is `INSUFICIENTE`. The caller must supply extracted evidence from the
same attempt and correctly distinguish unavailable speech from a valid but empty
extraction.

When the total of the explicit `ADDED` claims differs from the visual count, the
result is `CONFLICTO_ASR`, including when other claims agree; it does not identify
which source is wrong. Adding in steps (`puse cuatro, y luego puse dos` for six)
is therefore not a conflict, while `puse seis, puse siete` is. With
`missingAddendHypothesis`, the supplied `ExplanationInterpreter` may also report a
complete standalone number hypothesis (`siete`, `Siete.`), which conflicts in the
same way. Other action quantities are not compared to the answer. A coherent
account requires exactly the ordered `HAD` known addend, `ADDED` visual answer and
`REMAINED` total, and a correct visual answer. Other patterns, including
multi-step additions, and incompatible accounts remain insufficient unless the
added total conflicts. An unfinished attempt has no visual count to compare, so it
is always insufficient. This deliberately narrow rule does not infer intent or
accept every reasonable explanation.

Separately, `confirmedSubtractionSteps` validates explicit, independently
confirmed equations through `SubtractionDecompositionMath`. Only a complete,
connected valid decomposition with a false equation produces
`ERROR_ARITMETICO_IDENTIFICABLE`; incomplete, disconnected or invalid chains
remain insufficient. The caller must establish confirmation before invoking this
method: FARO-019 extracts action/quantity claims, not equations, and an ASR
hypothesis alone is not confirmation. This function does not assess a visual
subtraction attempt or correlate it with speech. No user-facing flow currently
calls either function. FARO-021 adds a separate [domain trace and rule catalog](assessment-traceability.md).
Feedback, clarification, confirmation and attempt correlation remain separate work. No transcript or
audio is retained or logged by this aggregator.

Verification with JDK 27 and Gradle:

```bash
gradle -p services/api --offline --no-daemon test --tests '*EvidenceAggregatorTests'
gradle -p services/api --offline --no-daemon test
```

Tests use synthetic statements and equations; they do not establish ASR accuracy,
child usability, or pilot readiness. No recordings or personal pilot evidence
are required.
