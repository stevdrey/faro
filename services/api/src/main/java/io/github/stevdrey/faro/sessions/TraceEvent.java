// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Any recorded event. Legacy {@link ActionEvent}s and {@link ExchangeEvent}s are separate
 * hierarchies, so scalar consumers cannot receive an exchange at compile time.
 */
public sealed interface TraceEvent permits ActionEvent, ExchangeEvent {
    int step();
    Instant occurredAt();

    static void validateMetadata(int step, Instant occurredAt) {
        if (step <= 0) {
            throw new IllegalArgumentException("Event step must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Steps, rather than wall-clock time, define order; a finish can only be last. */
    static void validateOrder(List<? extends TraceEvent> events) {
        for (int index = 0; index < events.size(); index++) {
            TraceEvent event = events.get(index);
            if (event.step() != index + 1) {
                throw new IllegalArgumentException("Event steps must be consecutive from 1 in received order");
            }
            if (event instanceof ActionEvent.FinishAttempt && index != events.size() - 1) {
                throw new IllegalArgumentException("finishAttempt must be the last event");
            }
        }
    }
}
