// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import io.github.stevdrey.faro.speech.AsrCapabilities;
import io.github.stevdrey.faro.speech.AsrProvider;
import io.github.stevdrey.faro.speech.ProviderConfig;
import io.github.stevdrey.faro.speech.SpeechFailure;
import java.net.URI;

/** A backend admission gate for trusted adapters, not an OS network sandbox. */
final class LocalOnlyPolicy {
    private LocalOnlyPolicy() {}

    static URI endpoint(String value) {
        if (value == null) return null; // In-process adapters need no transport.
        try {
            var endpoint = URI.create(value);
            if (ProviderConfig.isLoopbackEndpoint(endpoint)) {
                return endpoint;
            }
        } catch (IllegalArgumentException invalid) {
            // Drop parser messages: endpoints may contain credentials or other private configuration.
        }
        throw new IllegalStateException("LOCAL_ONLY requires an HTTP(S) endpoint on literal 127.0.0.1 or [::1]"
                + " without credentials, query or fragment");
    }

    static void requireLocal(AsrProvider provider) {
        boolean local;
        try {
            local = provider.localProcessing();
        } catch (RuntimeException vendorFailure) {
            // Deliberately dropped: vendor messages may carry paths or other sensitive details.
            throw veto(SpeechFailure.Code.PROVIDER_ERROR);
        }
        if (!local) {
            throw veto(SpeechFailure.Code.UNAVAILABLE);
        }
    }

    private static IllegalStateException veto(SpeechFailure.Code code) {
        return new IllegalStateException("LOCAL_ONLY vetoed ASR provider before creation", new SpeechFailure(code));
    }

    static void requireLocal(AsrCapabilities capabilities) throws SpeechFailure {
        if (capabilities == null || !capabilities.localProcessing()) {
            throw new SpeechFailure(SpeechFailure.Code.PROVIDER_ERROR);
        }
    }
}
