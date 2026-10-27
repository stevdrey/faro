# Missing-addend mathematics (FARO-015)

`MissingAddendMath.isCorrect` checks a supplied integer answer against the known
operands using `knownAddend + answer = total`. The same rule applies to all
three v1 presentation layouts, including both blank positions. It uses a
64-bit sum so signed 32-bit operands cannot wrap into a false match. The rule
accepts the full integer domain of the existing exercise contract; it does not
impose a new 0–100 restriction or rely on the stored `expectedResult`.

Tests enumerate every total, known addend, and answer from 0 through 100,
including correct and incorrect answers, and cover signed and overflow edges.
Run `gradle -p services/api --no-daemon test` with the repository's JDK and
Gradle versions. This pure check does not interpret action traces, assess an
unfinished attempt or explanation, issue feedback, expose an endpoint, or
process speech or private data. Those integrations remain separate work.
