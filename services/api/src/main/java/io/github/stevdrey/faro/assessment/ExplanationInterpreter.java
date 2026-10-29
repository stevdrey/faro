// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Extracts unconfirmed literal evidence; an empty list means insufficient evidence. */
public interface ExplanationInterpreter {
    enum Action { HAD, ADDED, REMOVED, REMAINED }

    /** All offsets are zero-based UTF-16 indices into the input; ends are exclusive. */
    record Evidence(Action action, int quantity, int phraseStart, int phraseEnd, int numberStart, int numberEnd) {
        public Evidence {
            Objects.requireNonNull(action, "action");
            // Messages omit the values: they derive from a child's transcript.
            if (quantity < 0) throw new IllegalArgumentException("quantity must not be negative");
            if (phraseStart < 0 || numberStart < phraseStart || numberEnd <= numberStart || phraseEnd < numberEnd) {
                throw new IllegalArgumentException("ranges must satisfy phraseStart <= numberStart < numberEnd <= phraseEnd");
            }
        }

        @Override
        public String toString() { return "Evidence[redacted]"; }
    }

    List<Evidence> interpret(String transcript);

    /**
     * The value of a complete hypothesis that is one bare number, with only spaces or separators
     * around it. Like all evidence it is unconfirmed and never establishes a strategy.
     */
    default OptionalInt standaloneNumber(String transcript) {
        Objects.requireNonNull(transcript, "transcript");
        return OptionalInt.empty();
    }
}
