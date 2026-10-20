// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.stevdrey.faro.exercises.Difficulty;
import io.github.stevdrey.faro.exercises.ExerciseDefinition;
import io.github.stevdrey.faro.exercises.ExerciseIds;
import io.github.stevdrey.faro.exercises.ExerciseOperands;
import io.github.stevdrey.faro.exercises.ExerciseType;
import io.github.stevdrey.faro.exercises.VisualModel;
import java.util.List;
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
    ExerciseType type();
    Operands operands();
    VisualModel visualModel();
    Difficulty difficulty();

    static ExercisePayload from(ExerciseDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        String id = definition.exerciseId();
        VisualModel visualModel = definition.visualModel();
        Difficulty difficulty = definition.difficulty();
        return switch (definition.operands()) {
            case ExerciseOperands.MissingAddend o -> new MissingAddend(
                    id, SCHEMA_VERSION, MissingAddendOperands.from(o), visualModel, difficulty);
            case ExerciseOperands.Sequence o -> new Sequence(
                    id, SCHEMA_VERSION, SequenceOperands.from(o), visualModel, difficulty);
            case ExerciseOperands.Subtraction o -> new Subtraction(
                    id, SCHEMA_VERSION, SubtractionOperands.from(o), visualModel, difficulty);
        };
    }

    /** Wire operand shapes, decoupled from domain record names so v1 JSON changes only deliberately. */
    sealed interface Operands {}

    record MissingAddendOperands(int total, int knownAddend) implements Operands {
        static MissingAddendOperands from(ExerciseOperands.MissingAddend operands) {
            return new MissingAddendOperands(operands.total(), operands.knownAddend());
        }
    }

    record SequenceOperands(List<Integer> terms) implements Operands {
        public SequenceOperands {
            // Reuse the domain's term-count and null rules.
            terms = new ExerciseOperands.Sequence(terms).terms();
        }

        static SequenceOperands from(ExerciseOperands.Sequence operands) {
            return new SequenceOperands(operands.terms());
        }
    }

    record SubtractionOperands(int minuend, int subtrahend) implements Operands {
        static SubtractionOperands from(ExerciseOperands.Subtraction operands) {
            return new SubtractionOperands(operands.minuend(), operands.subtrahend());
        }
    }

    record MissingAddend(
            String exerciseId, int schemaVersion, MissingAddendOperands operands,
            VisualModel visualModel, Difficulty difficulty) implements ExercisePayload {
        public MissingAddend {
            validate(exerciseId, schemaVersion, operands, visualModel, difficulty);
        }

        @Override
        public ExerciseType type() {
            return ExerciseType.MISSING_ADDEND;
        }
    }

    record Sequence(
            String exerciseId, int schemaVersion, SequenceOperands operands,
            VisualModel visualModel, Difficulty difficulty) implements ExercisePayload {
        public Sequence {
            validate(exerciseId, schemaVersion, operands, visualModel, difficulty);
        }

        @Override
        public ExerciseType type() {
            return ExerciseType.SEQUENCE;
        }
    }

    record Subtraction(
            String exerciseId, int schemaVersion, SubtractionOperands operands,
            VisualModel visualModel, Difficulty difficulty) implements ExercisePayload {
        public Subtraction {
            validate(exerciseId, schemaVersion, operands, visualModel, difficulty);
        }

        @Override
        public ExerciseType type() {
            return ExerciseType.SUBTRACTION;
        }
    }

    private static void validate(
            String exerciseId, int schemaVersion, Operands operands,
            VisualModel visualModel, Difficulty difficulty) {
        ExerciseIds.requireValid(exerciseId);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported exercise schema version");
        }
        Objects.requireNonNull(operands, "operands");
        Objects.requireNonNull(visualModel, "visualModel");
        Objects.requireNonNull(difficulty, "difficulty");
    }
}
