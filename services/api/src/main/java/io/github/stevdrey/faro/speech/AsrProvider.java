// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

/** Installed adapter factory. Only the explicitly selected factory may create a recognizer. */
public interface AsrProvider {
    String id();

    /**
     * Explicit opt-in, checked before create. Unknown/remote adapters are denied by default.
     * Local adapters must use only the validated config endpoint (or run in process), never follow
     * redirects, honor the timeout, and never download models or forward audio on failure.
     * Constructors/registration must not perform I/O; all initialization belongs in create.
     */
    default boolean localProcessing() { return false; }

    /** Adapters must normalize failures to SpeechFailure without retaining private data. */
    SpeechRecognizer create(ProviderConfig config) throws SpeechFailure;
}
