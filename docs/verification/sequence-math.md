# Arithmetic sequence mathematics (FARO-016)

`SequenceMath` infers the fixed difference from the first two visible terms,
checks every remaining difference and computes the next term. It accepts any
integer step, including zero and negative steps. Arithmetic uses `long` to avoid
32-bit wraparound. `solution` rejects an irregular sequence or a next term
outside the signed 32-bit contract; `isCorrect` never throws for such a
sequence and returns `false` for every answer, since none is correct.
Backend `ExerciseDefinition` rejects irregular sequences and an `expectedResult`
that differs from the inferred answer. Two visible terms determine the assumed
arithmetic progression; they do not prove that another pattern was intended.

The public v1 payload remains a structural contract and retains its existing
two-to-twenty term and null checks. Mathematical validation happens when solving
or constructing a backend definition, not when parsing a public payload.
Tests cover ±1, ±2, ±5 and ±10, zero and another step, irregular definitions,
wrong answers, matching results and integer boundaries. The contract tests
check the shared `sequence` fixture's documented answer (`30, 28 → 26`) with
`SequenceMath.isCorrect`. Run
`gradle -p services/api --no-daemon test` with JDK 27.

This pure domain function adds no endpoint, UI grading, assessment, speech,
storage or provider integration.
