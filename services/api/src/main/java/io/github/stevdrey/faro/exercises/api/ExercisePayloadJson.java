// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises.api;

import io.github.stevdrey.faro.common.StrictContractJson;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict v1 codec. Incoming exercise payloads must be read through this component; the
 * application-wide mapper keeps Spring Boot's defaults for every other JSON binding.
 */
@Component
public class ExercisePayloadJson {
    private final JsonMapper mapper;

    public ExercisePayloadJson(JsonMapper applicationMapper) {
        this.mapper = StrictContractJson.rebuild(applicationMapper);
    }

    public ExercisePayload read(String json) {
        return StrictContractJson.read(mapper, json, ExercisePayload.class);
    }

    public ExercisePayload read(InputStream json) {
        return StrictContractJson.read(mapper, json, ExercisePayload.class);
    }

    public String write(ExercisePayload payload) {
        return mapper.writeValueAsString(Objects.requireNonNull(payload, "payload"));
    }
}
