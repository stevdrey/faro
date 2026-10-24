// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.github.stevdrey.faro.speech.api.TranscriptionController;
import io.github.stevdrey.faro.speech.config.SpeechConfiguration;
import io.github.stevdrey.faro.speech.config.SyntheticSpeechConfiguration;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
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
        runner.withPropertyValues("faro.speech.synthetic-test.enabled=true", "faro.speech.provider=whisper-cpp",
                        "faro.speech.endpoint=http://127.0.0.1:8085/inference")
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

    @Test
    void selectedWhisperRequiresAnExplicitInferenceEndpoint() {
        for (var endpoint : new String[]{"", "faro.speech.endpoint=http://127.0.0.1:8085/"}) {
            var selected = runner.withPropertyValues("faro.speech.synthetic-test.enabled=true", "faro.speech.provider=whisper-cpp");
            if (!endpoint.isEmpty()) selected = selected.withPropertyValues(endpoint);
            selected.run(context -> assertThat(context).hasFailed().getFailure()
                    .hasRootCauseInstanceOf(SpeechFailure.class)
                    .hasStackTraceContaining("INCOMPATIBLE_REQUEST"));
        }
    }

    @Test
    void realWhisperFactoryIsStillSubjectToTheMergedPolicyAndEndpointVeto() {
        for (var property : new String[]{"faro.speech.endpoint=https://cloud.invalid/inference",
                "faro.privacy.policy=CLOUD_ALLOWED"}) {
            runner.withPropertyValues("faro.speech.synthetic-test.enabled=true", "faro.speech.provider=whisper-cpp", property)
                    .run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining("LOCAL_ONLY"));
        }
    }

    @Test
    void realWhisperUsesTheConfiguredLoopbackEndpointThroughThePrivacyGuard() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inference", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                calls.incrementAndGet();
                var json = "{\"text\":\"seis\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, json.length);
                exchange.getResponseBody().write(json);
            }
        });
        server.start();
        try {
            runner.withPropertyValues("faro.speech.synthetic-test.enabled=true", "faro.speech.provider=whisper-cpp",
                            "faro.speech.endpoint=http://127.0.0.1:" + server.getAddress().getPort() + "/inference")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var result = context.getBean(SpeechRecognizer.class).transcribe(
                                new SpeechRequest(new byte[2], 16_000, "es-CR", "synthetic", Duration.ofSeconds(30)));
                        assertThat(result.status()).isEqualTo(SpeechStatus.TRANSCRIBED);
                        assertThat(result.engineId()).isEqualTo("whisper-cpp");
                        assertThat(calls).hasValue(1);
                    });
        } finally { server.stop(0); }
    }
}
