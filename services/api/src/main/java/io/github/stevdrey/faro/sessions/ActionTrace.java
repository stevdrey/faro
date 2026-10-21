// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import java.util.List;

/** An immutable trace in received order. Steps, rather than wall-clock time, define order. */
public record ActionTrace(List<ActionEvent> events) {
    public ActionTrace {
        events = List.copyOf(events);
        for (int index = 0; index < events.size(); index++) {
            ActionEvent event = events.get(index);
            if (event.step() != index + 1) {
                throw new IllegalArgumentException("Event steps must be consecutive from 1 in received order");
            }
            if (event instanceof ActionEvent.FinishAttempt && index != events.size() - 1) {
                throw new IllegalArgumentException("finishAttempt must be the last event");
            }
        }
    }
}
