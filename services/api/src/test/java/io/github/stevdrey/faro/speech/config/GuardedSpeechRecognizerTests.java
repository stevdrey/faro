// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stevdrey.faro.speech.AsrCapabilities;
import io.github.stevdrey.faro.speech.ProviderConfig;
import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechHypothesis;
import io.github.stevdrey.faro.speech.SpeechRecognizer;
import io.github.stevdrey.faro.speech.SpeechRequest;
import io.github.stevdrey.faro.speech.SpeechStatus;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GuardedSpeechRecognizerTests {
    private static final SpeechHypothesis RESULT =
            new SpeechHypothesis("siete", "es-CR", "test", "synthetic", "1", 0, SpeechStatus.TRANSCRIBED);

    @Test
    void passesCompatibleRequestsThrough() throws SpeechFailure {
        assertThat(guard(request -> RESULT, Duration.ofSeconds(1)).transcribe(request("es-CR", 2))).isSameAs(RESULT);
    }

    @Test
    void rejectsIncompatibleRequestsWithoutCallingTheAdapter() {
        var calls = new AtomicInteger();
        var guard = new GuardedSpeechRecognizer(recognizer(request -> {
            calls.incrementAndGet();
            return RESULT;
        }, Set.of("es-CR", "en-US")), new ProviderConfig("test", "es-CR", Duration.ofSeconds(1)));
        for (var request : new SpeechRequest[] {request("en-US", 2), request("es-CR", 16_000 * 2 * 31)}) {
            assertFailure(() -> guard.transcribe(request), SpeechFailure.Code.INCOMPATIBLE_REQUEST);
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void enforcesTheInferenceDeadlineAndInterruptsTheAdapter() {
        var interrupted = new CountDownLatch(1);
        var guard = guard(request -> {
            try {
                Thread.sleep(Duration.ofSeconds(30));
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return RESULT;
        }, Duration.ofMillis(100));
        assertFailure(() -> guard.transcribe(request("es-CR", 2)), SpeechFailure.Code.TIMEOUT);
        assertThat(awaitQuietly(interrupted)).isTrue();
    }

    @Test
    void normalizesAdapterRuntimeFailuresAndNullResultsWithoutCauses() {
        var thrown = guard(request -> { throw new IllegalStateException("synthetic-private-detail"); },
                Duration.ofSeconds(1));
        assertThatThrownBy(() -> thrown.transcribe(request("es-CR", 2))).isInstanceOfSatisfying(SpeechFailure.class,
                failure -> {
                    assertThat(failure.code()).isEqualTo(SpeechFailure.Code.PROVIDER_ERROR);
                    assertThat(failure.getCause()).isNull();
                });
        assertFailure(() -> guard(request -> null, Duration.ofSeconds(1)).transcribe(request("es-CR", 2)),
                SpeechFailure.Code.PROVIDER_ERROR);
    }

    @Test
    void preservesAdapterSpeechFailures() {
        assertFailure(() -> guard(request -> { throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE); },
                Duration.ofSeconds(1)).transcribe(request("es-CR", 2)), SpeechFailure.Code.UNAVAILABLE);
    }

    @FunctionalInterface
    private interface Transcriber {
        SpeechHypothesis transcribe(SpeechRequest request) throws SpeechFailure;
    }

    private static GuardedSpeechRecognizer guard(Transcriber transcriber, Duration timeout) {
        return new GuardedSpeechRecognizer(recognizer(transcriber, Set.of("es-CR")),
                new ProviderConfig("test", "es-CR", timeout));
    }

    private static SpeechRecognizer recognizer(Transcriber transcriber, Set<String> languages) {
        var capabilities = new AsrCapabilities(Set.of(16_000), languages, Duration.ofSeconds(30), true);
        return new SpeechRecognizer() {
            public SpeechHypothesis transcribe(SpeechRequest request) throws SpeechFailure {
                return transcriber.transcribe(request);
            }
            public AsrCapabilities capabilities() { return capabilities; }
        };
    }

    private static SpeechRequest request(String language, int bytes) {
        return new SpeechRequest(new byte[bytes], 16_000, language, "synthetic-correlation", Duration.ofSeconds(60));
    }

    private static void assertFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, SpeechFailure.Code code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(SpeechFailure.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static boolean awaitQuietly(CountDownLatch latch) {
        try {
            return latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
