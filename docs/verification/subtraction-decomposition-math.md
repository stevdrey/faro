# Subtraction by decomposition (FARO-017)

`SubtractionDecompositionMath.check` checks a list of explicitly stated
`minuend - part = result` steps. It returns a strategy status and an ordered,
immutable list of independent arithmetic checks. A valid strategy has at least
two positive parts summing to the subtrahend, starts at the exercise minuend,
and links each subsequent minuend to the preceding **declared** result. For
example, `53 - 20 = 33; 33 - 8 = 26` has a valid strategy and an arithmetic
error in the second step. A linked arithmetic slip is not silently corrected.

A nonpositive part or a total exceeding the subtrahend is invalid. Incomplete,
single-step, or disconnected evidence is insufficient. A zero subtrahend always
returns insufficient strategy evidence; each supplied equation is still checked.
Arithmetic and part totals use `long`, avoiding signed 32-bit wraparound. Null
inputs and null steps are rejected. The exercise operands and public v1 payload
retain their existing signed integer contract.

Tests cover alternative positive decompositions, final and intermediate slips,
invalid and insufficient evidence, disconnected steps, integer boundaries,
nulls and immutable results. Run `gradle -p services/api --no-daemon test` with
JDK 27. This function does not interpret speech or action traces, assess a child,
provide feedback, add an endpoint or retain personal data.

The local backend suite passed 575 tests with JDK 27 and Gradle 9.8.2 in offline
mode. Gradle 9.8.2 was the installed version; CI uses the pinned 9.8.1.
