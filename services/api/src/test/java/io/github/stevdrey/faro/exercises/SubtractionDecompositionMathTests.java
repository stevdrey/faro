// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import static io.github.stevdrey.faro.exercises.SubtractionDecompositionMath.*;
import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
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
    void rejectsNonpositivePartsAndExcessiveSumEvenWhenDisconnected() {
        for (int part : List.of(0, -1, Integer.MIN_VALUE)) {
            assertThat(check(operands, List.of(new Step(53, 20, 33), new Step(33, part, 33))).strategy())
                    .isEqualTo(Strategy.INVALID);
        }
        assertThat(check(operands, List.of(new Step(53, 20, 33), new Step(0, 9, -9))).strategy())
                .isEqualTo(Strategy.INVALID);
    }

    @Test
    void incompleteOrDisconnectedEvidenceIsInsufficient() {
        for (var steps : List.of(List.<Step>of(), List.of(new Step(53, 28, 25)),
                List.of(new Step(53, 20, 33), new Step(33, 7, 26)),
                List.of(new Step(52, 20, 32), new Step(32, 8, 24)),
                List.of(new Step(53, 20, 33), new Step(32, 8, 24)))) {
            assertThat(check(operands, steps).strategy()).isEqualTo(Strategy.INSUFFICIENT);
        }
        assertThat(check(new ExerciseOperands.Subtraction(53, 0), List.of()).strategy())
                .isEqualTo(Strategy.INSUFFICIENT);
        assertThat(check(new ExerciseOperands.Subtraction(53, 0), List.of(new Step(53, 0, 53))).strategy())
                .isEqualTo(Strategy.INSUFFICIENT);
    }

    @Test
    void doesNotWrapArithmeticOrPartSums() {
        var extreme = new ExerciseOperands.Subtraction(Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertThat(check(extreme, List.of(new Step(Integer.MIN_VALUE, 1, Integer.MAX_VALUE),
                new Step(Integer.MAX_VALUE, Integer.MAX_VALUE - 1, 1))))
                .isEqualTo(new Result(Strategy.VALID, List.of(false, true)));
        assertThat(check(extreme, List.of(new Step(0, Integer.MAX_VALUE, -Integer.MAX_VALUE),
                new Step(-Integer.MAX_VALUE, Integer.MAX_VALUE, 2))).strategy())
                .isEqualTo(Strategy.INVALID);
    }

    @Test
    void requiresInputsAndReturnsAnImmutableSnapshot() {
        assertThatNullPointerException().isThrownBy(() -> check(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> check(operands, null));
        assertThatNullPointerException().isThrownBy(() -> check(operands, Arrays.asList((Step) null)));
        var result = check(operands, List.of(new Step(53, 20, 33), new Step(33, 8, 25)));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> result.stepArithmeticCorrect().add(false));
    }
}
