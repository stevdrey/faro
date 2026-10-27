// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import java.util.Objects;

/** Pure arithmetic for a completed missing-addend answer, independent of presentation. */
public final class MissingAddendMath {
    private MissingAddendMath() {}

    /** Tests knownAddend + answer = total without 32-bit overflow. */
    public static boolean isCorrect(ExerciseOperands.MissingAddend operands, int answer) {
        Objects.requireNonNull(operands, "operands");
        return (long) operands.knownAddend() + answer == operands.total();
    }
}
