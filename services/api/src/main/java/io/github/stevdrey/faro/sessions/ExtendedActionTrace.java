// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import java.util.List;

/** An immutable trace that may also contain exchanges, with the same ordering rules as {@link ActionTrace}. */
public record ExtendedActionTrace(List<TraceEvent> events) {
    public ExtendedActionTrace {
        events = List.copyOf(events);
        TraceEvent.validateOrder(events);
    }
}
