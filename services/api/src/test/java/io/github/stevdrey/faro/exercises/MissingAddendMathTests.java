// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MissingAddendMathTests {
    @Test
    void acceptsExactlyOneAnswerForEveryCombinationThroughOneHundred() {
        for (int total = 0; total <= 100; total++) {
            for (int known = 0; known <= total; known++) {
                var operands = new ExerciseOperands.MissingAddend(total, known);
                List<Integer> accepted = new ArrayList<>();
                for (int answer = 0; answer <= 100; answer++) {
                    if (MissingAddendMath.isCorrect(operands, answer)) {
                        accepted.add(answer);
                    }
                }
                assertThat(accepted).as("%s + □ = %s", known, total).containsExactly(total - known);
            }
        }
    }

    @Test
    void handlesBoundariesAndRejectsNegativeAnswers() {
        assertThat(MissingAddendMath.isCorrect(new ExerciseOperands.MissingAddend(Integer.MAX_VALUE, 0), Integer.MAX_VALUE)).isTrue();
        assertThat(MissingAddendMath.isCorrect(new ExerciseOperands.MissingAddend(Integer.MAX_VALUE, Integer.MAX_VALUE), 0)).isTrue();
        assertThat(MissingAddendMath.isCorrect(new ExerciseOperands.MissingAddend(0, 0), -1)).isFalse();
        assertThat(MissingAddendMath.isCorrect(new ExerciseOperands.MissingAddend(18, 12), Integer.MIN_VALUE)).isFalse();
    }

    @Test
    void operandsRejectNegativeOrImpossibleValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ExerciseOperands.MissingAddend(0, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ExerciseOperands.MissingAddend(-1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new ExerciseOperands.MissingAddend(5, 8));
    }

    @Test
    void requiresOperands() {
        assertThatNullPointerException().isThrownBy(() -> MissingAddendMath.isCorrect(null, 6))
                .withMessage("operands");
    }
}
