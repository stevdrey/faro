// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.common;

import java.io.InputStream;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/** Strict binding for one versioned contract type; does not change the application-wide mapper. */
public abstract class StrictContractCodec<T> {
    private final JsonMapper mapper;
    private final Class<T> type;

    protected StrictContractCodec(JsonMapper applicationMapper, Class<T> type) {
        this.mapper = StrictContractJson.rebuild(applicationMapper);
        this.type = Objects.requireNonNull(type, "type");
    }

    public T read(String json) {
        return StrictContractJson.read(mapper, json, type);
    }

    public T read(InputStream json) {
        return StrictContractJson.read(mapper, json, type);
    }

    public String write(T payload) {
        return mapper.writeValueAsString(Objects.requireNonNull(payload, "payload"));
    }
}
