// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.stevdrey.faro.exercises.Difficulty;
import io.github.stevdrey.faro.exercises.ExerciseDefinition;
import io.github.stevdrey.faro.exercises.ExerciseOperands;
import io.github.stevdrey.faro.exercises.ExerciseType;
import io.github.stevdrey.faro.exercises.VisualModel;
import java.util.Objects;

/** Version 1 child-facing contract. Deliberately has no expected-result component. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ExercisePayload.MissingAddend.class, name = "MISSING_ADDEND"),
    @JsonSubTypes.Type(value = ExercisePayload.Sequence.class, name = "SEQUENCE"),
    @JsonSubTypes.Type(value = ExercisePayload.Subtraction.class, name = "SUBTRACTION")
})
public sealed interface ExercisePayload {
    int SCHEMA_VERSION = 1;

    String exerciseId();
    int schemaVersion();
    ExerciseOperands operands();
    VisualModel visualModel();
    Difficulty difficulty();

    default ExerciseType type() {
        return operands().type();
    }

    static ExercisePayload from(ExerciseDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return switch (definition.operands()) {
            case ExerciseOperands.MissingAddend operands -> new MissingAddend(
                    definition.exerciseId(), SCHEMA_VERSION, operands,
                    definition.visualModel(), definition.difficulty());
            case ExerciseOperands.Sequence operands -> new Sequence(
                    definition.exerciseId(), SCHEMA_VERSION, operands,
                    definition.visualModel(), definition.difficulty());
            case ExerciseOperands.Subtraction operands -> new Subtraction(
                    definition.exerciseId(), SCHEMA_VERSION, operands,
                    definition.visualModel(), definition.difficulty());
        };
    }

    record MissingAddend(
            String exerciseId, int schemaVersion, ExerciseOperands.MissingAddend operands,
            VisualModel visualModel, Difficulty difficulty) implements ExercisePayload {
        public MissingAddend {
            validate(exerciseId, schemaVersion, operands, visualModel, difficulty);
        }
    }

    record Sequence(
            String exerciseId, int schemaVersion, ExerciseOperands.Sequence operands,
            VisualModel visualModel, Difficulty difficulty) implements ExercisePayload {
        public Sequence {
            validate(exerciseId, schemaVersion, operands, visualModel, difficulty);
        }
    }

    record Subtraction(
            String exerciseId, int schemaVersion, ExerciseOperands.Subtraction operands,
            VisualModel visualModel, Difficulty difficulty) implements ExercisePayload {
        public Subtraction {
            validate(exerciseId, schemaVersion, operands, visualModel, difficulty);
        }
    }

    private static void validate(
            String exerciseId, int schemaVersion, ExerciseOperands operands,
            VisualModel visualModel, Difficulty difficulty) {
        Objects.requireNonNull(exerciseId, "exerciseId");
        if (exerciseId.isBlank()) {
            throw new IllegalArgumentException("exerciseId must not be blank");
        }
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported exercise schema version");
        }
        Objects.requireNonNull(operands, "operands");
        Objects.requireNonNull(visualModel, "visualModel");
        Objects.requireNonNull(difficulty, "difficulty");
    }
}
