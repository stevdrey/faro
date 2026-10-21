// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

/** Installed adapter factory. Only the explicitly selected factory may create a recognizer. */
public interface AsrProvider {
    String id();

    /** Adapters must honor the configured inference timeout and normalize failures to SpeechFailure. */
    SpeechRecognizer create(ProviderConfig config) throws SpeechFailure;
}
