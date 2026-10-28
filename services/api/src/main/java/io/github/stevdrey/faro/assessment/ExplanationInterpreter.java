// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import java.util.List;

/** Extracts unconfirmed literal evidence; an empty list means insufficient evidence. */
public interface ExplanationInterpreter {
    enum Action { HAD, ADDED, REMOVED, REMAINED }

    /** All offsets are zero-based UTF-16 indices into the input; ends are exclusive. */
    record Evidence(Action action, int quantity, int phraseStart, int phraseEnd, int numberStart, int numberEnd) {
        @Override
        public String toString() { return "Evidence[redacted]"; }
    }

    List<Evidence> interpret(String transcript);
}
