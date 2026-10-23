// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import java.util.List;

/** An immutable trace in received order. Steps, rather than wall-clock time, define order. */
public record ActionTrace(List<ActionEvent> events) {
    public ActionTrace {
        events = List.copyOf(events);
        TraceEvent.validateOrder(events);
    }
}
