// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.sessions;

import java.util.Objects;

/** Reconstructs pieces in two trays, without solving or grading the exercise. */
public final class TensAndOnesReplay {
    private TensAndOnesReplay() {}

    public static Result replay(int initialValue, ExtendedActionTrace trace) {
        Objects.requireNonNull(trace, "trace");
        if (initialValue < 0) throw new IllegalArgumentException("Initial quantity must be nonnegative");
        int tens = initialValue / 10;
        int ones = initialValue % 10;
        int removedTens = 0;
        int removedOnes = 0;
        boolean completed = false;
        for (TraceEvent event : trace.events()) {
            switch (event) {
                case ActionEvent.RemoveBlock e -> {
                    requirePiece(e.value());
                    if (e.value() == 10) {
                        require(tens > 0);
                        tens--;
                        removedTens++;
                    } else {
                        require(ones > 0);
                        ones--;
                        removedOnes++;
                    }
                }
                case ActionEvent.PlaceBlock e -> {
                    requirePiece(e.value());
                    if (e.value() == 10) {
                        require(removedTens > 0);
                        removedTens--;
                        tens++;
                    } else {
                        require(removedOnes > 0);
                        removedOnes--;
                        ones++;
                    }
                }
                case ExchangeEvent.DecomposeTen ignored -> {
                    require(tens > 0);
                    tens--;
                    ones = Math.addExact(ones, 10);
                }
                case ExchangeEvent.ComposeTen ignored -> {
                    require(ones >= 10);
                    ones -= 10;
                    tens++;
                }
                case ActionEvent.FinishAttempt ignored -> completed = true;
                case ActionEvent.JumpLeft ignored -> throw new IllegalArgumentException("Jumps are not piece movements");
                case ActionEvent.JumpRight ignored -> throw new IllegalArgumentException("Jumps are not piece movements");
            }
        }
        return new Result(tens, ones, removedTens, removedOnes, completed);
    }

    private static void requirePiece(int value) {
        if (value != 1 && value != 10) throw new IllegalArgumentException("Move exactly one unit or ten");
    }

    private static void require(boolean available) {
        if (!available) throw new IllegalArgumentException("Not enough pieces in the source tray");
    }

    public record Result(int tens, int ones, int removedTens, int removedOnes, boolean completed) {
        public int remainingValue() { return Math.addExact(Math.multiplyExact(tens, 10), ones); }
        public int removedValue() { return Math.addExact(Math.multiplyExact(removedTens, 10), removedOnes); }
    }
}
