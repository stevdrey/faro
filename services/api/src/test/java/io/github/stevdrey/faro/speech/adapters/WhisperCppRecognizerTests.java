// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechRequest;
import io.github.stevdrey.faro.speech.SpeechStatus;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WhisperCppRecognizerTests {
    private HttpServer server;
    private final AtomicReference<String> response = new AtomicReference<>("{\"text\":\"seis\"}");
    private final AtomicReference<byte[]> received = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private volatile int status = 200;
    private Duration drainLimit = Duration.ofSeconds(2);
    private final CompletableFuture<Void> release = new CompletableFuture<>();
    private final CompletableFuture<Void> entered = new CompletableFuture<>();
    private volatile boolean stall;
    private volatile boolean stallBody;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/inference", exchange -> {
            try (exchange) {
                received.set(exchange.getRequestBody().readAllBytes());
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                entered.complete(null);
                if (stall) release.join();
                exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/private");
                var bytes = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, 0); // Chunked, including the oversized-response scenario.
                if (stallBody) release.join();
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
    }
    @AfterEach
    void stop() { release.complete(null); server.stop(0); }
    private WhisperCppRecognizer recognizer() {
        return new WhisperCppRecognizer(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/inference"),
                Duration.ofSeconds(2), drainLimit, "synthetic-model", "revision");
    }
    private SpeechRequest request(byte[] bytes) {
        return new SpeechRequest(bytes, 16_000, "es-CR", "synthetic", Duration.ofSeconds(30));
    }
    @Test
    void sendsValidWavAndExplicitSpanishSettingsAndPreservesHypothesis() throws Exception {
        response.set("{\"text\":\"  seis.\\n\"}");
        var pcm = new byte[]{0, 1, 2, 3};
        var result = recognizer().transcribe(request(pcm));
        assertThat(result.transcript()).isEqualTo("  seis.\n");
        assertThat(result.status()).isEqualTo(SpeechStatus.TRANSCRIBED);
        assertThat(result.languageTag()).isEqualTo("es");
        assertThat(result.engineId()).isEqualTo("whisper-cpp");
        assertThat(result.modelId()).isEqualTo("synthetic-model");
        assertThat(result.modelVersion()).isEqualTo("revision");
        assertThat(result.latencyMs()).isNotNegative();
        assertThat(contentType.get()).startsWith("multipart/form-data; boundary=faro-");
        var body = received.get();
        var text = new String(body, StandardCharsets.ISO_8859_1);
        assertThat(text).contains("name=\"language\"\r\n\r\nes\r\n", "name=\"translate\"\r\n\r\nfalse\r\n",
                "name=\"response_format\"\r\n\r\njson\r\n").doesNotContain("name=\"prompt\"");
        var wav = ByteBuffer.wrap(body, text.indexOf("RIFF"), 44 + pcm.length).slice().order(ByteOrder.LITTLE_ENDIAN);
        assertThat(wav.getInt(4)).isEqualTo(36 + pcm.length);
        assertThat(wav.getShort(20)).isEqualTo((short) 1);
        assertThat(wav.getShort(22)).isEqualTo((short) 1);
        assertThat(wav.getInt(24)).isEqualTo(16_000);
        assertThat(wav.getShort(34)).isEqualTo((short) 16);
        assertThat(wav.getInt(40)).isEqualTo(pcm.length);
        assertThat(new byte[]{wav.get(44), wav.get(45), wav.get(46), wav.get(47)}).isEqualTo(pcm);
    }
    @Test
    void emptyAudioDoesNotContactRuntimeAndWhitespaceResponseIsEmpty() throws Exception {
        assertThat(recognizer().transcribe(request(new byte[0])).status()).isEqualTo(SpeechStatus.EMPTY);
        assertThat(received.get()).isNull();
        response.set("{\"text\":\"  \"}");
        assertThat(recognizer().transcribe(request(new byte[2])).status()).isEqualTo(SpeechStatus.EMPTY);
    }
    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{\"text\":null}", "{\"text\":7}", "{\"text\":\"seis\",\"error\":\"private\"}",
            "{\"text\":\"seis\"} trailing", "{\"text\":\"seis\",\"text\":\"siete\"}", "private-provider-details"})
    void rejectsMalformedResultsWithoutLeakingRawMessages(String value) {
        response.set(value);
        assertThatThrownBy(() -> recognizer().transcribe(request(new byte[2])))
                .isInstanceOfSatisfying(SpeechFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo(SpeechFailure.Code.PROVIDER_ERROR);
                    assertThat(failure.getCause()).isNull();
                    assertThat(failure.getMessage()).doesNotContain(value);
                });
    }
    @Test
    void oversizedChunkedResponseIsBoundedAsAProviderError() {
        response.set("x".repeat(70_000));
        assertThatThrownBy(() -> recognizer().transcribe(request(new byte[2])))
                .isInstanceOfSatisfying(SpeechFailure.class, failure -> assertThat(failure.code()).isEqualTo(SpeechFailure.Code.PROVIDER_ERROR));
    }
    @Test
    void guardInterruptKeepsThePermitUntilTheRuntimeRepliesSoOverlapIsNotQueued() throws Exception {
        stall = true;
        var recognizer = recognizer();
        var outcome = new CompletableFuture<SpeechFailure.Code>();
        var caller = Thread.ofVirtual().start(() -> {
            try {
                recognizer.transcribe(request(new byte[2]));
                outcome.complete(null);
            } catch (SpeechFailure failure) {
                outcome.complete(failure.code());
            }
        });
        entered.get(2, TimeUnit.SECONDS);
        caller.interrupt(); // What GuardedSpeechRecognizer does at its deadline.
        assertThat(outcome.get(2, TimeUnit.SECONDS)).isEqualTo(SpeechFailure.Code.UNAVAILABLE);
        // The runtime is still decoding the abandoned clip: a second clip is rejected, not queued behind it.
        assertThatThrownBy(() -> recognizer.transcribe(request(new byte[2])))
                .isInstanceOfSatisfying(SpeechFailure.class, failure -> assertThat(failure.code()).isEqualTo(SpeechFailure.Code.UNAVAILABLE));
        stall = false;
        release.complete(null);
        assertThat(awaitTranscription(recognizer)).isEqualTo(SpeechStatus.TRANSCRIBED);
    }
    @Test
    void drainLimitAbortsAStalledResponseBodyAndFreesThePermit() throws Exception {
        stallBody = true;
        drainLimit = Duration.ofMillis(300);
        var recognizer = recognizer();
        var work = CompletableFuture.runAsync(() -> assertThatThrownBy(() -> recognizer.transcribe(request(new byte[2])))
                .isInstanceOfSatisfying(SpeechFailure.class, failure -> assertThat(failure.code()).isEqualTo(SpeechFailure.Code.TIMEOUT)));
        work.get(3, TimeUnit.SECONDS);
        stallBody = false;
        assertThat(awaitTranscription(recognizer)).isEqualTo(SpeechStatus.TRANSCRIBED);
    }
    /** The permit is released asynchronously when the abandoned exchange completes. */
    private SpeechStatus awaitTranscription(WhisperCppRecognizer recognizer) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (true) {
            try {
                return recognizer.transcribe(request(new byte[2])).status();
            } catch (SpeechFailure failure) {
                if (failure.code() != SpeechFailure.Code.UNAVAILABLE || System.nanoTime() > deadline) throw failure;
                Thread.sleep(20);
            }
        }
    }
    @Test
    void redirectsAndServerFailuresAreNotFollowed() {
        for (int code : new int[]{302, 503}) {
            status = code;
            assertThatThrownBy(() -> recognizer().transcribe(request(new byte[2])))
                    .isInstanceOfSatisfying(SpeechFailure.class, failure -> assertThat(failure.code())
                            .isEqualTo(code == 302 ? SpeechFailure.Code.PROVIDER_ERROR : SpeechFailure.Code.UNAVAILABLE));
        }
    }
    @Test
    void unavailableRuntimeIsATechnicalFailureAndCapabilitiesAreLeftToTheGuard() {
        server.stop(0);
        assertThatThrownBy(() -> recognizer().transcribe(request(new byte[2]))).isInstanceOf(SpeechFailure.class);
        // GuardedSpeechRecognizer rejects unsupported rates before any adapter is called.
        assertThat(recognizer().capabilities().sampleRatesHz()).containsExactly(16_000);
        assertThat(recognizer().capabilities().languageTags()).containsExactly("es-CR");
    }
}
