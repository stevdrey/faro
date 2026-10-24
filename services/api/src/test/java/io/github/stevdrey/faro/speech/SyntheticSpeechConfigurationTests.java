// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stevdrey.faro.speech.api.TranscriptionController;
import io.github.stevdrey.faro.speech.config.SpeechConfiguration;
import io.github.stevdrey.faro.speech.config.SyntheticSpeechConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SyntheticSpeechConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SpeechConfiguration.class, SyntheticSpeechConfiguration.class, TranscriptionController.class)
            .withPropertyValues("server.address=127.0.0.1", "faro.speech.provider=fake");
    @Test
    void ordinaryStartupDoesNotInstallWhisperOrExposeAudioEndpoint() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TranscriptionController.class)
                .doesNotHaveBean("whisperCppProvider"));
        runner.withPropertyValues("faro.speech.synthetic-test.enabled=false").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(TranscriptionController.class));
    }
    @Test
    void optInRegistersRealAdapterWithManifestProvenanceWithoutNetworkOrModelDownload() {
        runner.withPropertyValues("faro.speech.synthetic-test.enabled=true", "faro.speech.provider=whisper-cpp")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(TranscriptionController.class);
                    var result = context.getBean(SpeechRecognizer.class).transcribe(
                            new SpeechRequest(new byte[0], 16_000, "es-CR", "synthetic", java.time.Duration.ofSeconds(30)));
                    assertThat(result.status()).isEqualTo(SpeechStatus.EMPTY);
                    assertThat(result.modelId()).isEqualTo("base-multilingual");
                    assertThat(result.modelVersion()).matches("[0-9a-f]{40}");
                });
    }
    @Test
    void syntheticPreviewRejectsLanBinding() {
        runner.withPropertyValues("faro.speech.synthetic-test.enabled=true", "server.address=0.0.0.0")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseMessage("Synthetic speech tests require server.address=127.0.0.1"));
    }
}
