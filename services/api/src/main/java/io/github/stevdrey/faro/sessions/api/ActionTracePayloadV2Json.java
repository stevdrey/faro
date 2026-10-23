// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions.api;

import io.github.stevdrey.faro.common.StrictContractCodec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Strict v2 action-contract binding. */
@Component
public class ActionTracePayloadV2Json extends StrictContractCodec<ActionTracePayloadV2> {
    public ActionTracePayloadV2Json(JsonMapper applicationMapper) {
        super(applicationMapper, ActionTracePayloadV2.class);
    }
}
