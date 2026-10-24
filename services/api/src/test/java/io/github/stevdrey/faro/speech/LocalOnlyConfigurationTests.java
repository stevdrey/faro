// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stevdrey.faro.speech.config.PrivacyProperties;
import io.github.stevdrey.faro.speech.config.SpeechConfiguration;
import io.github.stevdrey.faro.speech.config.SpeechProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Synthetic adapters only: no network, audio files, credentials or model downloads. */
class LocalOnlyConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SpeechConfiguration.class)
            .withPropertyValues("faro.speech.provider=fake");

    @Test
    void absentPrivacyConfigurationDefaultsToLocalOnly() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PrivacyProperties.class).policy()).isEqualTo("LOCAL_ONLY");
            assertThat(context.getBean(SpeechRecognizer.class).transcribe(request()).status()).isEqualTo(SpeechStatus.EMPTY);
        });
    }

    @Test
    void explicitLocalOnlyAllowsFake() {
        runner.withPropertyValues("faro.privacy.policy=LOCAL_ONLY").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SpeechRecognizer.class).capabilities().localProcessing()).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "CLOUD_ALLOWED", "DISABLED", "unknown"})
    void policyCannotBeDisabledOrChanged(String policy) {
        var created = new AtomicInteger();
        runner.withBean("local", AsrProvider.class, () -> provider("local", true, config -> {
                    created.incrementAndGet();
                    return localRecognizer(request -> null);
                }))
                .withPropertyValues("faro.privacy.policy=" + policy, "faro.speech.provider=local")
                .run(context -> {
                    assertThat(context).hasFailed().getFailure()
                            .hasStackTraceContaining("faro.privacy.policy must be LOCAL_ONLY");
                    assertThat(created).hasValue(0);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"cloud", "whisper-cpp"})
    void remoteDeclarationIsVetoedBeforeFactoryEvenWithLocalEndpointOrLocalProviderName(String id) {
        var created = new AtomicInteger();
        runner.withBean("remote", AsrProvider.class, () -> provider(id, false, config -> {
                    created.incrementAndGet();
                    return localRecognizer(request -> null);
                }))
                .withPropertyValues("faro.speech.provider=" + id, "faro.speech.endpoint=http://127.0.0.1:8085")
                .run(context -> {
                    assertThat(context).hasFailed().getFailure()
                            .hasStackTraceContaining("LOCAL_ONLY vetoed ASR provider before creation");
                    assertThat(created).hasValue(0);
                });
    }

    @Test
    void undeclaredProcessingIsDeniedByDefault() {
        runner.withBean("undeclared", AsrProvider.class, () -> new AsrProvider() {
            public String id() { return "undeclared"; }
            public SpeechRecognizer create(ProviderConfig config) {
                throw new AssertionError("Undeclared provider must never initialize");
            }
        }).withPropertyValues("faro.speech.provider=undeclared").run(context -> assertThat(context)
                .hasFailed().getFailure().hasStackTraceContaining("LOCAL_ONLY vetoed ASR provider before creation"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "invalid synthetic-private-detail", "https://cloud.invalid/inference", "http://192.168.1.2:8085",
        "http://0.0.0.0:8085", "http://[::]:8085", "http://localhost:8085", "http://127.0.0.1.evil.invalid",
        "http://2130706433", "http://127.1", "http://[::ffff:127.0.0.1]", "file:///tmp/asr",
        "ftp://127.0.0.1", "//127.0.0.1:8085", "http://127.0.0.1:0", "http://127.0.0.1:65536",
        "http://synthetic-private-detail@127.0.0.1", "http://127.0.0.1?synthetic-private-detail",
        "http://127.0.0.1#synthetic-private-detail"
    })
    void unsafeEndpointsAreRejectedBeforeLocalFactoryWithoutLeakingValues(String endpoint) {
        var created = new AtomicInteger();
        runner.withBean("local", AsrProvider.class, () -> provider("whisper-cpp", true, config -> {
                    created.incrementAndGet();
                    return localRecognizer(request -> null);
                }))
                .withPropertyValues("faro.speech.provider=whisper-cpp", "faro.speech.endpoint=" + endpoint)
                .run(context -> {
                    assertThat(context).hasFailed().getFailure()
                            .hasStackTraceContaining("LOCAL_ONLY requires an HTTP(S) endpoint")
                            .hasStackTraceContaining("without credentials, query or fragment")
                            .hasStackTraceContaining("IllegalStateException")
                            .satisfies(failure -> assertThat(stackTrace(failure)).doesNotContain("synthetic-private-detail"));
                    assertThat(created).hasValue(0);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:8085/inference", "http://[::1]:8085", "https://127.0.0.1/inference"})
    void installedLocalWhisperReceivesValidatedEndpoint(String endpoint) {
        var received = new AtomicReference<ProviderConfig>();
        runner.withBean("local", AsrProvider.class, () -> provider("whisper-cpp", true, config -> {
                    received.set(config);
                    return localRecognizer(request -> new SpeechHypothesis("siete", "es-CR", "whisper-cpp",
                            "synthetic", "test", 0, SpeechStatus.TRANSCRIBED));
                }))
                .withPropertyValues("faro.speech.provider=whisper-cpp", "faro.speech.endpoint=" + endpoint)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(received.get().endpoint()).isEqualTo(URI.create(endpoint));
                    assertThat(context.getBean(SpeechRecognizer.class).transcribe(request()).engineId()).isEqualTo("whisper-cpp");
                });
    }

    @Test
    void localFactoryCannotReturnRemoteCapabilities() {
        runner.withBean("local", AsrProvider.class, () -> provider("local", true,
                config -> recognizer(new AtomicReference<>(capabilities(false)), request -> {
                    throw new AssertionError("Remote recognizer must never receive audio");
                })))
                .withPropertyValues("faro.speech.provider=local")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("LOCAL_ONLY rejected ASR capabilities"));
    }

    @Test
    void everyRequestRechecksLocalCapabilitiesBeforeDeliveringAudio() {
        var current = new AtomicReference<>(capabilities(true));
        var calls = new AtomicInteger();
        runner.withBean("local", AsrProvider.class, () -> provider("local", true,
                config -> recognizer(current, request -> {
                    calls.incrementAndGet();
                    return null;
                })))
                .withPropertyValues("faro.speech.provider=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    current.set(capabilities(false));
                    assertFailure(context.getBean(SpeechRecognizer.class), SpeechFailure.Code.PROVIDER_ERROR);
                    current.set(null);
                    assertFailure(context.getBean(SpeechRecognizer.class), SpeechFailure.Code.PROVIDER_ERROR);
                    assertThat(calls).hasValue(0);
                });
    }

    @ParameterizedTest
    @EnumSource(SpeechFailure.Code.class)
    void localFailureNeverCreatesOrCallsCloudFallback(SpeechFailure.Code code) {
        verifyNoFallback(request -> { throw new SpeechFailure(code); }, code, "1s");
    }

    @Test
    void localStartupFailureNeverCreatesCloudFallback() {
        var cloudCreated = new AtomicInteger();
        runner.withBean("local", AsrProvider.class, () -> new AsrProvider() {
                    public String id() { return "whisper-cpp"; }
                    public boolean localProcessing() { return true; }
                    public SpeechRecognizer create(ProviderConfig config) throws SpeechFailure {
                        throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
                    }
                })
                .withBean("cloud", AsrProvider.class, () -> provider("cloud", false, config -> {
                    cloudCreated.incrementAndGet();
                    return localRecognizer(request -> null);
                }))
                .withPropertyValues("faro.speech.provider=whisper-cpp")
                .run(context -> {
                    assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(SpeechFailure.class);
                    assertThat(cloudCreated).hasValue(0);
                });
    }

    @Test
    void uncheckedFailureAndNullResultNeverCreateOrCallCloudFallback() {
        verifyNoFallback(request -> { throw new IllegalStateException("synthetic-private-detail"); },
                SpeechFailure.Code.PROVIDER_ERROR, "1s");
        verifyNoFallback(request -> null, SpeechFailure.Code.PROVIDER_ERROR, "1s");
    }

    @Test
    void deadlineInterruptsLocalAdapterWithoutCloudFallback() {
        var interrupted = new CountDownLatch(1);
        verifyNoFallback(request -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
        }, SpeechFailure.Code.TIMEOUT, "100ms");
        assertThat(await(interrupted)).isTrue();
    }

    @Test
    void configurationStringRepresentationsRedactEndpoints() {
        var endpoint = "http://synthetic-private-detail@127.0.0.1";
        assertThat(new ProviderConfig("local", "es-CR", Duration.ofSeconds(1), URI.create(endpoint)).toString())
                .doesNotContain("synthetic-private-detail");
        assertThat(new SpeechProperties("local", "es-CR", Duration.ofSeconds(1), endpoint).toString())
                .doesNotContain("synthetic-private-detail");
    }

    private void verifyNoFallback(Transcriber transcriber, SpeechFailure.Code code, String timeout) {
        var localCalls = new AtomicInteger();
        var cloudCreated = new AtomicInteger();
        var cloudCalls = new AtomicInteger();
        runner.withBean("local", AsrProvider.class, () -> provider("whisper-cpp", true,
                    config -> localRecognizer(request -> {
                        localCalls.incrementAndGet();
                        return transcriber.transcribe(request);
                    })))
                .withBean("cloud", AsrProvider.class, () -> provider("cloud", false, config -> {
                    cloudCreated.incrementAndGet();
                    return localRecognizer(request -> {
                        cloudCalls.incrementAndGet();
                        return null;
                    });
                }))
                .withPropertyValues("faro.speech.provider=whisper-cpp", "faro.speech.timeout=" + timeout)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertFailure(context.getBean(SpeechRecognizer.class), code);
                    assertThat(localCalls).hasValue(1);
                    assertThat(cloudCreated).hasValue(0);
                    assertThat(cloudCalls).hasValue(0);
                });
    }

    @FunctionalInterface
    private interface Factory { SpeechRecognizer create(ProviderConfig config); }

    @FunctionalInterface
    private interface Transcriber { SpeechHypothesis transcribe(SpeechRequest request) throws SpeechFailure; }

    private static AsrProvider provider(String id, boolean local, Factory factory) {
        return new AsrProvider() {
            public String id() { return id; }
            public boolean localProcessing() { return local; }
            public SpeechRecognizer create(ProviderConfig config) { return factory.create(config); }
        };
    }

    private static AsrCapabilities capabilities(boolean local) {
        return new AsrCapabilities(Set.of(16_000), Set.of("es-CR"), Duration.ofSeconds(30), local);
    }

    private static SpeechRecognizer localRecognizer(Transcriber transcriber) {
        return recognizer(new AtomicReference<>(capabilities(true)), transcriber);
    }

    private static SpeechRecognizer recognizer(AtomicReference<AsrCapabilities> capabilities, Transcriber transcriber) {
        return new SpeechRecognizer() {
            public AsrCapabilities capabilities() { return capabilities.get(); }
            public SpeechHypothesis transcribe(SpeechRequest request) throws SpeechFailure {
                return transcriber.transcribe(request);
            }
        };
    }

    private static SpeechRequest request() {
        return new SpeechRequest(new byte[] {1, 2}, 16_000, "es-CR", "synthetic", Duration.ofSeconds(1));
    }

    private static void assertFailure(SpeechRecognizer recognizer, SpeechFailure.Code code) {
        assertThatThrownBy(() -> recognizer.transcribe(request())).isInstanceOfSatisfying(SpeechFailure.class, failure -> {
            assertThat(failure.code()).isEqualTo(code);
            assertThat(failure.getCause()).isNull();
            assertThat(failure.getSuppressed()).isEmpty();
        });
    }

    private static String stackTrace(Throwable failure) {
        var out = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(out));
        return out.toString();
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
