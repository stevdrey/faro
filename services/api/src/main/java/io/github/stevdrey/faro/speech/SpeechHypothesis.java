// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import io.github.stevdrey.faro.common.Text;
import java.util.Objects;

/** Unconfirmed recognition plus technical provenance; contains no assessment or calibrated confidence. */
public record SpeechHypothesis(String transcript, String languageTag, String engineId,
                               String modelId, String modelVersion, long latencyMs, SpeechStatus status) {
    public SpeechHypothesis {
        Objects.requireNonNull(transcript, "transcript");
        languageTag = LanguageTags.normalize(languageTag, "languageTag");
        Text.requireNonBlank(engineId, "engineId");
        Text.requireNonBlank(modelId, "modelId");
        Text.requireNonBlank(modelVersion, "modelVersion");
        Objects.requireNonNull(status, "status");
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        if (status == SpeechStatus.TRANSCRIBED && transcript.isBlank()) {
            throw new IllegalArgumentException("TRANSCRIBED requires nonblank transcript");
        }
        if ((status == SpeechStatus.EMPTY || status == SpeechStatus.UNAVAILABLE) && !transcript.isBlank()) {
            throw new IllegalArgumentException("EMPTY and UNAVAILABLE require blank transcript");
        }
    }

    @Override
    public String toString() {
        return "SpeechHypothesis[transcript=<redacted>, metadata=<redacted>, status=" + status + "]";
    }
}
