// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import java.util.Objects;

/** Technical failure with a fixed, safe message. Never attach vendor messages, audio or transcripts. */
public final class SpeechFailure extends Exception {
    public enum Code { UNAVAILABLE, TIMEOUT, INCOMPATIBLE_REQUEST, PROVIDER_ERROR }

    private final Code code;

    public SpeechFailure(Code code) {
        super("Speech recognition failed: " + Objects.requireNonNull(code, "code"), null, false, true);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
