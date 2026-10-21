// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment.api;

import io.github.stevdrey.faro.common.StrictContractJson;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Strict assessment-contract binding; does not change the application-wide mapper. */
@Component
public class AssessmentResultPayloadJson {
    private final JsonMapper mapper;

    public AssessmentResultPayloadJson(JsonMapper applicationMapper) {
        this.mapper = StrictContractJson.rebuild(applicationMapper);
    }

    public AssessmentResultPayload read(String json) {
        return StrictContractJson.read(mapper, json, AssessmentResultPayload.class);
    }

    public AssessmentResultPayload read(InputStream json) {
        return StrictContractJson.read(mapper, json, AssessmentResultPayload.class);
    }

    public String write(AssessmentResultPayload payload) {
        return mapper.writeValueAsString(Objects.requireNonNull(payload, "payload"));
    }
}
