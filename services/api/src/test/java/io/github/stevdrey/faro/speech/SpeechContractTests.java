// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SpeechContractTests {
    @Test
    void audioIsDefensivelyCopiedOnInputAndOutput() {
        var bytes = new byte[] {1, 2, 3, 4};
        var request = request(bytes, 16_000, Duration.ofSeconds(1));
        bytes[0] = 99;
        request.pcm16Mono()[1] = 99;
        assertThat(request.pcm16Mono()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void acceptsEmptyAudioAndExactDurationBoundary() {
        assertThat(request(new byte[0], 16_000, Duration.ofNanos(1)).pcm16Mono()).isEmpty();
        assertThat(request(new byte[32_000], 16_000, Duration.ofSeconds(1)).sampleRateHz()).isEqualTo(16_000);
        assertThatThrownBy(() -> request(new byte[32_002], 16_000, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("audio exceeds maxDuration");
    }

    @Test
    void durationCheckHandlesFractionalSamplesAndVeryLargeLimitsWithoutOverflow() {
        assertThatThrownBy(() -> request(new byte[2], 3, Duration.ofNanos(333_333_333)))
                .isInstanceOf(IllegalArgumentException.class);
        request(new byte[2], 3, Duration.ofNanos(333_333_334));
        request(new byte[2], Integer.MAX_VALUE, Duration.ofSeconds(Long.MAX_VALUE));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsInvalidSampleRates(int rate) {
        assertThatThrownBy(() -> request(new byte[0], rate, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPartialSamplesAndMissingRequestFields() {
        assertThatThrownBy(() -> request(new byte[1], 16_000, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(null, 16_000, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SpeechRequest(new byte[0], 16_000, " ", "synthetic", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SpeechRequest(new byte[0], 16_000, "es-CR", null, Duration.ofSeconds(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SpeechRequest(new byte[0], 16_000, "es-CR", "\t", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void durationsMustBePresentAndPositive() {
        for (var duration : new Duration[] {Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertThatThrownBy(() -> request(new byte[0], 16_000, duration))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ProviderConfig("fake", "es-CR", duration))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AsrCapabilities(Set.of(16_000), Set.of("es-CR"), duration, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> request(new byte[0], 16_000, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProviderConfig("fake", "es-CR", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AsrCapabilities(Set.of(16_000), Set.of("es-CR"), null, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void providerAndLanguageMustBePresent() {
        assertThatThrownBy(() -> new ProviderConfig(" ", "es-CR", Duration.ofSeconds(8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderConfig(null, "es-CR", Duration.ofSeconds(8)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProviderConfig("fake", "", Duration.ofSeconds(8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capabilitiesCopySetsAndRejectInvalidDeclarations() {
        var rates = new HashSet<>(Set.of(16_000));
        var languages = new HashSet<>(Set.of("es-CR"));
        var capabilities = new AsrCapabilities(rates, languages, Duration.ofSeconds(30), true);
        rates.clear();
        languages.clear();
        assertThat(capabilities.sampleRatesHz()).containsExactly(16_000);
        assertThat(capabilities.languageTags()).containsExactly("es-CR");
        assertThatThrownBy(() -> capabilities.languageTags().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> capabilities.sampleRatesHz().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AsrCapabilities(Set.of(), Set.of("es-CR"), Duration.ofSeconds(1), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsrCapabilities(Set.of(-1), Set.of("es-CR"), Duration.ofSeconds(1), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsrCapabilities(Set.of(16_000), Set.of(), Duration.ofSeconds(1), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsrCapabilities(Set.of(16_000), Set.of(" "), Duration.ofSeconds(1), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hypothesisPreservesTranscriptAndProvenanceWithoutInterpretingIt() {
        var hypothesis = hypothesis("  siete  ", SpeechStatus.TRANSCRIBED);
        assertThat(hypothesis.transcript()).isEqualTo("  siete  ");
        assertThat(hypothesis.languageTag()).isEqualTo("es-CR");
        assertThat(hypothesis.engineId()).isEqualTo("fake");
        assertThat(hypothesis.modelId()).isEqualTo("synthetic");
        assertThat(hypothesis.modelVersion()).isEqualTo("1");
        assertThat(hypothesis.latencyMs()).isZero();
        assertThat(hypothesis.status()).isEqualTo(SpeechStatus.TRANSCRIBED);
    }

    @Test
    void validatesHypothesisStatusAndMetadata() {
        hypothesis("", SpeechStatus.EMPTY);
        hypothesis("", SpeechStatus.UNRELIABLE);
        hypothesis("", SpeechStatus.UNAVAILABLE);
        assertThatThrownBy(() -> hypothesis(" ", SpeechStatus.TRANSCRIBED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hypothesis("synthetic", SpeechStatus.EMPTY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hypothesis("synthetic", SpeechStatus.UNAVAILABLE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hypothesis(null, SpeechStatus.EMPTY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> hypothesis("", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SpeechHypothesis("", "es-CR", "fake", "synthetic", "1", -1, SpeechStatus.EMPTY))
                .isInstanceOf(IllegalArgumentException.class);
        for (int field = 0; field < 4; field++) {
            var fields = new String[] {"es-CR", "fake", "synthetic", "1"};
            fields[field] = " ";
            assertThatThrownBy(() -> new SpeechHypothesis("", fields[0], fields[1], fields[2], fields[3], 0, SpeechStatus.EMPTY))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void stringRepresentationsRedactAudioTranscriptAndOpaqueMetadata() {
        var request = new SpeechRequest(new byte[] {83, 69}, 16_000, "es-CR", "synthetic-private-id", Duration.ofSeconds(1));
        assertThat(request.toString()).contains("<redacted>").doesNotContain("synthetic-private-id", "83", "69");
        assertThat(hypothesis("synthetic-private-transcript", SpeechStatus.TRANSCRIBED).toString())
                .contains("<redacted>").doesNotContain("synthetic-private-transcript");
    }

    @ParameterizedTest
    @EnumSource(SpeechFailure.Code.class)
    void failuresHaveOnlySafeCodesAndCannotAcquireVendorCauses(SpeechFailure.Code code) {
        var failure = new SpeechFailure(code);
        assertThat(failure.code()).isEqualTo(code);
        assertThat(failure.getMessage()).isEqualTo("Speech recognition failed: " + code);
        assertThat(failure.getCause()).isNull();
        assertThatThrownBy(() -> failure.initCause(new RuntimeException("synthetic-private-transcript")))
                .isInstanceOf(IllegalStateException.class);
        failure.addSuppressed(new RuntimeException("synthetic-private-transcript"));
        assertThat(failure.getSuppressed()).isEmpty();
    }

    private static SpeechRequest request(byte[] audio, int rate, Duration duration) {
        return new SpeechRequest(audio, rate, "es-CR", "synthetic-correlation", duration);
    }

    static SpeechHypothesis hypothesis(String text, SpeechStatus status) {
        return new SpeechHypothesis(text, "es-CR", "fake", "synthetic", "1", 0, status);
    }
}
