// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import io.github.stevdrey.faro.common.Text;
import io.github.stevdrey.faro.speech.AsrProvider;
import io.github.stevdrey.faro.speech.ProviderConfig;
import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechRecognizer;
import io.github.stevdrey.faro.speech.adapters.FakeSpeechRecognizer;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechConfiguration {
    @Bean
    AsrProvider fakeAsrProvider() {
        return new AsrProvider() {
            @Override
            public String id() { return "fake"; }

            @Override
            public SpeechRecognizer create(ProviderConfig config) { return new FakeSpeechRecognizer(); }
        };
    }

    @Bean
    SpeechRecognizer speechRecognizer(SpeechProperties properties, List<AsrProvider> providers) throws SpeechFailure {
        var config = properties.toConfig();
        var registry = new HashMap<String, AsrProvider>();
        for (var provider : providers) {
            var id = Text.requireNonBlank(provider.id(), "provider id");
            if (registry.putIfAbsent(id, provider) != null) {
                throw new IllegalStateException("Duplicate ASR provider registration");
            }
        }
        var selected = registry.get(config.provider());
        if (selected == null) {
            throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
        }
        var recognizer = Objects.requireNonNull(selected.create(config), "recognizer");
        if (!recognizer.capabilities().languageTags().contains(config.language())) {
            throw new SpeechFailure(SpeechFailure.Code.INCOMPATIBLE_REQUEST);
        }
        return recognizer;
    }
}
