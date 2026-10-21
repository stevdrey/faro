// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stevdrey.faro.sessions.ActionEvent;
import io.github.stevdrey.faro.sessions.ActionReplay;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
class ActionTracePayloadTests {
    private static final String AT = "2026-01-01T00:00:00Z";

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ActionTracePayloadJson actionJson;

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("blocks", 6, true, List.of(
                        ActionEvent.PlaceBlock.class, ActionEvent.RemoveBlock.class, ActionEvent.FinishAttempt.class)),
                Arguments.of("number-line", 26, true, List.of(
                        ActionEvent.JumpRight.class, ActionEvent.JumpLeft.class,
                        ActionEvent.JumpLeft.class, ActionEvent.FinishAttempt.class)),
                Arguments.of("subtraction", 25, true, List.of(
                        ActionEvent.RemoveBlock.class, ActionEvent.RemoveBlock.class, ActionEvent.FinishAttempt.class)),
                Arguments.of("incomplete", 1, false, List.of(ActionEvent.PlaceBlock.class)));
    }

    @ParameterizedTest(name = "{0}: round trip, projection and deterministic replay")
    @MethodSource("fixtures")
    void verifiesSharedFixtures(String name, int finalValue, boolean completed, List<Class<?>> variants)
            throws IOException {
        JsonNode expected = fixture(name);
        ActionTracePayload payload;
        try (var input = getClass().getResourceAsStream("/actions/v1/fixtures/" + name + ".json")) {
            payload = actionJson.read(input);
        }
        var trace = payload.toDomain();
        var result = new ActionReplay.Result(finalValue, completed);

        assertThat(trace.events().stream().map(Object::getClass).toList()).isEqualTo(variants);
        assertThat(mapper.readTree(actionJson.write(payload))).isEqualTo(expected);
        assertThat(actionJson.read(actionJson.write(payload))).isEqualTo(payload);
        assertThat(ActionTracePayload.from(payload.initialValue(), trace).toDomain()).isEqualTo(trace);
        assertThat(mapper.readTree(actionJson.write(ActionTracePayload.from(payload.initialValue(), trace))))
                .isEqualTo(expected);
        assertThat(ActionReplay.replay(payload.initialValue(), trace)).isEqualTo(result);
        assertThat(ActionReplay.replay(payload.initialValue(), trace)).isEqualTo(result);
    }

    static Stream<Arguments> invalidPayloads() {
        Stream<Arguments> missingEnvelope = Stream.of("schemaVersion", "initialValue", "events")
                .map(field -> invalid("missing " + field, root -> root.remove(field)));
        Stream<Arguments> nullEnvelope = Stream.of("schemaVersion", "initialValue", "events")
                .map(field -> invalid("null " + field, root -> root.putNull(field)));
        Stream<Arguments> missingEvent = Stream.of("kind", "value", "step", "occurredAt")
                .map(field -> invalid("missing event " + field, root -> event(root, 0).remove(field)));
        Stream<Arguments> nullEvent = Stream.of("kind", "value", "step", "occurredAt")
                .map(field -> invalid("null event " + field, root -> event(root, 0).putNull(field)));
        Stream<Arguments> malformed = Stream.of(
                invalid("future version", root -> root.put("schemaVersion", 2)),
                invalid("zero version", root -> root.put("schemaVersion", 0)),
                invalid("string version", root -> root.put("schemaVersion", "1")),
                invalid("fractional version", root -> root.put("schemaVersion", 1.0)),
                invalid("boolean version", root -> root.put("schemaVersion", true)),
                invalid("string initial value", root -> root.put("initialValue", "0")),
                invalid("fractional initial value", root -> root.put("initialValue", 0.5)),
                invalid("boolean initial value", root -> root.put("initialValue", false)),
                invalid("initial value overflow", root -> root.put("initialValue", 2147483648L)),
                invalid("initial value underflow", root -> root.put("initialValue", -2147483649L)),
                invalid("unknown envelope field", root -> root.put("extra", true)),
                invalid("non-array events", root -> root.putObject("events")),
                invalid("null event element", root -> events(root).set(0, NullNode.getInstance())),
                invalid("unknown kind", root -> event(root, 0).put("kind", "dragSprite")),
                invalid("wrong-case kind", root -> event(root, 0).put("kind", "PLACE_BLOCK")),
                invalid("numeric kind", root -> event(root, 0).put("kind", 0)),
                invalid("boolean kind", root -> event(root, 0).put("kind", true)),
                invalid("zero value", root -> event(root, 0).put("value", 0)),
                invalid("negative value", root -> event(root, 0).put("value", -1)),
                invalid("string value", root -> event(root, 0).put("value", "10")),
                invalid("fractional value", root -> event(root, 0).put("value", 10.0)),
                invalid("boolean value", root -> event(root, 0).put("value", true)),
                invalid("value overflow", root -> event(root, 0).put("value", 2147483648L)),
                invalid("zero step", root -> event(root, 0).put("step", 0)),
                invalid("negative step", root -> event(root, 0).put("step", -1)),
                invalid("string step", root -> event(root, 0).put("step", "1")),
                invalid("fractional step", root -> event(root, 0).put("step", 1.0)),
                invalid("boolean step", root -> event(root, 0).put("step", true)),
                invalid("step overflow", root -> event(root, 0).put("step", 2147483648L)),
                invalid("first step not one", root -> event(root, 0).put("step", 2)),
                invalid("duplicate step", root -> event(root, 1).put("step", 1)),
                invalid("skipped step", root -> event(root, 1).put("step", 3)),
                invalid("out-of-order steps", root -> {
                    event(root, 0).put("step", 2);
                    event(root, 1).put("step", 1);
                }),
                invalid("unknown event field", root -> event(root, 0).put("spriteX", 12)),
                invalid("malformed timestamp", root -> event(root, 0).put("occurredAt", "yesterdayZ")),
                invalid("date-only timestamp", root -> event(root, 0).put("occurredAt", "2026-01-01")),
                invalid("zone-free timestamp", root -> event(root, 0).put("occurredAt", "2026-01-01T00:00:00")),
                invalid("offset timestamp", root -> event(root, 0).put("occurredAt", "2025-12-31T18:00:00-06:00")),
                invalid("non-Z UTC timestamp", root -> event(root, 0).put("occurredAt", "2026-01-01T00:00:00+00:00")),
                invalid("lowercase separator timestamp", root -> event(root, 0).put("occurredAt", "2026-01-01t00:00:00Z")),
                invalid("hour 24 timestamp", root -> event(root, 0).put("occurredAt", "2026-01-01T24:00:00Z")),
                invalid("leap second timestamp", root -> event(root, 0).put("occurredAt", "2026-12-31T23:59:60Z")),
                invalid("impossible date timestamp", root -> event(root, 0).put("occurredAt", "2026-02-30T00:00:00Z")),
                invalid("extended year timestamp", root -> event(root, 0).put("occurredAt", "+12026-01-01T00:00:00Z")),
                invalid("minute precision timestamp", root -> event(root, 0).put("occurredAt", "2026-01-01T00:00Z")),
                invalid("excess fraction timestamp", root -> event(root, 0).put("occurredAt", "2026-01-01T00:00:00.1234567890Z")),
                invalid("numeric timestamp", root -> event(root, 0).put("occurredAt", 0)),
                invalid("fractional timestamp", root -> event(root, 0).put("occurredAt", 0.5)),
                invalid("boolean timestamp", root -> event(root, 0).put("occurredAt", true)),
                invalid("finish with value", root -> event(root, 2).put("value", 0)),
                invalid("finish with null value", root -> event(root, 2).putNull("value")),
                invalid("finish missing step", root -> event(root, 2).remove("step")),
                invalid("finish missing timestamp", root -> event(root, 2).remove("occurredAt")),
                invalid("finish null timestamp", root -> event(root, 2).putNull("occurredAt")),
                invalid("event after finish", root -> events(root).addObject()
                        .put("kind", "placeBlock").put("value", 1).put("step", 4).put("occurredAt", AT)),
                invalid("duplicate finish", root -> events(root).addObject()
                        .put("kind", "finishAttempt").put("step", 4).put("occurredAt", AT)));
        return Stream.of(missingEnvelope, nullEnvelope, missingEvent, nullEvent, malformed)
                .flatMap(stream -> stream);
    }

    @ParameterizedTest(name = "Reject {0}")
    @MethodSource("invalidPayloads")
    void rejectsInvalidPayloads(String name, Consumer<ObjectNode> mutate) throws IOException {
        var json = (ObjectNode) fixture("blocks");
        mutate.accept(json);
        assertThatThrownBy(() -> actionJson.read(mapper.writeValueAsString(json)))
                .isInstanceOf(JacksonException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"placeBlock", "removeBlock", "jumpLeft", "jumpRight"})
    void everyMovementRequiresAPositiveIntegerMagnitude(String kind) throws IOException {
        var json = (ObjectNode) fixture("blocks");
        event(json, 0).put("kind", kind);
        assertThat(actionJson.read(mapper.writeValueAsString(json)).events()).hasSize(3);
        event(json, 0).remove("value");
        assertThatThrownBy(() -> actionJson.read(mapper.writeValueAsString(json)))
                .isInstanceOf(JacksonException.class);
        event(json, 0).put("value", -1);
        assertThatThrownBy(() -> actionJson.read(mapper.writeValueAsString(json)))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void acceptsEmptyTraceAndImmediateFinish() {
        var empty = actionJson.read("""
                {"schemaVersion":1,"initialValue":-2,"events":[]}
                """);
        var finished = actionJson.read("""
                {"schemaVersion":1,"initialValue":-2,"events":[
                  {"kind":"finishAttempt","step":1,"occurredAt":"2026-01-01T00:00:00Z"}
                ]}
                """);
        assertThat(ActionReplay.replay(empty.initialValue(), empty.toDomain()))
                .isEqualTo(new ActionReplay.Result(-2, false));
        assertThat(ActionReplay.replay(finished.initialValue(), finished.toDomain()))
                .isEqualTo(new ActionReplay.Result(-2, true));
    }

    @Test
    void acceptsSignedIntegerBoundariesAndNanosecondTimestamps() {
        var payload = actionJson.read("""
                {"schemaVersion":1,"initialValue":-2147483648,"events":[
                  {"kind":"jumpRight","value":2147483647,"step":1,"occurredAt":"2026-01-01T00:00:00.123456789Z"}
                ]}
                """);
        assertThat(ActionReplay.replay(payload.initialValue(), payload.toDomain()))
                .isEqualTo(new ActionReplay.Result(-1, false));
        assertThat(actionJson.read(actionJson.write(payload))).isEqualTo(payload);
        assertThat(ActionTracePayload.from(payload.initialValue(), payload.toDomain())).isEqualTo(payload);
    }

    @Test
    void canonicalizesEquivalentTimestamps() {
        var padded = actionJson.read("""
                {"schemaVersion":1,"initialValue":0,"events":[
                  {"kind":"finishAttempt","step":1,"occurredAt":"2026-01-01T00:00:00.100Z"}
                ]}
                """);
        assertThat(padded.events()).containsExactly(new ActionTracePayload.FinishAttempt(1, "2026-01-01T00:00:00.1Z"));
        assertThat(padded.events().getFirst().occurredAt()).isEqualTo("2026-01-01T00:00:00.100Z");
        assertThat(new ActionTracePayload.PlaceBlock(1, 1, "2026-01-01T00:00:00.000000000Z").occurredAt())
                .isEqualTo(AT);
        assertThat(ActionTracePayload.from(padded.initialValue(), padded.toDomain())).isEqualTo(padded);
    }

    @Test
    void rejectsNullPayloads() {
        assertThatThrownBy(() -> actionJson.read("null")).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> actionJson.read(
                new ByteArrayInputStream("null".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> actionJson.write(null))
                .isInstanceOf(NullPointerException.class).hasMessage("payload");
    }

    @Test
    void rejectsAmbiguousOrTrailingJson() {
        assertThatThrownBy(() -> actionJson.read("""
                {"schemaVersion":1,"initialValue":0,"initialValue":1,"events":[]}
                """)).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> actionJson.read("""
                {"schemaVersion":1,"initialValue":0,"events":[]} {}
                """)).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> actionJson.read("""
                {"schemaVersion":1,"initialValue":0,"events":[
                  {"kind":"placeBlock","kind":"jumpRight","value":1,"step":1,"occurredAt":"2026-01-01T00:00:00Z"}
                ]}
                """)).isInstanceOf(JacksonException.class);
    }

    @Test
    void strictnessIsScopedToTheActionCodec() throws IOException {
        var json = (ObjectNode) fixture("blocks");
        json.put("extra", "ignored by application mapper");
        assertThat(mapper.readValue(mapper.writeValueAsString(json), ActionTracePayload.class))
                .isInstanceOf(ActionTracePayload.class);
        assertThatThrownBy(() -> actionJson.read(mapper.writeValueAsString(json)))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void constructorsValidateAndCopyTransportData() {
        var move = new ActionTracePayload.PlaceBlock(6, 1, AT);
        List<ActionTracePayload.Event> source = new ArrayList<>(List.of(move));
        var payload = new ActionTracePayload(1, 0, source);
        source.clear();
        assertThat(payload.events()).containsExactly(move);
        assertThatThrownBy(() -> payload.events().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ActionTracePayload(2, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionTracePayload(1, 0, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ActionTracePayload(1, 0, Arrays.asList(move, null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ActionTracePayload(1, 0, List.of(
                new ActionTracePayload.FinishAttempt(1, AT), new ActionTracePayload.PlaceBlock(1, 2, AT))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionTracePayload.JumpLeft(0, 1, AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionTracePayload.FinishAttempt(1, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ActionTracePayload.FinishAttempt(1, "yesterdayZ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionTracePayload.FinishAttempt(1, "2026-02-30T00:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JsonNode fixture(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/actions/v1/fixtures/" + name + ".json")) {
            assertThat(input).as("shared fixture %s", name).isNotNull();
            return mapper.readTree(input);
        }
    }

    private static ArrayNode events(ObjectNode root) {
        return (ArrayNode) root.get("events");
    }

    private static ObjectNode event(ObjectNode root, int index) {
        return (ObjectNode) events(root).get(index);
    }

    private static Arguments invalid(String name, Consumer<ObjectNode> mutate) {
        return Arguments.of(name, mutate);
    }
}
