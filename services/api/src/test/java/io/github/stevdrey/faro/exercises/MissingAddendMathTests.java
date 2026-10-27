// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class MissingAddendMathTests {
    @Test
    void checksEveryNonnegativeCombinationThroughOneHundred() {
        for (int total = 0; total <= 100; total++) {
            for (int known = 0; known <= 100; known++) {
                var operands = new ExerciseOperands.MissingAddend(total, known);
                for (int answer = 0; answer <= 100; answer++) {
                    assertThat(MissingAddendMath.isCorrect(operands, answer))
                            .as("%s + %s = %s", known, answer, total)
                            .isEqualTo(known + answer == total);
                }
            }
        }
    }

    @Test
    void handlesSignedBoundariesWithoutOverflow() {
        assertThat(MissingAddendMath.isCorrect(new ExerciseOperands.MissingAddend(0, -1), 1)).isTrue();
        assertThat(MissingAddendMath.isCorrect(new ExerciseOperands.MissingAddend(Integer.MIN_VALUE, Integer.MIN_VALUE), 0)).isTrue();
        assertThat(MissingAddendMath.isCorrect(new ExerciseOperands.MissingAddend(Integer.MAX_VALUE, Integer.MIN_VALUE), -1)).isFalse();
        assertThat(MissingAddendMath.isCorrect(new ExerciseOperands.MissingAddend(Integer.MIN_VALUE, Integer.MAX_VALUE), 1)).isFalse();
    }

    @Test
    void requiresOperands() {
        assertThatNullPointerException().isThrownBy(() -> MissingAddendMath.isCorrect(null, 6))
                .withMessage("operands");
    }
}
