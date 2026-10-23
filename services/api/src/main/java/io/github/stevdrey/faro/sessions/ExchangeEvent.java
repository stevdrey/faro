// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import java.time.Instant;

/** Structural tens-and-ones exchanges. They carry no magnitude and never enter a v1 {@link ActionTrace}. */
public sealed interface ExchangeEvent extends TraceEvent {
    record DecomposeTen(int step, Instant occurredAt) implements ExchangeEvent {
        public DecomposeTen { TraceEvent.validateMetadata(step, occurredAt); }
    }

    record ComposeTen(int step, Instant occurredAt) implements ExchangeEvent {
        public ComposeTen { TraceEvent.validateMetadata(step, occurredAt); }
    }
}
