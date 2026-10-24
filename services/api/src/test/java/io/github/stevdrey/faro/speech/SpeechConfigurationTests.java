// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import static org.assertj.core.api.Assertions.assertThat;

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
    void unsetProviderFailsStartupInsteadOfDefaultingToFake() {
        runner.run(context -> assertThat(context).hasFailed().getFailure()
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("faro.speech.provider must be set"));
    }

    @Test
    void explicitFakeSelectionReturnsEmptyHypotheses() {
        runner.withPropertyValues("faro.speech.provider=fake").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SpeechRecognizer.class);
            var result = context.getBean(SpeechRecognizer.class).transcribe(request("es-CR"));
            assertThat(result.status()).isEqualTo(SpeechStatus.EMPTY);
            assertThat(result.engineId()).isEqualTo("fake");
        });
    }

    @Test
    void configurationSelectsInstalledWhisperFactoryAndPassesLanguageAndTimeout() {
        var received = new AtomicReference<ProviderConfig>();
        runner.withBean("testWhisperProvider", AsrProvider.class, () -> provider("whisper-cpp", config -> {
                    received.set(config);
                    return testWhisper();
                }))
                .withPropertyValues("faro.speech.provider=whisper-cpp", "faro.speech.language=es-CR", "faro.speech.timeout=3s")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SpeechRecognizer.class);
                    var result = context.getBean(SpeechRecognizer.class).transcribe(request("es-CR"));
                    assertThat(result.engineId()).isEqualTo("whisper-cpp");
                    assertThat(result.transcript()).isEqualTo("siete");
                    assertThat(received.get()).isEqualTo(new ProviderConfig("whisper-cpp", "es-CR", Duration.ofSeconds(3)));
                });
    }

    @Test
    void languageTagsAreMatchedCaseInsensitively() {
        runner.withPropertyValues("faro.speech.provider=fake", "faro.speech.language=es-cr").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SpeechRecognizer.class).transcribe(request("ES-cr")).status())
                    .isEqualTo(SpeechStatus.EMPTY);
        });
    }

    @Test
    void unusedFactoryIsNotCreated() {
        runner.withBean("testWhisperProvider", AsrProvider.class, () -> provider("whisper-cpp", config -> {
            throw new AssertionError("unselected provider must not be created");
        })).withPropertyValues("faro.speech.provider=fake").run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"whisper-cpp", "unknown"})
    void missingProviderFailsInsteadOfSelectingFake(String id) {
        runner.withPropertyValues("faro.speech.provider=" + id).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(SpeechFailure.class)
                    .hasStackTraceContaining("Speech recognition failed: UNAVAILABLE")
                    .hasStackTraceContaining("ASR provider '" + id + "' is not installed; registered: [fake]");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"faro.speech.provider=", "faro.speech.provider= "})
    void blankProviderFailsStartup(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed().getFailure()
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("faro.speech.provider must be set"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"faro.speech.language=", "faro.speech.language=!!"})
    void invalidLanguageFailsStartup(String property) {
        runner.withPropertyValues("faro.speech.provider=fake", property).run(context -> assertThat(context).hasFailed()
                .getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class).hasStackTraceContaining("language"));
    }

    @Test
    void unsupportedLanguageFailsStartupNamingIt() {
        runner.withPropertyValues("faro.speech.provider=fake", "faro.speech.language=en-US").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(SpeechFailure.class)
                    .hasStackTraceContaining("Speech recognition failed: INCOMPATIBLE_REQUEST")
                    .hasStackTraceContaining("Configured language 'en-US' is not supported by ASR provider 'fake'");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s"})
    void nonPositiveTimeoutFailsStartup(String timeout) {
        runner.withPropertyValues("faro.speech.provider=fake", "faro.speech.timeout=" + timeout).run(context ->
                assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasRootCauseMessage("timeout must be positive"));
    }

    @Test
    void malformedTimeoutFailsBinding() {
        runner.withPropertyValues("faro.speech.provider=fake", "faro.speech.timeout=invalid").run(context ->
                assertThat(context).hasFailed().getFailure().hasStackTraceContaining("faro.speech.timeout"));
    }

    @Test
    void duplicateRegistrationFailsEvenWhenNotSelected() {
        runner.withBean("duplicateFake", AsrProvider.class, () -> provider("fake", config -> testWhisper()))
                .withPropertyValues("faro.speech.provider=whisper-cpp")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseMessage("Duplicate ASR provider registration: 'fake'"));
    }

    @Test
    void unavailableInstalledFactoryDoesNotFallBack() {
        runner.withBean("testWhisperProvider", AsrProvider.class, () -> provider("whisper-cpp", config -> {
            throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
        })).withPropertyValues("faro.speech.provider=whisper-cpp").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(SpeechFailure.class);
        });
    }

    @Test
    void vendorRuntimeFailureIsNormalizedWithoutLeakingItsMessage() {
        runner.withBean("testWhisperProvider", AsrProvider.class, () -> provider("whisper-cpp", config -> {
            throw new IllegalStateException("synthetic-private-detail");
        })).withPropertyValues("faro.speech.provider=whisper-cpp").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(SpeechFailure.class)
                    .hasStackTraceContaining("Speech recognition failed: PROVIDER_ERROR")
                    .satisfies(failure -> assertThat(stackTrace(failure)).doesNotContain("synthetic-private-detail"));
        });
    }

    @FunctionalInterface
    private interface Factory {
        SpeechRecognizer create(ProviderConfig config) throws SpeechFailure;
    }

    private static AsrProvider provider(String id, Factory factory) {
        return new AsrProvider() {
            public String id() { return id; }
            public boolean localProcessing() { return true; }
            public SpeechRecognizer create(ProviderConfig config) throws SpeechFailure { return factory.create(config); }
        };
    }

    private static SpeechRequest request(String language) {
        return new SpeechRequest(new byte[] {1, 2}, 16_000, language, "synthetic-correlation", Duration.ofSeconds(1));
    }

    private static String stackTrace(Throwable failure) {
        var out = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(out));
        return out.toString();
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
