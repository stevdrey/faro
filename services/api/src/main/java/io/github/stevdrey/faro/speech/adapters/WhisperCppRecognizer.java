// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.adapters;

import io.github.stevdrey.faro.speech.AsrCapabilities;
import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechHypothesis;
import io.github.stevdrey.faro.speech.SpeechRecognizer;
import io.github.stevdrey.faro.speech.SpeechRequest;
import io.github.stevdrey.faro.speech.SpeechStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** In-memory PCM/WAV transport. The endpoint is a fixed literal loopback address, with no redirects/proxy. */
public final class WhisperCppRecognizer implements SpeechRecognizer {
    private static final AsrCapabilities CAPABILITIES =
            new AsrCapabilities(Set.of(16_000), Set.of("es-CR"), Duration.ofSeconds(30), true);
    private static final int MAX_RESPONSE_BYTES = 65_536;
    /** Aborts exchanges whose runtime never answers (headers or body), which also frees the inference permit. */
    private static final ScheduledThreadPoolExecutor DRAIN = drainTimer();
    private static final ProxySelector DIRECT = new ProxySelector() {
        public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
        public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
    };
    private final URI endpoint;
    private final Duration drainLimit;
    private final String modelId;
    private final String modelVersion;
    private final HttpClient client;
    // whisper.cpp serializes inference and keeps decoding after a client gives up. One permit is held until the
    // runtime replies (or the drain limit), so overlap is rejected instead of queuing private clips in the runtime.
    private final Semaphore inference = new Semaphore(1);
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public WhisperCppRecognizer(Duration timeout, String modelId, String modelVersion) {
        this(URI.create("http://127.0.0.1:8085/inference"), timeout, drainLimitFor(timeout), modelId, modelVersion);
    }

    /** Package-private test seam; even test endpoints must use literal loopback without URI overrides. */
    WhisperCppRecognizer(URI endpoint, Duration timeout, Duration drainLimit, String modelId, String modelVersion) {
        if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !"/inference".equals(endpoint.getPath())) {
            throw new IllegalArgumentException("Only the local baseline inference endpoint is supported");
        }
        this.endpoint = endpoint;
        this.drainLimit = drainLimit;
        this.modelId = modelId;
        this.modelVersion = modelVersion;
        this.client = HttpClient.newBuilder().proxy(DIRECT).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout).build();
    }

    /** The caller-facing inference deadline is GuardedSpeechRecognizer's; this only bounds holding the permit. */
    private static Duration drainLimitFor(Duration timeout) {
        var limit = timeout.multipliedBy(4);
        return limit.compareTo(Duration.ofSeconds(60)) < 0 ? Duration.ofSeconds(60) : limit;
    }

    @Override
    public SpeechHypothesis transcribe(SpeechRequest request) throws SpeechFailure {
        var pcm = request.pcm16Mono();
        if (pcm.length == 0) return hypothesis("", 0);
        if (!inference.tryAcquire()) throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
        long started = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> sent;
        try {
            var boundary = "faro-" + UUID.randomUUID();
            var httpRequest = HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(pcm, boundary))).build();
            sent = client.sendAsync(httpRequest, ignored -> new BoundedBody());
        } catch (RuntimeException failure) {
            inference.release();
            throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
        }
        var pending = sent;
        var drained = new AtomicBoolean();
        var drain = DRAIN.schedule(() -> {
            drained.set(true);
            pending.cancel(true);
        }, drainLimit.toNanos(), TimeUnit.NANOSECONDS);
        pending.whenComplete((ignored, failure) -> {
            drain.cancel(false);
            inference.release();
        });
        HttpResponse<byte[]> response;
        try {
            response = pending.get();
        } catch (CancellationException failure) {
            throw new SpeechFailure(SpeechFailure.Code.TIMEOUT);
        } catch (ExecutionException failure) {
            // Depending on its phase, an aborted exchange may surface as a transport error rather than cancellation.
            throw new SpeechFailure(drained.get() ? SpeechFailure.Code.TIMEOUT
                    : causedBy(failure, ResponseLimit.class) ? SpeechFailure.Code.PROVIDER_ERROR
                    : causedBy(failure, HttpTimeoutException.class) ? SpeechFailure.Code.TIMEOUT
                    : SpeechFailure.Code.UNAVAILABLE);
        } catch (InterruptedException failure) {
            // The guard's deadline. The exchange is left to finish so the runtime is not handed a second clip;
            // its late response is bounded and discarded.
            Thread.currentThread().interrupt();
            throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
        }
        if (response.statusCode() >= 500) throw new SpeechFailure(SpeechFailure.Code.UNAVAILABLE);
        if (response.statusCode() != 200) throw new SpeechFailure(SpeechFailure.Code.PROVIDER_ERROR);
        try {
            var json = mapper.readTree(response.body());
            if (json == null || !json.isObject() || json.has("error") || !json.has("text")
                    || !json.get("text").isString() || json.get("text").asString().length() > 16_000) {
                throw new SpeechFailure(SpeechFailure.Code.PROVIDER_ERROR);
            }
            return hypothesis(json.get("text").asString(), (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException failure) {
            throw new SpeechFailure(SpeechFailure.Code.PROVIDER_ERROR);
        }
    }

    private static ScheduledThreadPoolExecutor drainTimer() {
        var timer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("faro-asr-drain").factory());
        // Cancelled timers must not retain completed exchanges (and their private response bytes).
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        for (var cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }

    private SpeechHypothesis hypothesis(String text, long latency) {
        // The baseline is explicitly instructed to use Spanish; it does not distinguish regional es-CR.
        return new SpeechHypothesis(text, "es", "whisper-cpp", modelId, modelVersion, latency,
                text.isBlank() ? SpeechStatus.EMPTY : SpeechStatus.TRANSCRIBED);
    }

    @Override
    public AsrCapabilities capabilities() { return CAPABILITIES; }

    private static byte[] multipart(byte[] pcm, String boundary) {
        var out = new ByteArrayOutputStream();
        for (var field : List.of(new String[]{"language", "es"}, new String[]{"response_format", "json"},
                new String[]{"temperature", "0.0"}, new String[]{"translate", "false"})) {
            out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field[0]
                    + "\"\r\n\r\n" + field[1] + "\r\n").getBytes(StandardCharsets.US_ASCII));
        }
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
                + "filename=\"clip.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        var wav = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16_000).putInt(32_000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        out.writeBytes(wav.array());
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    /** An oversized response is malformed provider output, not runtime unavailability. */
    private static final class ResponseLimit extends IOException {
        ResponseLimit() { super("ASR response limit", null); }
    }

    /** Cancels oversized responses while receiving them, including unknown-length/chunked bodies. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new ResponseLimit());
                    return;
                }
                var chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable failure) { body.completeExceptionally(new IOException("ASR transport failed")); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
