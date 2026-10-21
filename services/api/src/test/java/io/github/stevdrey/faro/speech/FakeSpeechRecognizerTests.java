// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stevdrey.faro.speech.adapters.FakeSpeechRecognizer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FakeSpeechRecognizerTests {
    @Test
    void defaultIsEmptyForEmptyAndNonemptyAudio() throws SpeechFailure {
        SpeechRecognizer recognizer = new FakeSpeechRecognizer();
        for (var audio : new byte[][] {new byte[0], new byte[] {1, 2}}) {
            var result = recognizer.transcribe(request(audio, 16_000, "es-CR", Duration.ofSeconds(1)));
            assertThat(result.status()).isEqualTo(SpeechStatus.EMPTY);
            assertThat(result.transcript()).isEmpty();
            assertThat(result.engineId()).isEqualTo("fake");
        }
        assertThat(recognizer.capabilities().localProcessing()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SpeechStatus.class, names = {"TRANSCRIBED", "EMPTY", "UNRELIABLE"})
    void returnsConfiguredSyntheticHypothesisDeterministically(SpeechStatus status) throws SpeechFailure {
        var result = SpeechContractTests.hypothesis(status == SpeechStatus.EMPTY ? "" : "siete", status);
        SpeechRecognizer recognizer = new FakeSpeechRecognizer(result);
        var request = request(new byte[] {1, 2}, 16_000, "es-CR", Duration.ofSeconds(1));
        assertThat(recognizer.transcribe(request)).isSameAs(result);
        assertThat(recognizer.transcribe(request)).isSameAs(result);
    }

    @ParameterizedTest
    @EnumSource(SpeechFailure.Code.class)
    void reportsConfiguredFailureWithoutWaitingOrFallingBack(SpeechFailure.Code code) {
        SpeechRecognizer recognizer = new FakeSpeechRecognizer(code);
        var request = request(new byte[0], 16_000, "es-CR", Duration.ofSeconds(1));
        assertThatThrownBy(() -> recognizer.transcribe(request)).isInstanceOfSatisfying(SpeechFailure.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }

    @Test
    void rejectsUnsupportedRequestsBeforeReturningCannedResult() {
        var recognizer = new FakeSpeechRecognizer();
        for (var request : new SpeechRequest[] {
                request(new byte[0], 8_000, "es-CR", Duration.ofSeconds(1)),
                request(new byte[0], 16_000, "en-US", Duration.ofSeconds(1)),
                request(new byte[0], 16_000, "es-CR", Duration.ofSeconds(31))}) {
            assertThatThrownBy(() -> recognizer.transcribe(request)).isInstanceOfSatisfying(SpeechFailure.class,
                    failure -> assertThat(failure.code()).isEqualTo(SpeechFailure.Code.INCOMPATIBLE_REQUEST));
        }
    }

    @Test
    void doesNotMisrepresentAnUnavailableOrRealProviderAsFake() {
        assertThatThrownBy(() -> new FakeSpeechRecognizer(SpeechContractTests.hypothesis("", SpeechStatus.UNAVAILABLE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FakeSpeechRecognizer(new SpeechHypothesis("", "es-CR", "whisper-cpp",
                "synthetic", "1", 0, SpeechStatus.EMPTY))).isInstanceOf(IllegalArgumentException.class);
    }

    private static SpeechRequest request(byte[] audio, int rate, String language, Duration duration) {
        return new SpeechRequest(audio, rate, language, "synthetic-correlation", duration);
    }
}
