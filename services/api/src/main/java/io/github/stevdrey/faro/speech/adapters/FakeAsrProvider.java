// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.adapters;

import io.github.stevdrey.faro.speech.AsrProvider;
import io.github.stevdrey.faro.speech.ProviderConfig;
import io.github.stevdrey.faro.speech.SpeechRecognizer;

/** Factory for the deterministic, non-recognizing fake adapter. */
public final class FakeAsrProvider implements AsrProvider {
    public static final String ID = "fake";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean localProcessing() {
        return true;
    }

    @Override
    public SpeechRecognizer create(ProviderConfig config) {
        return new FakeSpeechRecognizer();
    }
}
