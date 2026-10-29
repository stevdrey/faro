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

    /** Why an interpretation has, or lacks, evidence; it never says what the input contained. */
    enum Outcome { EXTRACTED, NOTHING_EXTRACTED, UNSUPPORTED }

    /** Evidence is present exactly when the outcome is {@link Outcome#EXTRACTED}. */
    record Interpretation(Outcome outcome, List<Evidence> evidence) {
        public Interpretation {
            Objects.requireNonNull(outcome, "outcome");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            if (evidence.isEmpty() == (outcome == Outcome.EXTRACTED)) {
                throw new IllegalArgumentException("Evidence must be present exactly when extracted");
            }
        }

        static Interpretation unsupported() { return new Interpretation(Outcome.UNSUPPORTED, List.of()); }
    }

    /** An unsupported part voids the whole input, which is distinct from an input with nothing to extract. */
    Interpretation interpretation(String transcript);

    default List<Evidence> interpret(String transcript) { return interpretation(transcript).evidence(); }

    /**
     * The value of a complete hypothesis that is one bare number, with only spaces or separators
     * around it. Like all evidence it is unconfirmed and never establishes a strategy.
     */
    default OptionalInt standaloneNumber(String transcript) {
        Objects.requireNonNull(transcript, "transcript");
        return OptionalInt.empty();
    }
}
