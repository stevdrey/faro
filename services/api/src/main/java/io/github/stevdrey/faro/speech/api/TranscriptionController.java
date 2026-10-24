// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.api;

import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechHypothesis;
import io.github.stevdrey.faro.speech.SpeechRecognizer;
import io.github.stevdrey.faro.speech.SpeechRequest;
import io.github.stevdrey.faro.speech.config.SpeechProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Synthetic preview wire format: raw PCM16 LE mono, 16 kHz, at most 30s. Never multipart/disk-backed. */
@RestController
@ConditionalOnProperty(name = "faro.speech.synthetic-test.enabled", havingValue = "true")
public class TranscriptionController {
    private static final int SAMPLE_RATE_HZ = 16_000;
    private static final Duration MAX_AUDIO = Duration.ofSeconds(30);
    private static final int MAX_BYTES = Math.toIntExact(SAMPLE_RATE_HZ * 2 * MAX_AUDIO.toSeconds());
    private final SpeechRecognizer recognizer;
    private final String language;

    public TranscriptionController(SpeechRecognizer recognizer, SpeechProperties properties) {
        this.recognizer = recognizer;
        this.language = properties.toConfig().language();
    }

    @PostMapping(path = "/api/v1/speech/transcriptions", produces = "application/json")
    public ResponseEntity<?> transcribe(HttpServletRequest request) {
        // Do not let Spring's generic content-type/argument errors echo private request details.
        if (!"application/octet-stream".equalsIgnoreCase(request.getContentType())) {
            return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FORMAT");
        }
        if (request.getContentLengthLong() > MAX_BYTES) return error(HttpStatus.CONTENT_TOO_LARGE, "AUDIO_LIMIT");
        try {
            var pcm = request.getInputStream().readNBytes(MAX_BYTES + 1);
            if (pcm.length > MAX_BYTES) return error(HttpStatus.CONTENT_TOO_LARGE, "AUDIO_LIMIT");
            if (pcm.length == 0 || pcm.length % 2 != 0) return error(HttpStatus.BAD_REQUEST, "INVALID_AUDIO");
            var speechRequest = new SpeechRequest(pcm, SAMPLE_RATE_HZ, language,
                    UUID.randomUUID().toString(), MAX_AUDIO);
            var hypothesis = recognizer.transcribe(speechRequest);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(payload(hypothesis, speechRequest.audioDuration().toMillis()));
        } catch (SpeechFailure failure) {
            return switch (failure.code()) {
                case INCOMPATIBLE_REQUEST -> error(HttpStatus.BAD_REQUEST, "INVALID_AUDIO");
                case TIMEOUT -> error(HttpStatus.GATEWAY_TIMEOUT, "TIMEOUT");
                case UNAVAILABLE -> error(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE");
                case PROVIDER_ERROR -> error(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR");
            };
        } catch (IOException failure) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_AUDIO");
        } catch (RuntimeException failure) {
            return error(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR");
        }
    }

    private static Map<String, Object> payload(SpeechHypothesis result, long audioDurationMs) {
        return Map.of("schemaVersion", 1, "transcript", result.transcript(), "languageTag", result.languageTag(),
                "engineId", result.engineId(), "modelId", result.modelId(), "modelVersion", result.modelVersion(),
                "latencyMs", result.latencyMs(), "status", result.status(), "audioDurationMs", audioDurationMs);
    }

    private static ResponseEntity<?> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("code", code));
    }
}
