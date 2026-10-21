// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.adapters;

import io.github.stevdrey.faro.speech.AsrCapabilities;
import io.github.stevdrey.faro.speech.SpeechFailure;
import io.github.stevdrey.faro.speech.SpeechHypothesis;
import io.github.stevdrey.faro.speech.SpeechRecognizer;
import io.github.stevdrey.faro.speech.SpeechRequest;
import io.github.stevdrey.faro.speech.SpeechStatus;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Deterministic synthetic adapter. Never analyzes, retains or logs input audio. */
public final class FakeSpeechRecognizer implements SpeechRecognizer {
    private static final AsrCapabilities CAPABILITIES = new AsrCapabilities(
            Set.of(16_000), Set.of("es-CR"), Duration.ofSeconds(30), true);

    private final SpeechHypothesis result;
    private final SpeechFailure.Code failure;

    public FakeSpeechRecognizer() {
        this(new SpeechHypothesis("", "es-CR", "fake", "synthetic", "1", 0, SpeechStatus.EMPTY));
    }

    public FakeSpeechRecognizer(SpeechHypothesis result) {
        this.result = Objects.requireNonNull(result, "result");
        if (!CAPABILITIES.languageTags().contains(result.languageTag()) || !result.engineId().equals("fake")) {
            throw new IllegalArgumentException("fake result must use supported language and fake engine");
        }
        if (result.status() == SpeechStatus.UNAVAILABLE) {
            throw new IllegalArgumentException("use SpeechFailure for unavailable adapter behavior");
        }
        this.failure = null;
    }

    public FakeSpeechRecognizer(SpeechFailure.Code failure) {
        this.failure = Objects.requireNonNull(failure, "failure");
        this.result = null;
    }

    @Override
    public SpeechHypothesis transcribe(SpeechRequest request) throws SpeechFailure {
        Objects.requireNonNull(request, "request");
        if (!CAPABILITIES.supports(request)) {
            throw new SpeechFailure(SpeechFailure.Code.INCOMPATIBLE_REQUEST);
        }
        if (failure != null) {
            throw new SpeechFailure(failure);
        }
        return result;
    }

    @Override
    public AsrCapabilities capabilities() {
        return CAPABILITIES;
    }
}
