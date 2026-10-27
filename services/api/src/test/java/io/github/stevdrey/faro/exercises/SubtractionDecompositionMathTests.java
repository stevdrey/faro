// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import static io.github.stevdrey.faro.exercises.SubtractionDecompositionMath.*;
import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SubtractionDecompositionMathTests {
    private final ExerciseOperands.Subtraction operands = new ExerciseOperands.Subtraction(53, 28);

    @Test
    void acceptsDifferentPositiveDecompositions() {
        assertThat(check(operands, List.of(new Step(53, 20, 33), new Step(33, 8, 25))))
                .isEqualTo(new Result(Strategy.VALID, List.of(true, true)));
        assertThat(check(operands, List.of(new Step(53, 8, 45), new Step(45, 20, 25))).strategy())
                .isEqualTo(Strategy.VALID);
        assertThat(check(operands, List.of(new Step(53, 10, 43), new Step(43, 10, 33),
                new Step(33, 8, 25))).strategy()).isEqualTo(Strategy.VALID);
    }

    @Test
    void distinguishesArithmeticErrorsFromAValidStrategy() {
        assertThat(check(operands, List.of(new Step(53, 20, 33), new Step(33, 8, 26))))
                .isEqualTo(new Result(Strategy.VALID, List.of(true, false)));
        assertThat(check(operands, List.of(new Step(53, 20, 34), new Step(34, 8, 26))))
                .isEqualTo(new Result(Strategy.VALID, List.of(false, true)));
        assertThat(check(operands, List.of(new Step(53, 20, 34), new Step(33, 8, 25))))
                .isEqualTo(new Result(Strategy.INSUFFICIENT, List.of(false, true)));
    }

    @Test
    void rejectsNonpositivePartsAndExcessiveSumInAConnectedChain() {
        for (int part : List.of(0, -1, Integer.MIN_VALUE)) {
            assertThat(check(operands, List.of(new Step(53, 20, 33), new Step(33, part, 33))).strategy())
                    .isEqualTo(Strategy.INVALID);
        }
        assertThat(check(operands, List.of(new Step(53, 20, 33), new Step(33, 9, 24))).strategy())
                .isEqualTo(Strategy.INVALID);
        assertThat(check(operands, List.of(new Step(53, 30, 23))).strategy())
                .isEqualTo(Strategy.INVALID);
    }

    @Test
    void incompleteOrDisconnectedEvidenceIsInsufficient() {
        for (var steps : List.of(List.<Step>of(), List.of(new Step(53, 28, 25)),
                List.of(new Step(53, 20, 33), new Step(33, 7, 26)),
                List.of(new Step(52, 20, 32), new Step(32, 8, 24)),
                List.of(new Step(53, 20, 33), new Step(32, 8, 24)),
                List.of(new Step(53, 20, 33), new Step(0, 9, -9)),
                List.of(new Step(53, 20, 33), new Step(32, 0, 32)))) {
            assertThat(check(operands, steps).strategy()).isEqualTo(Strategy.INSUFFICIENT);
        }
        assertThat(check(new ExerciseOperands.Subtraction(53, 0), List.of()).strategy())
                .isEqualTo(Strategy.INSUFFICIENT);
        assertThat(check(new ExerciseOperands.Subtraction(53, 0), List.of(new Step(53, 0, 53))).strategy())
                .isEqualTo(Strategy.INSUFFICIENT);
    }

    @Test
    void doesNotWrapArithmeticOrPartSums() {
        var extreme = new ExerciseOperands.Subtraction(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThat(check(extreme, List.of(new Step(Integer.MAX_VALUE, 1, Integer.MAX_VALUE - 1),
                new Step(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1, 0))))
                .isEqualTo(new Result(Strategy.VALID, List.of(true, true)));
        assertThat(check(extreme, List.of(new Step(Integer.MAX_VALUE, Integer.MAX_VALUE, 0),
                new Step(0, Integer.MAX_VALUE, -Integer.MAX_VALUE))).strategy())
                .isEqualTo(Strategy.INVALID);
        assertThat(check(operands, List.of(new Step(Integer.MIN_VALUE, 1, Integer.MAX_VALUE)))
                .stepArithmeticCorrect()).containsExactly(false);
    }

    @Test
    void rejectsOperandsOutsideTheCountContract() {
        for (int[] invalid : new int[][] {{53, -5}, {-1, 0}, {5, 28}}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ExerciseOperands.Subtraction(invalid[0], invalid[1]));
        }
        assertThatNoException().isThrownBy(() -> new ExerciseOperands.Subtraction(0, 0));
        assertThatNoException()
                .isThrownBy(() -> new ExerciseOperands.Subtraction(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void limitsTheNumberOfSteps() {
        var twenty = new ExerciseOperands.Subtraction(53, MAX_STEPS);
        assertThat(check(twenty, ones(53, MAX_STEPS)).strategy()).isEqualTo(Strategy.VALID);
        assertThatIllegalArgumentException().isThrownBy(() -> check(twenty, ones(53, MAX_STEPS + 1)));
    }

    @Test
    void requiresInputsAndReturnsAnImmutableSnapshot() {
        assertThatNullPointerException().isThrownBy(() -> check(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> check(operands, null));
        assertThatNullPointerException().isThrownBy(() -> check(operands, Arrays.asList((Step) null)));
        assertThatNullPointerException().isThrownBy(() -> new Result(Strategy.VALID, null))
                .withMessage("stepArithmeticCorrect");
        var result = check(operands, List.of(new Step(53, 20, 33), new Step(33, 8, 25)));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> result.stepArithmeticCorrect().add(false));
    }

    private static List<Step> ones(int start, int count) {
        return IntStream.range(0, count).mapToObj(i -> new Step(start - i, 1, start - i - 1)).toList();
    }
}
