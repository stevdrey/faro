// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import io.github.stevdrey.faro.common.Text;
import io.github.stevdrey.faro.speech.AsrProvider;
import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechRecognizer;
import io.github.stevdrey.faro.speech.adapters.FakeAsrProvider;
import java.util.List;
import java.util.TreeMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechConfiguration {
    @Bean
    AsrProvider fakeAsrProvider() {
        return new FakeAsrProvider();
    }

    /** Provider ids and language tags are configuration, not child data, so failures may name them. */
    @Bean
    SpeechRecognizer speechRecognizer(SpeechProperties properties, List<AsrProvider> providers) {
        var config = properties.toConfig();
        var registry = new TreeMap<String, AsrProvider>();
        for (var provider : providers) {
            var id = Text.requireNonBlank(provider.id(), "provider id");
            if (registry.putIfAbsent(id, provider) != null) {
                throw new IllegalStateException("Duplicate ASR provider registration: '" + id + "'");
            }
        }
        var selected = registry.get(config.provider());
        if (selected == null) {
            throw new IllegalStateException("ASR provider '" + config.provider() + "' is not installed; registered: "
                    + registry.keySet(), new SpeechFailure(SpeechFailure.Code.UNAVAILABLE));
        }
        SpeechRecognizer recognizer;
        try {
            recognizer = selected.create(config);
        } catch (SpeechFailure failure) {
            throw new IllegalStateException("ASR provider '" + config.provider() + "' is unavailable", failure);
        } catch (RuntimeException vendorFailure) {
            // Deliberately dropped: vendor messages may carry paths or other sensitive details.
            throw new IllegalStateException("ASR provider '" + config.provider() + "' failed to start",
                    new SpeechFailure(SpeechFailure.Code.PROVIDER_ERROR));
        }
        if (recognizer == null) {
            throw new IllegalStateException("ASR provider '" + config.provider() + "' returned no recognizer",
                    new SpeechFailure(SpeechFailure.Code.PROVIDER_ERROR));
        }
        if (!recognizer.capabilities().languageTags().contains(config.language())) {
            throw new IllegalStateException("Configured language '" + config.language()
                    + "' is not supported by ASR provider '" + config.provider() + "'",
                    new SpeechFailure(SpeechFailure.Code.INCOMPATIBLE_REQUEST));
        }
        return new GuardedSpeechRecognizer(recognizer, config);
    }
}
