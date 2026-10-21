// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import java.util.List;
import java.util.Objects;

/** Known inputs only. A sequence contains visible terms, never its missing next term. */
public sealed interface ExerciseOperands {
    record MissingAddend(int total, int knownAddend) implements ExerciseOperands {}

    record Sequence(List<Integer> terms) implements ExerciseOperands {
        public static final int MAX_TERMS = 20;

        public Sequence {
            Objects.requireNonNull(terms, "terms");
            if (terms.size() < 2 || terms.size() > MAX_TERMS) {
                throw new IllegalArgumentException(
                        "A sequence needs between 2 and " + MAX_TERMS + " visible terms");
            }
            for (Integer term : terms) {
                Objects.requireNonNull(term, "terms must not contain null");
            }
            terms = List.copyOf(terms);
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
