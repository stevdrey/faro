// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import io.github.stevdrey.faro.speech.AsrProvider;
import io.github.stevdrey.faro.speech.adapters.WhisperCppProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Temporary synthetic-only preview, default off. This is not the general FARO-040 policy gate. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "faro.speech.synthetic-test.enabled", havingValue = "true")
public class SyntheticSpeechConfiguration {
    @Bean
    AsrProvider whisperCppProvider(@Value("${server.address:}") String address) {
        if (!"127.0.0.1".equals(address)) {
            throw new IllegalStateException("Synthetic speech tests require server.address=127.0.0.1");
        }
        return new WhisperCppProvider();
    }
}
