// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import io.github.stevdrey.faro.speech.ProviderConfig;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("faro.speech")
public record SpeechProperties(@DefaultValue("fake") String provider,
                               @DefaultValue("es-CR") String language,
                               @DefaultValue("8s") Duration timeout) {
    public ProviderConfig toConfig() {
        return new ProviderConfig(provider, language, timeout);
    }
}
