// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stevdrey.faro.sessions.ActionEvent;
import io.github.stevdrey.faro.sessions.ActionReplay;
import io.github.stevdrey.faro.sessions.ActionTrace;
import io.github.stevdrey.faro.sessions.TensAndOnesReplay;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
class ActionTracePayloadV2Tests {
    private static final String AT = "2026-01-01T00:00:00Z";
    @Autowired private ObjectMapper mapper;
    @Autowired private ActionTracePayloadV2Json json;
    @Autowired private ActionTracePayloadJson v1;

    @Test
    void sharedFixtureRoundTripsAndReplaysEveryPrefixLikeTypeScript() throws IOException {
        ActionTracePayloadV2 payload;
        try (var input = getClass().getResourceAsStream("/actions/v2/fixtures/subtraction.json")) {
            assertThat(input).isNotNull();
            payload = json.read(input);
        }
        assertThat(json.read(json.write(payload))).isEqualTo(payload);
        assertThat(ActionTracePayloadV2.from(payload.initialValue(), payload.toDomain())).isEqualTo(payload);
        var events = payload.toDomain().events();
        for (int end = 0; end <= events.size(); end++) {
            var result = TensAndOnesReplay.replay(53, new ActionTrace(events.subList(0, end)));
            assertThat(result.remainingValue() + result.removedValue()).isEqualTo(53);
            if (end == 2) assertThat(result).isEqualTo(new TensAndOnesReplay.Result(3, 3, 2, 0, false));
            if (end == 3) assertThat(result).isEqualTo(new TensAndOnesReplay.Result(2, 13, 2, 0, false));
        }
        assertThat(TensAndOnesReplay.replay(53, payload.toDomain()))
                .isEqualTo(new TensAndOnesReplay.Result(2, 5, 2, 8, true));
        assertThatThrownBy(() -> v1.read(json.write(payload))).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> payload.events().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    // Reuse the v1 malformed-input matrix so both versions retain identical binding strictness.
    @ParameterizedTest(name = "v2 rejects {0}")
    @MethodSource("io.github.stevdrey.faro.sessions.api.ActionTracePayloadTests#invalidPayloads")
    void rejectsLegacyMalformedShapes(String name, Consumer<ObjectNode> mutate) throws IOException {
        ObjectNode root;
        try (var input = getClass().getResourceAsStream("/actions/v1/fixtures/blocks.json")) {
            root = (ObjectNode) mapper.readTree(input);
        }
        root.put("schemaVersion", 2);
        mutate.accept(root);
        if (name.equals("future version")) root.put("schemaVersion", 3);
        assertThatThrownBy(() -> json.read(mapper.writeValueAsString(root))).isInstanceOf(JacksonException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"decomposeTen", "composeTen"})
    void exchangesHaveStrictStructuralMetadataAndCannotBeDowngraded(String kind) throws IOException {
        String valid = """
                {"schemaVersion":2,"initialValue":53,"events":[
                  {"kind":"%s","step":1,"occurredAt":"2026-01-01T00:00:00.100Z"}
                ]}
                """.formatted(kind);
        var payload = json.read(valid);
        assertThat(payload.events().getFirst().occurredAt()).isEqualTo("2026-01-01T00:00:00.100Z");
        assertThat(json.read(json.write(payload))).isEqualTo(payload);
        assertThatThrownBy(() -> v1.read(valid.replace("\"schemaVersion\":2", "\"schemaVersion\":1")))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> ActionTracePayload.from(53, payload.toDomain())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActionReplay.replay(53, payload.toDomain()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TensAndOnesReplay");
        for (String value : List.of("0", "10", "null")) {
            assertThatThrownBy(() -> json.read(valid.replace("\"step\":1", "\"value\":" + value + ",\"step\":1")))
                    .isInstanceOf(JacksonException.class);
        }
        for (String field : List.of("step", "occurredAt")) {
            var root = (ObjectNode) mapper.readTree(valid);
            ((ObjectNode) root.get("events").get(0)).remove(field);
            assertThatThrownBy(() -> json.read(mapper.writeValueAsString(root))).isInstanceOf(JacksonException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"blocks", "number-line", "subtraction", "incomplete"})
    void allFiveLegacyEventsStillRoundTripInV2(String fixture) throws IOException {
        ObjectNode root;
        try (var input = getClass().getResourceAsStream("/actions/v1/fixtures/" + fixture + ".json")) {
            root = (ObjectNode) mapper.readTree(input);
        }
        root.put("schemaVersion", 2);
        var payload = json.read(mapper.writeValueAsString(root));
        assertThat(mapper.readTree(json.write(payload))).isEqualTo(root);
        assertThat(ActionTracePayloadV2.from(payload.initialValue(), payload.toDomain())).isEqualTo(payload);
    }

    @Test
    void rejectsAmbiguousJsonRootNullAndWrongVersions() {
        for (String invalid : List.of("null", "{\"schemaVersion\":2,\"initialValue\":0,\"initialValue\":1,\"events\":[]}",
                "{\"schemaVersion\":2,\"initialValue\":0,\"events\":[]} {}",
                "{\"schemaVersion\":1,\"initialValue\":0,\"events\":[]}")) {
            assertThatThrownBy(() -> json.read(invalid)).isInstanceOf(JacksonException.class);
        }
        assertThatThrownBy(() -> json.read(new ByteArrayInputStream("null".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> json.write(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorsCopyValidateAndAllowEmptyOrImmediatelyFinishedTraces() {
        var event = new ActionTracePayloadV2.DecomposeTen(1, AT);
        var source = new ArrayList<ActionTracePayloadV2.Event>(List.of(event));
        var payload = new ActionTracePayloadV2(2, 53, source);
        source.clear();
        assertThat(payload.events()).containsExactly(event);
        assertThatThrownBy(() -> new ActionTracePayloadV2(1, 53, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionTracePayloadV2(2, 53, List.of(event, event))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionTracePayloadV2.ComposeTen(0, AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionTracePayloadV2.DecomposeTen(1, "2026-02-30T00:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionTracePayloadV2.ComposeTen(1, null)).isInstanceOf(NullPointerException.class);
        assertThat(new ActionTracePayloadV2(2, 0, List.of()).toDomain().events()).isEmpty();
        var finished = ActionTracePayloadV2.from(53,
                new ActionTrace(List.of(new ActionEvent.FinishAttempt(1, Instant.parse(AT)))));
        assertThat(TensAndOnesReplay.replay(53, finished.toDomain()).completed()).isTrue();
    }
}
