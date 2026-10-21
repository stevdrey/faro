// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.InputStream;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Shared strict binding for versioned public contracts. Rebuilt mappers inherit application
 * settings, so every leniency that could change accepted input or the written wire shape is pinned.
 */
public final class StrictContractJson {
    private static final JsonInclude.Value ALWAYS =
            JsonInclude.Value.construct(JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS);

    private StrictContractJson() {}

    public static JsonMapper rebuild(JsonMapper applicationMapper) {
        return applicationMapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(JsonReadFeature.ALLOW_JAVA_COMMENTS,
                        JsonReadFeature.ALLOW_YAML_COMMENTS,
                        JsonReadFeature.ALLOW_SINGLE_QUOTES,
                        JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES,
                        JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS,
                        JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS,
                        JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS,
                        JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS,
                        JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS,
                        JsonReadFeature.ALLOW_MISSING_VALUES,
                        JsonReadFeature.ALLOW_TRAILING_COMMA,
                        JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS,
                        JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER,
                        JsonReadFeature.ALLOW_RS_CONTROL_CHAR)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                        DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY,
                        DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL,
                        EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE,
                        EnumFeature.READ_ENUMS_USING_TO_STRING,
                        EnumFeature.WRITE_ENUMS_USING_TO_STRING,
                        EnumFeature.WRITE_ENUMS_USING_INDEX,
                        EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS,
                        MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES,
                        MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS,
                        MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Enum, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                // ALLOW_COERCION_OF_SCALARS does not govern numbers/booleans read as strings.
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                // The wire shape uses record component names and always writes every field.
                .propertyNamingStrategy(null)
                .changeDefaultPropertyInclusion(inclusion -> ALWAYS)
                .build();
    }

    public static <T> T read(JsonMapper mapper, String json, Class<T> type) {
        return requirePayload(mapper.readValue(json, type));
    }

    public static <T> T read(JsonMapper mapper, InputStream json, Class<T> type) {
        return requirePayload(mapper.readValue(json, type));
    }

    // A root JSON null is malformed input, reported like every other decoding failure.
    private static <T> T requirePayload(T payload) {
        if (payload == null) {
            throw DatabindException.from((JsonParser) null, "payload must not be null");
        }
        return payload;
    }
}
