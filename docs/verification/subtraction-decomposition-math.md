# Subtraction by decomposition (FARO-017)

`SubtractionDecompositionMath.check` checks a list of explicitly stated
`minuend - part = result` steps. It returns a strategy status and an ordered,
immutable list of independent arithmetic checks. A valid strategy has at least
two positive parts summing to the subtrahend, starts at the exercise minuend,
and links each subsequent minuend to the preceding **declared** result. For
example, `53 - 20 = 33; 33 - 8 = 26` has a valid strategy and an arithmetic
error in the second step. A linked arithmetic slip is not silently corrected.

Within a connected chain, a nonpositive part or a running total exceeding the
subtrahend is invalid. Incomplete, single-step, or disconnected evidence is
insufficient; disconnection takes precedence, so an unrelated step never makes
an otherwise connected attempt invalid. A zero subtrahend always returns
insufficient strategy evidence; each supplied equation is still checked. At
most `MAX_STEPS` (20) steps are accepted. Arithmetic and part totals use
`long`, avoiding signed 32-bit wraparound. Null inputs and null steps are
rejected.

The domain now matches the web client's operand rule: subtraction operands must
be non-negative with `subtrahend <= minuend`. No 0–99 limit is imposed. The
public v1 payload (`ExercisePayload.SubtractionOperands`) is unchanged and still
accepts the full signed 32-bit range.

Tests cover alternative positive decompositions, final and intermediate slips,
invalid and insufficient evidence, disconnected steps with invalid parts,
integer boundaries, rejected operands, the step limit, nulls and immutable
results. Run `gradle -p services/api --no-daemon test` with JDK 27. This
function does not interpret speech or action traces, assess a child, provide
feedback, add an endpoint or retain personal data.

The local backend suite passed 577 tests with JDK 27 and Gradle 9.8.2 in offline
mode. Gradle 9.8.2 was the installed version; CI uses the pinned 9.8.1.
