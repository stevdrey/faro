// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Checks explicit subtraction steps without interpreting or correcting the evidence. */
public final class SubtractionDecompositionMath {
    public static final int MAX_STEPS = 20;

    private SubtractionDecompositionMath() {}

    public enum Strategy { VALID, INVALID, INSUFFICIENT }

    /** One explicitly stated equation: minuend - part = result. */
    public record Step(int minuend, int part, int result) {}

    /** Arithmetic checks correspond to the supplied steps in their original order. */
    public record Result(Strategy strategy, List<Boolean> stepArithmeticCorrect) {
        public Result {
            Objects.requireNonNull(strategy, "strategy");
            stepArithmeticCorrect = List.copyOf(
                    Objects.requireNonNull(stepArithmeticCorrect, "stepArithmeticCorrect"));
        }
    }

    /**
     * Checks each equation independently, and checks the decomposition using the declared
     * results as links. Incomplete or disconnected evidence is not an invalid strategy.
     */
    public static Result check(ExerciseOperands.Subtraction operands, List<Step> steps) {
        Objects.requireNonNull(operands, "operands");
        var supplied = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (supplied.size() > MAX_STEPS) {
            throw new IllegalArgumentException("A decomposition accepts at most " + MAX_STEPS + " steps");
        }

        var arithmetic = new ArrayList<Boolean>(supplied.size());
        long sum = 0;
        boolean connected = true;
        boolean invalid = false;
        int previous = operands.minuend();
        for (var step : supplied) {
            arithmetic.add((long) step.minuend() - step.part() == step.result());
            connected &= step.minuend() == previous;
            previous = step.result();
            sum += step.part();
            invalid |= step.part() <= 0 || sum > operands.subtrahend();
        }

        Strategy strategy;
        if (operands.subtrahend() == 0 || !connected) {
            strategy = Strategy.INSUFFICIENT;
        } else if (invalid) {
            strategy = Strategy.INVALID;
        } else {
            strategy = supplied.size() >= 2 && sum == operands.subtrahend()
                    ? Strategy.VALID : Strategy.INSUFFICIENT;
        }
        return new Result(strategy, arithmetic);
    }
}
