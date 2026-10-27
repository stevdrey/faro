// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import java.util.List;
import java.util.Objects;

/** Known inputs only. A sequence contains visible terms, never its missing next term. */
public sealed interface ExerciseOperands {
    /** Non-negative counts with knownAddend <= total, matching the web client's rule. */
    record MissingAddend(int total, int knownAddend) implements ExerciseOperands {
        public MissingAddend {
            if (total < 0 || knownAddend < 0 || knownAddend > total) {
                throw new IllegalArgumentException(
                        "A missing addend needs non-negative operands with knownAddend <= total");
            }
        }

        /** The single answer that completes knownAddend + answer = total; cannot overflow. */
        public int solution() {
            return total - knownAddend;
        }
    }

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

    /** Non-negative counts with subtrahend <= minuend, matching the web client's rule. */
    record Subtraction(int minuend, int subtrahend) implements ExerciseOperands {
        public Subtraction {
            if (minuend < 0 || subtrahend < 0 || subtrahend > minuend) {
                throw new IllegalArgumentException(
                        "A subtraction needs non-negative operands with subtrahend <= minuend");
            }
        }
    }

    default ExerciseType type() {
        return switch (this) {
            case MissingAddend ignored -> ExerciseType.MISSING_ADDEND;
            case Sequence ignored -> ExerciseType.SEQUENCE;
            case Subtraction ignored -> ExerciseType.SUBTRACTION;
        };
    }
}
