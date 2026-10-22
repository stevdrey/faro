// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import java.time.Instant;
import java.util.Objects;

/** Semantic actions, independent of UI coordinates, transport, and assessment rules. */
public sealed interface ActionEvent {
    int step();
    Instant occurredAt();

    record PlaceBlock(int value, int step, Instant occurredAt) implements ActionEvent {
        public PlaceBlock {
            validateMovement(value, step, occurredAt);
        }
    }

    record RemoveBlock(int value, int step, Instant occurredAt) implements ActionEvent {
        public RemoveBlock {
            validateMovement(value, step, occurredAt);
        }
    }

    record JumpLeft(int value, int step, Instant occurredAt) implements ActionEvent {
        public JumpLeft {
            validateMovement(value, step, occurredAt);
        }
    }

    record JumpRight(int value, int step, Instant occurredAt) implements ActionEvent {
        public JumpRight {
            validateMovement(value, step, occurredAt);
        }
    }

    record FinishAttempt(int step, Instant occurredAt) implements ActionEvent {
        public FinishAttempt {
            validateMetadata(step, occurredAt);
        }
    }

    record DecomposeTen(int step, Instant occurredAt) implements ActionEvent {
        public DecomposeTen { validateMetadata(step, occurredAt); }
    }

    record ComposeTen(int step, Instant occurredAt) implements ActionEvent {
        public ComposeTen { validateMetadata(step, occurredAt); }
    }

    private static void validateMovement(int value, int step, Instant occurredAt) {
        if (value <= 0) {
            throw new IllegalArgumentException("Movement value must be positive");
        }
        validateMetadata(step, occurredAt);
    }

    private static void validateMetadata(int step, Instant occurredAt) {
        if (step <= 0) {
            throw new IllegalArgumentException("Event step must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
