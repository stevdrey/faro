// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import io.github.stevdrey.faro.common.Text;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Exact supported language tags and rates for the port's PCM16 mono format. */
public record AsrCapabilities(Set<Integer> sampleRatesHz, Set<String> languageTags,
                              Duration maxAudioDuration, boolean localProcessing) {
    public AsrCapabilities {
        sampleRatesHz = Set.copyOf(sampleRatesHz);
        languageTags = Set.copyOf(languageTags);
        if (sampleRatesHz.isEmpty() || sampleRatesHz.stream().anyMatch(rate -> rate <= 0)) {
            throw new IllegalArgumentException("sampleRatesHz must contain positive sample rates");
        }
        if (languageTags.isEmpty()) {
            throw new IllegalArgumentException("languageTags must not be empty");
        }
        languageTags.forEach(tag -> Text.requireNonBlank(tag, "languageTag"));
        Objects.requireNonNull(maxAudioDuration, "maxAudioDuration");
        if (maxAudioDuration.isNegative() || maxAudioDuration.isZero()) {
            throw new IllegalArgumentException("maxAudioDuration must be positive");
        }
    }

    public boolean supports(SpeechRequest request) {
        return sampleRatesHz.contains(request.sampleRateHz()) && languageTags.contains(request.languageTag())
                && request.maxDuration().compareTo(maxAudioDuration) <= 0;
    }
}
