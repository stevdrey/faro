// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import java.io.InputStream;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/** Strict action-contract binding; does not change the application-wide mapper. */
@Component
public class ActionTracePayloadJson {
    private final JsonMapper mapper;

    public ActionTracePayloadJson(JsonMapper applicationMapper) {
        this.mapper = applicationMapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                // Rebuilt mappers inherit application settings; pin leniency off explicitly.
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                        DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                        DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS,
                        MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build();
    }

    public ActionTracePayload read(String json) {
        return Objects.requireNonNull(mapper.readValue(json, ActionTracePayload.class), "payload");
    }

    public ActionTracePayload read(InputStream json) {
        return Objects.requireNonNull(mapper.readValue(json, ActionTracePayload.class), "payload");
    }

    public String write(ActionTracePayload payload) {
        return mapper.writeValueAsString(Objects.requireNonNull(payload, "payload"));
    }
}
