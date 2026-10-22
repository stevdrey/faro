// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import io.github.stevdrey.faro.common.StrictContractJson;
import java.io.InputStream;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Strict action-contract binding; does not change the application-wide mapper. */
@Component
public class ActionTracePayloadV2Json {
    private final JsonMapper mapper;

    public ActionTracePayloadV2Json(JsonMapper applicationMapper) {
        this.mapper = StrictContractJson.rebuild(applicationMapper);
    }

    public ActionTracePayloadV2 read(String json) {
        return StrictContractJson.read(mapper, json, ActionTracePayloadV2.class);
    }

    public ActionTracePayloadV2 read(InputStream json) {
        return StrictContractJson.read(mapper, json, ActionTracePayloadV2.class);
    }

    public String write(ActionTracePayloadV2 payload) {
        return mapper.writeValueAsString(Objects.requireNonNull(payload, "payload"));
    }
}
