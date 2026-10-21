// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

/** Application-owned ASR boundary. A transcript is an unconfirmed hypothesis, never a math judgment. */
public interface SpeechRecognizer {
    SpeechHypothesis transcribe(SpeechRequest request) throws SpeechFailure;

    AsrCapabilities capabilities();
}
