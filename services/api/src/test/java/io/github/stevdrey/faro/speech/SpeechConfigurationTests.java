// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stevdrey.faro.speech.adapters.FakeSpeechRecognizer;
import io.github.stevdrey.faro.speech.config.SpeechConfiguration;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpeechConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SpeechConfiguration.class);

    @Test
    void defaultsToFakeWithoutAnyRuntime() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SpeechRecognizer.class);
            assertThat(context.getBean(SpeechRecognizer.class)).isInstanceOf(FakeSpeechRecognizer.class);
        });
    }

    @Test
    void configurationSelectsInstalledWhisperFactoryAndPassesLanguageAndTimeout() {
        var received = new AtomicReference<ProviderConfig>();
        SpeechRecognizer whisper = testWhisper();
        runner.withBean("testWhisperProvider", AsrProvider.class, () -> provider("whisper-cpp", received, whisper))
                .withPropertyValues("faro.speech.provider=whisper-cpp", "faro.speech.language=es-CR", "faro.speech.timeout=3s")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SpeechRecognizer.class);
                    assertThat(context.getBean(SpeechRecognizer.class)).isSameAs(whisper);
                    assertThat(received.get()).isEqualTo(new ProviderConfig("whisper-cpp", "es-CR", Duration.ofSeconds(3)));
                });
    }

    @Test
    void unusedFactoryIsNotCreated() {
        runner.withBean("testWhisperProvider", AsrProvider.class, () -> new AsrProvider() {
            public String id() { return "whisper-cpp"; }
            public SpeechRecognizer create(ProviderConfig config) {
                throw new AssertionError("unselected provider must not be created");
            }
        }).withPropertyValues("faro.speech.provider=fake").run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"whisper-cpp", "unknown"})
    void missingProviderFailsInsteadOfSelectingFake(String id) {
        runner.withPropertyValues("faro.speech.provider=" + id).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(SpeechFailure.class)
                    .hasStackTraceContaining("Speech recognition failed: UNAVAILABLE");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"faro.speech.provider=", "faro.speech.language=", "faro.speech.language=en-US",
            "faro.speech.timeout=0s", "faro.speech.timeout=-1s", "faro.speech.timeout=invalid"})
    void invalidConfigurationFailsStartup(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void duplicateRegistrationFailsEvenWhenNotSelected() {
        runner.withBean("duplicateFake", AsrProvider.class,
                        () -> provider("fake", new AtomicReference<>(), new FakeSpeechRecognizer()))
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseMessage("Duplicate ASR provider registration"));
    }

    @Test
    void unavailableInstalledFactoryDoesNotFallBack() {
        runner.withBean("testWhisperProvider", AsrProvider.class, () -> new AsrProvider() {
            public String id() { return "whisper-cpp"; }
            public SpeechRecognizer create(ProviderConfig config) throws SpeechFailure {
                throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
            }
        }).withPropertyValues("faro.speech.provider=whisper-cpp").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(SpeechFailure.class);
        });
    }

    private static AsrProvider provider(String id, AtomicReference<ProviderConfig> received, SpeechRecognizer recognizer) {
        return new AsrProvider() {
            public String id() { return id; }
            public SpeechRecognizer create(ProviderConfig config) {
                received.set(config);
                return recognizer;
            }
        };
    }

    /** Test-only installed adapter; never a production whisper implementation or external process. */
    private static SpeechRecognizer testWhisper() {
        return new SpeechRecognizer() {
            public SpeechHypothesis transcribe(SpeechRequest request) {
                return new SpeechHypothesis("siete", "es-CR", "whisper-cpp", "synthetic", "test", 0, SpeechStatus.TRANSCRIBED);
            }
            public AsrCapabilities capabilities() {
                return new AsrCapabilities(Set.of(16_000), Set.of("es-CR"), Duration.ofSeconds(30), true);
            }
        };
    }
}
