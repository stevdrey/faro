// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import io.github.stevdrey.faro.speech.AsrCapabilities;
import io.github.stevdrey.faro.speech.AsrProvider;
import io.github.stevdrey.faro.speech.SpeechFailure;
import java.net.URI;

/** A backend admission gate for trusted adapters, not an OS network sandbox. */
final class LocalOnlyPolicy {
    private LocalOnlyPolicy() {}

    static URI endpoint(String value) {
        if (value == null) return null; // In-process adapters need no transport.
        try {
            var endpoint = URI.create(value);
            var host = endpoint.getHost();
            if (("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                    && ("127.0.0.1".equals(host) || "[::1]".equals(host))
                    && endpoint.getRawUserInfo() == null && endpoint.getRawQuery() == null
                    && endpoint.getRawFragment() == null
                    && (endpoint.getPort() == -1 || endpoint.getPort() >= 1 && endpoint.getPort() <= 65535)) {
                return endpoint;
            }
        } catch (IllegalArgumentException invalid) {
            // Drop parser messages: endpoints may contain credentials or other private configuration.
        }
        throw new IllegalStateException("LOCAL_ONLY requires an HTTP(S) endpoint on literal 127.0.0.1 or [::1]"
                + " without credentials, query or fragment");
    }

    static void requireLocal(AsrProvider provider) {
        if (!provider.localProcessing()) {
            throw new IllegalStateException("LOCAL_ONLY vetoed ASR provider before creation");
        }
    }

    static void requireLocal(AsrCapabilities capabilities) throws SpeechFailure {
        if (capabilities == null || !capabilities.localProcessing()) {
            throw new SpeechFailure(SpeechFailure.Code.PROVIDER_ERROR);
        }
    }
}
