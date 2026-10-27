// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import java.util.List;
import java.util.Objects;

/** Pure arithmetic for the next term of a fixed-difference sequence. */
public final class SequenceMath {
    private SequenceMath() {}

    /** Rejects non-arithmetic sequences and next terms outside the integer contract. */
    public static int solution(ExerciseOperands.Sequence operands) {
        var terms = Objects.requireNonNull(operands, "operands").terms();
        if (!hasFixedDifference(terms)) {
            throw new IllegalArgumentException("Sequence terms must have a fixed difference");
        }
        long next = nextTerm(terms);
        if (next < Integer.MIN_VALUE || next > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Sequence solution must fit in a signed 32-bit integer");
        }
        return (int) next;
    }

    /** Tests answer against the next term; a sequence without an integer solution accepts no answer. */
    public static boolean isCorrect(ExerciseOperands.Sequence operands, int answer) {
        var terms = Objects.requireNonNull(operands, "operands").terms();
        return hasFixedDifference(terms) && nextTerm(terms) == answer;
    }

    private static boolean hasFixedDifference(List<Integer> terms) {
        long difference = (long) terms.get(1) - terms.get(0);
        for (int i = 2; i < terms.size(); i++) {
            if ((long) terms.get(i) - terms.get(i - 1) != difference) {
                return false;
            }
        }
        return true;
    }

    /** Computed in long so neither the difference nor the next term wraps around. */
    private static long nextTerm(List<Integer> terms) {
        return terms.get(terms.size() - 1) + ((long) terms.get(1) - terms.get(0));
    }
}
