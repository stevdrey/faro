// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import io.github.stevdrey.faro.common.Text;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/** Transient signed PCM16 little-endian mono audio; maxDuration bounds audio, not inference time. */
public record SpeechRequest(byte[] pcm16Mono, int sampleRateHz, String languageTag,
                            String correlationId, Duration maxDuration) {
    public SpeechRequest {
        Objects.requireNonNull(pcm16Mono, "pcm16Mono");
        if (sampleRateHz <= 0) {
            throw new IllegalArgumentException("sampleRateHz must be positive");
        }
        languageTag = LanguageTags.normalize(languageTag, "languageTag");
        Text.requireNonBlank(correlationId, "correlationId");
        Objects.requireNonNull(maxDuration, "maxDuration");
        if (maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("maxDuration must be positive");
        }
        if (pcm16Mono.length % 2 != 0) {
            throw new IllegalArgumentException("pcm16Mono must contain complete 16-bit samples");
        }
        if (durationOf(pcm16Mono.length, sampleRateHz).compareTo(maxDuration) > 0) {
            throw new IllegalArgumentException("audio exceeds maxDuration");
        }
        pcm16Mono = pcm16Mono.clone();
    }

    @Override
    public byte[] pcm16Mono() {
        return pcm16Mono.clone();
    }

    /** Actual audio length, rounded up to the next nanosecond. */
    public Duration audioDuration() {
        return durationOf(pcm16Mono.length, sampleRateHz);
    }

    private static Duration durationOf(int bytes, int sampleRateHz) {
        long samples = bytes / 2;
        // Round up fractional nanoseconds so even a partial sample beyond the bound is rejected.
        return Duration.ofSeconds(samples / sampleRateHz)
                .plusNanos(((samples % sampleRateHz) * 1_000_000_000L + sampleRateHz - 1) / sampleRateHz);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SpeechRequest that && Arrays.equals(pcm16Mono, that.pcm16Mono)
                && sampleRateHz == that.sampleRateHz && languageTag.equals(that.languageTag)
                && correlationId.equals(that.correlationId) && maxDuration.equals(that.maxDuration);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(pcm16Mono) + Objects.hash(sampleRateHz, languageTag, correlationId, maxDuration);
    }

    @Override
    public String toString() {
        return "SpeechRequest[audio=<redacted>, metadata=<redacted>]";
    }
}
