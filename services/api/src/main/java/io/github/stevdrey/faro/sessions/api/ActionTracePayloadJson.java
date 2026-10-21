// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import io.github.stevdrey.faro.common.StrictContractJson;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Strict action-contract binding; does not change the application-wide mapper. */
@Component
public class ActionTracePayloadJson {
    private final JsonMapper mapper;

    public ActionTracePayloadJson(JsonMapper applicationMapper) {
        this.mapper = StrictContractJson.rebuild(applicationMapper);
    }

    public ActionTracePayload read(String json) {
        return StrictContractJson.read(mapper, json, ActionTracePayload.class);
    }

    public ActionTracePayload read(InputStream json) {
        return StrictContractJson.read(mapper, json, ActionTracePayload.class);
    }

    public String write(ActionTracePayload payload) {
        return mapper.writeValueAsString(Objects.requireNonNull(payload, "payload"));
    }
}
