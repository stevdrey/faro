// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import io.github.stevdrey.faro.speech.ProviderConfig;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The provider has no default: the fake is selected only by the dev profile or test configuration. */
@ConfigurationProperties("faro.speech")
public record SpeechProperties(String provider,
                               @DefaultValue("es-CR") String language,
                               @DefaultValue("8s") Duration timeout,
                               String endpoint) {
    public ProviderConfig toConfig() {
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("faro.speech.provider must be set; use the dev profile for the fake");
        }
        return new ProviderConfig(provider, language, timeout, LocalOnlyPolicy.endpoint(endpoint));
    }

    @Override
    public String toString() {
        return "SpeechProperties[provider=" + provider + ", language=" + language + ", timeout=" + timeout
                + ", endpoint=" + (endpoint == null ? "<none>" : "<redacted>") + "]";
    }
}
