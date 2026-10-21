// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import io.github.stevdrey.faro.common.Text;
import java.time.Duration;
import java.util.Objects;

/** Transient signed PCM16 little-endian mono audio; maxDuration bounds audio, not inference time. */
public record SpeechRequest(byte[] pcm16Mono, int sampleRateHz, String languageTag,
                            String correlationId, Duration maxDuration) {
    public SpeechRequest {
        Objects.requireNonNull(pcm16Mono, "pcm16Mono");
        if (sampleRateHz <= 0) {
            throw new IllegalArgumentException("sampleRateHz must be positive");
        }
        Text.requireNonBlank(languageTag, "languageTag");
        Text.requireNonBlank(correlationId, "correlationId");
        Objects.requireNonNull(maxDuration, "maxDuration");
        if (maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("maxDuration must be positive");
        }
        if (pcm16Mono.length % 2 != 0) {
            throw new IllegalArgumentException("pcm16Mono must contain complete 16-bit samples");
        }
        long samples = pcm16Mono.length / 2;
        // Round up fractional nanoseconds so even a partial sample beyond the bound is rejected.
        var audioDuration = Duration.ofSeconds(samples / sampleRateHz)
                .plusNanos(((samples % sampleRateHz) * 1_000_000_000L + sampleRateHz - 1) / sampleRateHz);
        if (audioDuration.compareTo(maxDuration) > 0) {
            throw new IllegalArgumentException("audio exceeds maxDuration");
        }
        pcm16Mono = pcm16Mono.clone();
    }

    @Override
    public byte[] pcm16Mono() {
        return pcm16Mono.clone();
    }

    @Override
    public String toString() {
        return "SpeechRequest[audio=<redacted>, metadata=<redacted>]";
    }
}
