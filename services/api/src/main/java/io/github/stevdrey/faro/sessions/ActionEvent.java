// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import java.time.Instant;

/** Semantic actions, independent of UI coordinates, transport, and assessment rules. */
public sealed interface ActionEvent extends TraceEvent {
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
            TraceEvent.validateMetadata(step, occurredAt);
        }
    }

    private static void validateMovement(int value, int step, Instant occurredAt) {
        if (value <= 0) {
            throw new IllegalArgumentException("Movement value must be positive");
        }
        TraceEvent.validateMetadata(step, occurredAt);
    }
}
