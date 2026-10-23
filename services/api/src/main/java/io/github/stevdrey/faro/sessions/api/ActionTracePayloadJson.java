// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import io.github.stevdrey.faro.common.StrictContractCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Strict v1 action-contract binding. */
@Component
public class ActionTracePayloadJson extends StrictContractCodec<ActionTracePayload> {
    public ActionTracePayloadJson(JsonMapper applicationMapper) {
        super(applicationMapper, ActionTracePayload.class);
    }
}
