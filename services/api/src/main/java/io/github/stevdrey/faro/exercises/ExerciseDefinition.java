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
        Objects.requireNonNull(exerciseId, "exerciseId");
        if (exerciseId.isBlank()) {
            throw new IllegalArgumentException("exerciseId must not be blank");
        }
        Objects.requireNonNull(operands, "operands");
        Objects.requireNonNull(visualModel, "visualModel");
        Objects.requireNonNull(difficulty, "difficulty");
    }

    public ExerciseType type() {
        return operands.type();
    }
}
