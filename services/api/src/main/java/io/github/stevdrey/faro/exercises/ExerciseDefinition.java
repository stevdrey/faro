// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import java.util.Objects;

/** Backend-only definition. Transport adapters must explicitly project its public fields. */
public record ExerciseDefinition(
        String exerciseId,
        ExerciseOperands operands,
        VisualModel visualModel,
        Difficulty difficulty,
        int expectedResult) {

    public ExerciseDefinition {
        ExerciseIds.requireValid(exerciseId);
        Objects.requireNonNull(operands, "operands");
        Objects.requireNonNull(visualModel, "visualModel");
        Objects.requireNonNull(difficulty, "difficulty");
        switch (operands) {
            case ExerciseOperands.MissingAddend missingAddend -> {
                if (expectedResult != missingAddend.solution()) {
                    throw new IllegalArgumentException("expectedResult must match the missing-addend solution");
                }
            }
            case ExerciseOperands.Sequence sequence -> {
                if (expectedResult != SequenceMath.solution(sequence)) {
                    throw new IllegalArgumentException("expectedResult must match the sequence solution");
                }
            }
            // No subtraction solver exists yet; validating its expectedResult is separate work.
            case ExerciseOperands.Subtraction ignored -> {}
        }
    }

    public ExerciseType type() {
        return operands.type();
    }
}
