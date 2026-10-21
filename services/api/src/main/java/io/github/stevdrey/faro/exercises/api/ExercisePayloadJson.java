// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises.api;

import java.io.InputStream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Strict v1 codec. Incoming exercise payloads must be read through this component; the
 * application-wide mapper keeps Spring Boot's defaults for every other JSON binding.
 */
@Component
public class ExercisePayloadJson {
    private final JsonMapper mapper;

    public ExercisePayloadJson(JsonMapper applicationMapper) {
        this.mapper = applicationMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                // ALLOW_COERCION_OF_SCALARS does not govern numbers/booleans read as strings.
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build();
    }

    public ExercisePayload read(String json) {
        return mapper.readValue(json, ExercisePayload.class);
    }

    public ExercisePayload read(InputStream json) {
        return mapper.readValue(json, ExercisePayload.class);
    }

    public String write(ExercisePayload payload) {
        return mapper.writeValueAsString(payload);
    }
}
