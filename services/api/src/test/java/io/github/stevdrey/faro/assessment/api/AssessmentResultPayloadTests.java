// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stevdrey.faro.assessment.ActionMathState;
import io.github.stevdrey.faro.assessment.AssessmentResult;
import io.github.stevdrey.faro.assessment.NextAction;
import io.github.stevdrey.faro.assessment.StrategyEvidenceState;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.type.LogicalType;

@SpringBootTest
class AssessmentResultPayloadTests {
    private static final String VALID = """
            {"schemaVersion":1,"actionMathState":"CORRECTA","strategyEvidenceState":"COHERENTE",
             "nextAction":"CONTINUAR","ruleIds":["synthetic.coherent.v1"],"assessmentVersion":"synthetic-v1"}
            """;
    private static final List<String> FIELDS = List.of("schemaVersion", "actionMathState",
            "strategyEvidenceState", "nextAction", "ruleIds", "assessmentVersion");
    private static final List<String> AXES = List.of("actionMathState", "strategyEvidenceState", "nextAction");

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private AssessmentResultPayloadJson assessmentJson;

    static Stream<Arguments> combinations() {
        return Arrays.stream(ActionMathState.values()).flatMap(math ->
                Arrays.stream(StrategyEvidenceState.values()).flatMap(evidence ->
                        Arrays.stream(NextAction.values()).map(next -> Arguments.of(math, evidence, next))));
    }

    @ParameterizedTest(name = "{0} / {1} / {2}")
    @MethodSource("combinations")
    void allSixtyCombinationsRoundTripWithoutInferringPolicy(
            ActionMathState math, StrategyEvidenceState evidence, NextAction next) {
        var domain = new AssessmentResult(math, evidence, next, List.of("synthetic.rule.v2"), "custom-v7");
        var payload = AssessmentResultPayload.from(domain);
        var json = mapper.readTree(assessmentJson.write(payload));
        assertThat(json.size()).isEqualTo(6);
        assertThat(json.get("schemaVersion").intValue()).isEqualTo(1);
        assertThat(json.get("actionMathState").stringValue()).isEqualTo(math.name());
        assertThat(json.get("strategyEvidenceState").stringValue()).isEqualTo(evidence.name());
        assertThat(json.get("nextAction").stringValue()).isEqualTo(next.name());
        var decoded = assessmentJson.read(json.toString());
        assertThat(decoded).isEqualTo(payload);
        assertThat(decoded.toDomain()).isEqualTo(domain);
    }

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("coherent", "CORRECTA", "COHERENTE", "CONTINUAR"),
                Arguments.of("arithmetic-error", "INCORRECTA", "ERROR_ARITMETICO_IDENTIFICABLE", "PISTA"),
                Arguments.of("insufficient", "CORRECTA", "INSUFICIENTE", "PEDIR_ACLARACION"),
                Arguments.of("asr-conflict", "CORRECTA", "CONFLICTO_ASR", "PEDIR_ACLARACION"),
                Arguments.of("not-evaluable", "CORRECTA", "NO_EVALUABLE", "REPETIR_AUDIO"),
                Arguments.of("incomplete", "SIN_TERMINAR", "NO_EVALUABLE", "CONTINUAR"));
    }

    @ParameterizedTest(name = "Shared fixture {0}")
    @MethodSource("fixtures")
    void sharedFixturesPreserveStatesAndProvenance(String name, String math, String evidence, String next)
            throws IOException {
        try (var input = getClass().getResourceAsStream("/assessments/v1/fixtures/" + name + ".json")) {
            assertThat(input).as("shared fixture %s", name).isNotNull();
            byte[] bytes = input.readAllBytes();
            var payload = assessmentJson.read(new ByteArrayInputStream(bytes));
            var expected = new AssessmentResult(ActionMathState.valueOf(math), StrategyEvidenceState.valueOf(evidence),
                    NextAction.valueOf(next), name.equals("incomplete") ? List.of() : List.of("synthetic." + name + ".v1"),
                    "synthetic-v1");
            assertThat(payload.toDomain()).isEqualTo(expected);
            assertThat(AssessmentResultPayload.from(expected)).isEqualTo(payload);
            assertThat(mapper.readTree(assessmentJson.write(payload))).isEqualTo(mapper.readTree(bytes));
        }
    }

    static Stream<Arguments> invalidPayloads() {
        var missing = FIELDS.stream().map(field -> invalid("missing " + field, root -> root.remove(field)));
        var nulls = FIELDS.stream().map(field -> invalid("null " + field, root -> root.putNull(field)));
        var enums = AXES.stream().flatMap(field -> Stream.of(
                invalid("unknown " + field, root -> root.put(field, "UNKNOWN")),
                invalid("lowercase " + field, root -> root.put(field, root.get(field).stringValue().toLowerCase(java.util.Locale.ROOT))),
                invalid("ordinal " + field, root -> root.put(field, 0)),
                invalid("numeric string " + field, root -> root.put(field, "0")),
                invalid("boolean " + field, root -> root.put(field, true)),
                invalid("array " + field, root -> root.putArray(field).add("CORRECTA"))));
        var malformed = Stream.of(
                invalid("unknown field", root -> root.put("transcript", "synthetic")),
                invalid("future schema", root -> root.put("schemaVersion", 2)),
                invalid("zero schema", root -> root.put("schemaVersion", 0)),
                invalid("string schema", root -> root.put("schemaVersion", "1")),
                invalid("fractional schema", root -> root.put("schemaVersion", 1.0)),
                invalid("boolean schema", root -> root.put("schemaVersion", true)),
                invalid("overflow schema", root -> root.put("schemaVersion", 2147483648L)),
                invalid("array schema", root -> root.putArray("schemaVersion").add(1)),
                invalid("scalar rules", root -> root.put("ruleIds", "synthetic.rule.v1")),
                invalid("blank rule", root -> root.putArray("ruleIds").add(" \t")),
                invalid("null rule", root -> root.putArray("ruleIds").addNull()),
                invalid("numeric rule", root -> root.putArray("ruleIds").add(1)),
                invalid("fractional rule", root -> root.putArray("ruleIds").add(1.5)),
                invalid("boolean rule", root -> root.putArray("ruleIds").add(true)),
                invalid("blank version", root -> root.put("assessmentVersion", "\t\n")),
                invalid("numeric version", root -> root.put("assessmentVersion", 1)),
                invalid("fractional version", root -> root.put("assessmentVersion", 1.5)),
                invalid("boolean version", root -> root.put("assessmentVersion", true)),
                invalid("array version", root -> root.putArray("assessmentVersion").add("synthetic-v1")),
                invalid("case-insensitive property", root -> {
                    root.remove("nextAction");
                    root.put("NEXTACTION", "CONTINUAR");
                }));
        return Stream.of(missing, nulls, enums, malformed).flatMap(stream -> stream);
    }

    @ParameterizedTest(name = "Reject {0}")
    @MethodSource("invalidPayloads")
    void rejectsInvalidJson(String name, Consumer<ObjectNode> mutate) {
        var json = (ObjectNode) mapper.readTree(VALID);
        mutate.accept(json);
        assertThatThrownBy(() -> assessmentJson.read(json.toString())).isInstanceOf(JacksonException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "1", "true", "\"result\""})
    void rejectsInvalidRoots(String json) {
        assertThatThrownBy(() -> assessmentJson.read(json)).isInstanceOf(JacksonException.class);
    }

    @Test
    void rejectsNullRootsAndWrites() {
        assertThatThrownBy(() -> assessmentJson.read("null")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> assessmentJson.read(new ByteArrayInputStream("null".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> assessmentJson.write(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AssessmentResultPayload.from(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsDuplicatesAndTrailingValues() {
        assertThatThrownBy(() -> assessmentJson.read(VALID.replace("\"schemaVersion\":1",
                "\"schemaVersion\":1,\"schemaVersion\":1"))).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> assessmentJson.read(VALID.replace("\"nextAction\":\"CONTINUAR\"",
                "\"nextAction\":\"CONTINUAR\",\"nextAction\":\"PISTA\""))).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> assessmentJson.read(VALID + " {}")).isInstanceOf(JacksonException.class);
    }

    @Test
    void validatesAndCopiesDirectPayloadConstruction() {
        var ids = new ArrayList<>(List.of("synthetic.rule.v1"));
        var payload = payload(1, ids, "v1");
        ids.clear();
        assertThat(payload.ruleIds()).containsExactly("synthetic.rule.v1");
        assertThatThrownBy(() -> payload.ruleIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(payload(1, List.of(), "v1").ruleIds()).isEmpty();
        assertThatThrownBy(() -> payload(2, List.of(), "v1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payload(1, List.of(), " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payload(1, List.of(" "), "v1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payload(1, null, "v1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> payload(1, Arrays.asList((String) null), "v1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void codecPinsStrictnessWhenApplicationMapperIsPermissive() {
        var permissive = mapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                        DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS,
                        DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(MapperFeature.ALLOW_COERCION_OF_SCALARS,
                        MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES,
                        MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS,
                        MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,
                        EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE,
                        EnumFeature.WRITE_ENUMS_USING_INDEX)
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.TryConvert)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.TryConvert)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.TryConvert))
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.TryConvert)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.TryConvert)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.TryConvert))
                .withCoercionConfig(LogicalType.Enum, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.TryConvert))
                .build();
        var strict = new AssessmentResultPayloadJson(permissive);
        invalidPayloads().forEach(arguments -> {
            Object[] args = arguments.get();
            @SuppressWarnings("unchecked")
            var mutate = (Consumer<ObjectNode>) args[1];
            var json = (ObjectNode) mapper.readTree(VALID);
            mutate.accept(json);
            assertThatThrownBy(() -> strict.read(json.toString())).as("%s", args[0])
                    .isInstanceOf(JacksonException.class);
        });
        var extra = (ObjectNode) mapper.readTree(VALID);
        extra.put("extra", true);
        assertThat(permissive.readValue(extra.toString(), AssessmentResultPayload.class)).isNotNull();
        var encoded = mapper.readTree(strict.write(strict.read(VALID)));
        assertThat(encoded).isEqualTo(mapper.readTree(VALID));
        assertThat(permissive.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)).isTrue();
        assertThat(mapper.readValue(extra.toString(), AssessmentResultPayload.class)).isNotNull();
        assertThatThrownBy(() -> assessmentJson.read(extra.toString())).isInstanceOf(JacksonException.class);
    }

    private static AssessmentResultPayload payload(int schema, List<String> ids, String version) {
        return new AssessmentResultPayload(schema, ActionMathState.CORRECTA, StrategyEvidenceState.COHERENTE,
                NextAction.CONTINUAR, ids, version);
    }

    private static Arguments invalid(String name, Consumer<ObjectNode> mutate) {
        return Arguments.of(name, mutate);
    }
}
