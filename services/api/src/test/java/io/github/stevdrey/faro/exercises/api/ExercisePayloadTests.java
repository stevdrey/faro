// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stevdrey.faro.exercises.Difficulty;
import io.github.stevdrey.faro.exercises.ExerciseDefinition;
import io.github.stevdrey.faro.exercises.ExerciseOperands;
import io.github.stevdrey.faro.exercises.ExerciseType;
import io.github.stevdrey.faro.exercises.VisualModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Uses the application's mapper, including its strict JSON settings. */
@SpringBootTest
class ExercisePayloadTests {
    @Autowired
    private ObjectMapper mapper;

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("missing-addend", ExercisePayload.MissingAddend.class,
                        ExerciseType.MISSING_ADDEND, new ExerciseOperands.MissingAddend(18, 12), 6),
                Arguments.of("sequence", ExercisePayload.Sequence.class,
                        ExerciseType.SEQUENCE, new ExerciseOperands.Sequence(List.of(30, 28)), 26),
                Arguments.of("subtraction", ExercisePayload.Subtraction.class,
                        ExerciseType.SUBTRACTION, new ExerciseOperands.Subtraction(53, 28), 25));
    }

    @ParameterizedTest(name = "{0}: JSON round trip")
    @MethodSource("fixtures")
    void roundTripsPublicFixtures(
            String fixture, Class<? extends ExercisePayload> variant,
            ExerciseType type, ExerciseOperands operands, int expectedResult) throws IOException {
        JsonNode json = fixture(fixture);
        ExercisePayload payload = mapper.treeToValue(json, ExercisePayload.class);

        assertThat(payload).isInstanceOf(variant);
        assertThat(payload.type()).isEqualTo(type);
        assertThat(payload.operands()).isEqualTo(operands);
        assertThat(payload.schemaVersion()).isEqualTo(1);
        assertThat(mapper.readTree(mapper.writeValueAsString(payload))).isEqualTo(json);
    }

    @ParameterizedTest(name = "{0}: backend projection hides solution")
    @MethodSource("fixtures")
    void projectsOnlyPublicFields(
            String fixture, Class<? extends ExercisePayload> variant,
            ExerciseType type, ExerciseOperands operands, int expectedResult) throws IOException {
        JsonNode json = fixture(fixture);
        ExerciseDefinition definition = new ExerciseDefinition(
                json.get("exerciseId").asString(), operands,
                VisualModel.valueOf(json.get("visualModel").asString()),
                Difficulty.valueOf(json.get("difficulty").asString()), expectedResult);

        ExercisePayload payload = ExercisePayload.from(definition);
        JsonNode serialized = mapper.readTree(mapper.writeValueAsString(payload));

        assertThat(definition.expectedResult()).isEqualTo(expectedResult);
        assertThat(definition.type()).isEqualTo(type);
        assertThat(payload).isInstanceOf(variant);
        assertThat(serialized).isEqualTo(json);
        assertThat(serialized.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder(
                        "exerciseId", "schemaVersion", "type", "operands", "visualModel", "difficulty");
        assertThat(serialized.findValues("expectedResult")).isEmpty();
    }

    static Stream<Arguments> invalidPayloads() {
        Stream<Arguments> missingFields = Stream.of(
                "exerciseId", "schemaVersion", "type", "operands", "visualModel", "difficulty")
                .map(field -> invalid("missing " + field, root -> root.remove(field)));
        Stream<Arguments> nullFields = Stream.of(
                "exerciseId", "schemaVersion", "type", "operands", "visualModel", "difficulty")
                .map(field -> invalid("null " + field, root -> root.putNull(field)));
        Stream<Arguments> malformed = Stream.of(
                invalid("blank id", root -> root.put("exerciseId", " \t")),
                invalid("numeric id", root -> root.put("exerciseId", 42)),
                invalid("fractional id", root -> root.put("exerciseId", 4.2)),
                invalid("boolean id", root -> root.put("exerciseId", true)),
                invalid("future version", root -> root.put("schemaVersion", 2)),
                invalid("zero version", root -> root.put("schemaVersion", 0)),
                invalid("string version", root -> root.put("schemaVersion", "1")),
                invalid("fractional version", root -> root.put("schemaVersion", 1.5)),
                invalid("unknown type", root -> root.put("type", "MULTIPLICATION")),
                invalid("unknown visual model", root -> root.put("visualModel", "SPRITES")),
                invalid("unknown difficulty", root -> root.put("difficulty", "EXPERT")),
                invalid("numeric enum", root -> root.put("difficulty", 0)),
                invalid("unexpected property", root -> root.put("extra", "ignored?")),
                invalid("public solution", root -> root.put("expectedResult", 6)),
                invalid("missing total", root -> operands(root).remove("total")),
                invalid("missing addend", root -> operands(root).remove("knownAddend")),
                invalid("null total", root -> operands(root).putNull("total")),
                invalid("string total", root -> operands(root).put("total", "18")),
                invalid("fractional total", root -> operands(root).put("total", 18.5)),
                invalid("boolean total", root -> operands(root).put("total", true)),
                invalid("out-of-range total", root -> operands(root).put("total", 2147483648L)),
                invalid("hidden operand", root -> operands(root).put("expectedResult", 6)),
                invalid("mismatched family", root -> root.put("type", "SUBTRACTION")),
                invalid("missing minuend", root -> {
                    root.put("type", "SUBTRACTION");
                    root.putObject("operands").put("subtrahend", 28);
                }),
                invalid("missing subtrahend", root -> {
                    root.put("type", "SUBTRACTION");
                    root.putObject("operands").put("minuend", 53);
                }),
                invalid("missing terms", root -> {
                    root.put("type", "SEQUENCE");
                    root.putObject("operands");
                }),
                invalid("null terms", root -> {
                    root.put("type", "SEQUENCE");
                    root.putObject("operands").putNull("terms");
                }),
                invalid("empty sequence", root -> {
                    root.put("type", "SEQUENCE");
                    root.putObject("operands").putArray("terms");
                }),
                invalid("short sequence", root -> {
                    root.put("type", "SEQUENCE");
                    root.putObject("operands").putArray("terms").add(30);
                }),
                invalid("null term", root -> {
                    root.put("type", "SEQUENCE");
                    root.putObject("operands").putArray("terms").add(30).addNull();
                }),
                invalid("fractional term", root -> {
                    root.put("type", "SEQUENCE");
                    root.putObject("operands").putArray("terms").add(30).add(28.5);
                }),
                invalid("string term", root -> {
                    root.put("type", "SEQUENCE");
                    root.putObject("operands").putArray("terms").add(30).add("28");
                }));
        return Stream.concat(Stream.concat(missingFields, nullFields), malformed);
    }

    @ParameterizedTest(name = "Reject {0}")
    @MethodSource("invalidPayloads")
    void rejectsInvalidPayloads(String name, Consumer<ObjectNode> mutate) throws IOException {
        ObjectNode json = (ObjectNode) fixture("missing-addend");
        mutate.accept(json);

        assertThatThrownBy(() -> mapper.readValue(mapper.writeValueAsString(json), ExercisePayload.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void sequenceOperandsAreImmutable() {
        List<Integer> source = new ArrayList<>(List.of(30, 28));
        ExerciseOperands.Sequence operands = new ExerciseOperands.Sequence(source);
        source.set(1, 99);

        assertThat(operands.terms()).containsExactly(30, 28);
        assertThatThrownBy(() -> operands.terms().add(26)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructorsValidateStructureWithoutSolvingTheExercise() {
        ExerciseOperands operands = new ExerciseOperands.MissingAddend(0, -1);
        ExerciseDefinition definition = new ExerciseDefinition(
                "synthetic", operands, VisualModel.BLOCKS, Difficulty.HARD, 99);

        assertThat(ExercisePayload.from(definition).operands()).isEqualTo(operands);
        assertThatThrownBy(() -> new ExerciseDefinition(" ", operands, VisualModel.BLOCKS, Difficulty.EASY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExerciseDefinition("id", null, VisualModel.BLOCKS, Difficulty.EASY, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExerciseOperands.Sequence(List.of(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExercisePayload.MissingAddend(
                "id", 2, new ExerciseOperands.MissingAddend(18, 12), VisualModel.BLOCKS, Difficulty.EASY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JsonNode fixture(String name) throws IOException {
        try (var resource = getClass().getResourceAsStream("/exercises/v1/fixtures/" + name + ".json")) {
            assertThat(resource).as("shared fixture %s", name).isNotNull();
            return mapper.readTree(resource);
        }
    }

    private static ObjectNode operands(ObjectNode root) {
        return (ObjectNode) root.get("operands");
    }

    private static Arguments invalid(String name, Consumer<ObjectNode> mutate) {
        return Arguments.of(name, mutate);
    }
}
