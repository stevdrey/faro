// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import java.util.Objects;

/** Pure replay of observable quantities. Does not solve or grade an exercise. */
public final class ActionReplay {
    private ActionReplay() {}

    /** Rejects invalid operations and integer overflow; never returns a partial result. */
    public static Result replay(int initialValue, ActionTrace trace) {
        Objects.requireNonNull(trace, "trace");
        int value = initialValue;
        boolean completed = false;
        for (ActionEvent event : trace.events()) {
            switch (event) {
                case ActionEvent.PlaceBlock e -> value = Math.addExact(value, e.value());
                case ActionEvent.RemoveBlock e -> {
                    if (e.value() > value) {
                        throw new IllegalArgumentException("Cannot remove more blocks than are available");
                    }
                    value -= e.value();
                }
                case ActionEvent.JumpLeft e -> value = Math.subtractExact(value, e.value());
                case ActionEvent.JumpRight e -> value = Math.addExact(value, e.value());
                case ActionEvent.DecomposeTen ignored -> throw new IllegalArgumentException("Use TensAndOnesReplay for exchanges");
                case ActionEvent.ComposeTen ignored -> throw new IllegalArgumentException("Use TensAndOnesReplay for exchanges");
                case ActionEvent.FinishAttempt ignored -> completed = true;
            }
        }
        return new Result(value, completed);
    }

    public record Result(int finalValue, boolean completed) {}
}
