// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import java.util.List;

/** Known inputs only. A sequence contains visible terms, never its missing next term. */
public sealed interface ExerciseOperands {
    record MissingAddend(int total, int knownAddend) implements ExerciseOperands {}

    record Sequence(List<Integer> terms) implements ExerciseOperands {
        public Sequence {
            terms = List.copyOf(terms);
            if (terms.size() < 2) {
                throw new IllegalArgumentException("A sequence needs at least two visible terms");
            }
        }
    }

    record Subtraction(int minuend, int subtrahend) implements ExerciseOperands {}

    default ExerciseType type() {
        return switch (this) {
            case MissingAddend ignored -> ExerciseType.MISSING_ADDEND;
            case Sequence ignored -> ExerciseType.SEQUENCE;
            case Subtraction ignored -> ExerciseType.SUBTRACTION;
        };
    }
}
