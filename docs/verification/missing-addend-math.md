# Missing-addend mathematics (FARO-015)

`MissingAddendMath.isCorrect` checks a supplied integer answer against
`knownAddend + answer = total` by comparing it with
`ExerciseOperands.MissingAddend.solution()` (`total - knownAddend`). The same
rule applies to all three v1 presentation layouts, including both blank
positions.

The domain now matches the web client's operand rule: missing-addend operands
must be non-negative with `knownAddend <= total`, so the single solution is a
non-negative count and the subtraction cannot overflow. `ExerciseDefinition`
rejects a missing-addend `expectedResult` that differs from `solution()`, so the
check and the stored result cannot disagree. No 0–100 limit is imposed. The
wire v1 contract (`ExercisePayload.MissingAddendOperands`) is unchanged and
still accepts the full signed 32-bit range.

Tests cover every valid total and known addend from 0 through 100 and assert
that exactly one answer in 0–100, `total - known`, is accepted. They also cover
the `Integer.MAX_VALUE` edges, negative answers, rejected operands and
mismatched `expectedResult` values. The contract tests check the documented
answer (`18 = 12 + 6`) against each of the three shared missing-addend layout
fixtures.

Run `gradle -p services/api --no-daemon test` with JDK 27 and Gradle 9.8.1. The
local run for this change used Gradle 9.8.2 because 9.8.1 was not installed;
CI runs the pinned 9.8.1. This pure check does not interpret action traces,
assess an unfinished attempt or explanation, issue feedback, expose an endpoint,
or process speech or private data. Those integrations remain separate work.
