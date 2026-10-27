// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import java.util.List;
import java.util.Objects;

/** Checks explicit subtraction steps without interpreting or correcting the evidence. */
public final class SubtractionDecompositionMath {
    private SubtractionDecompositionMath() {}

    public enum Strategy { VALID, INVALID, INSUFFICIENT }

    /** One explicitly stated equation: minuend - part = result. */
    public record Step(int minuend, int part, int result) {}

    /** Arithmetic checks correspond to the supplied steps in their original order. */
    public record Result(Strategy strategy, List<Boolean> stepArithmeticCorrect) {
        public Result {
            Objects.requireNonNull(strategy, "strategy");
            stepArithmeticCorrect = List.copyOf(stepArithmeticCorrect);
        }
    }

    /**
     * Checks each equation independently, and checks the decomposition using the declared
     * results as links. Incomplete or disconnected evidence is not an invalid strategy.
     */
    public static Result check(ExerciseOperands.Subtraction operands, List<Step> steps) {
        Objects.requireNonNull(operands, "operands");
        var supplied = List.copyOf(Objects.requireNonNull(steps, "steps"));
        var arithmetic = supplied.stream()
                .map(step -> (long) step.minuend() - step.part() == step.result())
                .toList();

        if (operands.subtrahend() == 0) {
            return new Result(Strategy.INSUFFICIENT, arithmetic);
        }

        long sum = 0;
        boolean connected = true;
        int previous = operands.minuend();
        for (var step : supplied) {
            if (step.part() <= 0) {
                return new Result(Strategy.INVALID, arithmetic);
            }
            sum += step.part();
            if (sum > operands.subtrahend()) {
                return new Result(Strategy.INVALID, arithmetic);
            }
            connected &= step.minuend() == previous;
            previous = step.result();
        }

        var strategy = supplied.size() >= 2 && connected && sum == operands.subtrahend()
                ? Strategy.VALID : Strategy.INSUFFICIENT;
        return new Result(strategy, arithmetic);
    }
}
