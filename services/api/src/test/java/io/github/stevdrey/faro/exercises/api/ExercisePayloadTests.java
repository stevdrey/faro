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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Uses the strict v1 codec; the application mapper only parses fixtures into trees. */
@SpringBootTest
class ExercisePayloadTests {
    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ExercisePayloadJson exerciseJson;

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("missing-addend", ExercisePayload.MissingAddend.class,
                        ExerciseType.MISSING_ADDEND, new ExerciseOperands.MissingAddend(18, 12),
                        new ExercisePayload.MissingAddendOperands(18, 12), 6),
                Arguments.of("missing-addend-known-first", ExercisePayload.MissingAddend.class,
                        ExerciseType.MISSING_ADDEND, new ExerciseOperands.MissingAddend(18, 12),
                        new ExercisePayload.MissingAddendOperands(18, 12), 6),
                Arguments.of("missing-addend-missing-first", ExercisePayload.MissingAddend.class,
                        ExerciseType.MISSING_ADDEND, new ExerciseOperands.MissingAddend(18, 12),
                        new ExercisePayload.MissingAddendOperands(18, 12), 6),
                Arguments.of("sequence", ExercisePayload.Sequence.class,
                        ExerciseType.SEQUENCE, new ExerciseOperands.Sequence(List.of(30, 28)),
                        new ExercisePayload.SequenceOperands(List.of(30, 28)), 26),
                Arguments.of("subtraction", ExercisePayload.Subtraction.class,
                        ExerciseType.SUBTRACTION, new ExerciseOperands.Subtraction(53, 28),
                        new ExercisePayload.SubtractionOperands(53, 28), 25));
    }

    @ParameterizedTest(name = "{0}: JSON round trip")
    @MethodSource("fixtures")
    void roundTripsPublicFixtures(
            String fixture, Class<? extends ExercisePayload> variant,
            ExerciseType type, ExerciseOperands operands, ExercisePayload.Operands wireOperands,
            int expectedResult) throws IOException {
        JsonNode json = fixture(fixture);
        ExercisePayload payload = exerciseJson.read(mapper.writeValueAsString(json));

        assertThat(payload).isInstanceOf(variant);
        assertThat(payload.type()).isEqualTo(type);
        assertThat(payload.operands()).isEqualTo(wireOperands);
        assertThat(payload.schemaVersion()).isEqualTo(1);
        if (payload instanceof ExercisePayload.MissingAddend missingAddend) {
            assertThat(missingAddend.presentation().name()).isEqualTo(json.get("presentation").asString());
        }
        assertThat(mapper.readTree(exerciseJson.write(payload))).isEqualTo(json);
    }

    @ParameterizedTest(name = "{0}: backend projection hides solution")
    @MethodSource("fixtures")
    void projectsOnlyPublicFields(
            String fixture, Class<? extends ExercisePayload> variant,
            ExerciseType type, ExerciseOperands operands, ExercisePayload.Operands wireOperands,
            int expectedResult) throws IOException {
        JsonNode json = fixture(fixture);
        ExerciseDefinition definition = new ExerciseDefinition(
                json.get("exerciseId").asString(), operands,
                VisualModel.valueOf(json.get("visualModel").asString()),
                Difficulty.valueOf(json.get("difficulty").asString()), expectedResult);

        ExercisePayload payload = type == ExerciseType.MISSING_ADDEND
                ? ExercisePayload.from(definition,
                        ExercisePayload.MissingAddendPresentation.valueOf(json.get("presentation").asString()))
                : ExercisePayload.from(definition);
        JsonNode serialized = mapper.readTree(exerciseJson.write(payload));

        assertThat(definition.type()).isEqualTo(type);
        assertThat(payload).isInstanceOf(variant);
        assertThat(payload.type()).isEqualTo(type);
        assertThat(payload.operands()).isEqualTo(wireOperands);
        assertThat(serialized).isEqualTo(json);
        List<String> publicFields = new ArrayList<>(List.of(
                "exerciseId", "schemaVersion", "type", "operands", "visualModel", "difficulty"));
        if (type == ExerciseType.MISSING_ADDEND) {
            publicFields.add("presentation");
        }
        assertThat(serialized.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrderElementsOf(publicFields);
        assertThat(serialized.findValues("expectedResult")).isEmpty();
    }

    static Stream<Arguments> invalidPayloads() {
        Stream<Arguments> missingFields = Stream.of(
                "exerciseId", "schemaVersion", "type", "operands", "visualModel", "difficulty", "presentation")
                .map(field -> invalid("missing " + field, root -> root.remove(field)));
        Stream<Arguments> nullFields = Stream.of(
                "exerciseId", "schemaVersion", "type", "operands", "visualModel", "difficulty", "presentation")
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
                invalid("unknown presentation", root -> root.put("presentation", "FIRST")),
                invalid("numeric presentation", root -> root.put("presentation", 0)),
                invalid("numeric-string presentation", root -> root.put("presentation", "0")),
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
                invalid("mismatched family", root -> changeFamily(root, ExerciseType.SUBTRACTION)),
                invalid("missing minuend", root -> {
                    changeFamily(root, ExerciseType.SUBTRACTION);
                    root.putObject("operands").put("subtrahend", 28);
                }),
                invalid("missing subtrahend", root -> {
                    changeFamily(root, ExerciseType.SUBTRACTION);
                    root.putObject("operands").put("minuend", 53);
                }),
                invalid("missing terms", root -> {
                    changeFamily(root, ExerciseType.SEQUENCE);
                    root.putObject("operands");
                }),
                invalid("null terms", root -> {
                    changeFamily(root, ExerciseType.SEQUENCE);
                    root.putObject("operands").putNull("terms");
                }),
                invalid("empty sequence", root -> {
                    changeFamily(root, ExerciseType.SEQUENCE);
                    root.putObject("operands").putArray("terms");
                }),
                invalid("short sequence", root -> {
                    changeFamily(root, ExerciseType.SEQUENCE);
                    root.putObject("operands").putArray("terms").add(30);
                }),
                invalid("null term", root -> {
                    changeFamily(root, ExerciseType.SEQUENCE);
                    root.putObject("operands").putArray("terms").add(30).addNull();
                }),
                invalid("fractional term", root -> {
                    changeFamily(root, ExerciseType.SEQUENCE);
                    root.putObject("operands").putArray("terms").add(30).add(28.5);
                }),
                invalid("string term", root -> {
                    changeFamily(root, ExerciseType.SEQUENCE);
                    root.putObject("operands").putArray("terms").add(30).add("28");
                }),
                invalid("too many terms", root -> {
                    changeFamily(root, ExerciseType.SEQUENCE);
                    var terms = root.putObject("operands").putArray("terms");
                    for (int i = 0; i <= ExerciseOperands.Sequence.MAX_TERMS; i++) {
                        terms.add(i);
                    }
                }));
        return Stream.concat(Stream.concat(missingFields, nullFields), malformed);
    }

    @ParameterizedTest(name = "Reject {0}")
    @MethodSource("invalidPayloads")
    void rejectsInvalidPayloads(String name, Consumer<ObjectNode> mutate) throws IOException {
        ObjectNode json = (ObjectNode) fixture("missing-addend");
        mutate.accept(json);

        assertThatThrownBy(() -> exerciseJson.read(mapper.writeValueAsString(json)))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void strictnessIsScopedToTheExerciseCodec() throws IOException {
        ObjectNode json = (ObjectNode) fixture("missing-addend");
        json.put("extra", "ignored");

        // The application mapper keeps Spring Boot's lenient defaults for other endpoints.
        assertThat(mapper.readValue(mapper.writeValueAsString(json), ExercisePayload.class))
                .isInstanceOf(ExercisePayload.MissingAddend.class);
        assertThatThrownBy(() -> exerciseJson.read(mapper.writeValueAsString(json)))
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

        assertThat(ExercisePayload.from(definition).operands())
                .isEqualTo(new ExercisePayload.MissingAddendOperands(0, -1));
        assertThatThrownBy(() -> new ExerciseDefinition(" ", operands, VisualModel.BLOCKS, Difficulty.EASY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExerciseDefinition("id", null, VisualModel.BLOCKS, Difficulty.EASY, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExerciseOperands.Sequence(List.of(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExerciseOperands.Sequence(
                Stream.iterate(0, i -> i + 1).limit(ExerciseOperands.Sequence.MAX_TERMS + 1).toList()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExerciseOperands.Sequence(null))
                .isInstanceOf(NullPointerException.class).hasMessage("terms");
        assertThatThrownBy(() -> new ExercisePayload.MissingAddend(
                "id", 2, new ExercisePayload.MissingAddendOperands(18, 12), VisualModel.BLOCKS, Difficulty.EASY,
                ExercisePayload.MissingAddendPresentation.TOTAL_EQUALS_KNOWN_PLUS_MISSING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void projectionDefaultsToTheMvpLayoutAndCanSelectAnAlternative() {
        ExerciseDefinition definition = new ExerciseDefinition(
                "synthetic", new ExerciseOperands.MissingAddend(18, 12),
                VisualModel.BLOCKS, Difficulty.EASY, 6);
        var mvp = (ExercisePayload.MissingAddend) ExercisePayload.from(definition);
        var alternative = ExercisePayload.from(definition,
                ExercisePayload.MissingAddendPresentation.MISSING_PLUS_KNOWN_EQUALS_TOTAL);

        assertThat(mvp.presentation())
                .isEqualTo(ExercisePayload.MissingAddendPresentation.TOTAL_EQUALS_KNOWN_PLUS_MISSING);
        assertThat(alternative.presentation())
                .isEqualTo(ExercisePayload.MissingAddendPresentation.MISSING_PLUS_KNOWN_EQUALS_TOTAL);
        assertThat(alternative.operands()).isEqualTo(mvp.operands());
        assertThat(alternative.exerciseId()).isEqualTo(mvp.exerciseId());
        assertThatThrownBy(() -> ExercisePayload.from(definition, null))
                .isInstanceOf(NullPointerException.class).hasMessage("presentation");
    }

    @Test
    void missingAddendPresentationCannotBeAppliedToAnotherFamily() {
        ExerciseDefinition definition = new ExerciseDefinition(
                "synthetic", new ExerciseOperands.Sequence(List.of(30, 28)),
                VisualModel.NUMBER_LINE, Difficulty.EASY, 26);
        assertThatThrownBy(() -> ExercisePayload.from(definition,
                ExercisePayload.MissingAddendPresentation.MISSING_PLUS_KNOWN_EQUALS_TOTAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}: reject missing-addend presentation")
    @ValueSource(strings = {"sequence", "subtraction"})
    void otherFamiliesRejectMissingAddendPresentation(String fixture) throws IOException {
        ObjectNode json = (ObjectNode) fixture(fixture);
        json.put("presentation", "TOTAL_EQUALS_KNOWN_PLUS_MISSING");
        assertThatThrownBy(() -> exerciseJson.read(mapper.writeValueAsString(json)))
                .isInstanceOf(JacksonException.class);
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

    private static void changeFamily(ObjectNode root, ExerciseType type) {
        root.put("type", type.name());
        root.remove("presentation");
    }

    private static Arguments invalid(String name, Consumer<ObjectNode> mutate) {
        return Arguments.of(name, mutate);
    }
}
