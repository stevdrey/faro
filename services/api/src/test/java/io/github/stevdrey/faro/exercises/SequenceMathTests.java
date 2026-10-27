// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SequenceMathTests {
    @ParameterizedTest
    @ValueSource(ints = {-10, -5, -2, -1, 0, 1, 2, 5, 10, 3})
    void infersFixedDifferencesAndChecksAnswers(int step) {
        var twoTerms = new ExerciseOperands.Sequence(List.of(30, 30 + step));
        var multipleTerms = new ExerciseOperands.Sequence(List.of(30, 30 + step, 30 + 2 * step));
        assertThat(SequenceMath.solution(twoTerms)).isEqualTo(30 + 2 * step);
        assertThat(SequenceMath.isCorrect(multipleTerms, 30 + 3 * step)).isTrue();
        assertThat(SequenceMath.isCorrect(multipleTerms, 31 + 3 * step)).isFalse();
        assertThat(new ExerciseDefinition("sequence", multipleTerms, VisualModel.NUMBER_LINE,
                Difficulty.EASY, 30 + 3 * step).expectedResult()).isEqualTo(30 + 3 * step);
    }

    @Test
    void rejectsNonArithmeticAndIncorrectDefinitions() {
        var irregular = new ExerciseOperands.Sequence(List.of(1, 3, 5, 8));
        assertThatIllegalArgumentException().isThrownBy(() -> SequenceMath.solution(irregular));
        assertThat(SequenceMath.isCorrect(irregular, 11)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new ExerciseDefinition(
                "sequence", irregular, VisualModel.NUMBER_LINE, Difficulty.EASY, 10));
        var valid = new ExerciseOperands.Sequence(List.of(30, 28));
        assertThatIllegalArgumentException().isThrownBy(() -> new ExerciseDefinition(
                "sequence", valid, VisualModel.NUMBER_LINE, Difficulty.EASY, 27));
    }

    @Test
    void checksIntegerBoundariesWithoutWraparound() {
        assertThat(SequenceMath.solution(new ExerciseOperands.Sequence(
                List.of(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1)))).isEqualTo(Integer.MAX_VALUE);
        assertThat(SequenceMath.solution(new ExerciseOperands.Sequence(
                List.of(Integer.MIN_VALUE + 2, Integer.MIN_VALUE + 1)))).isEqualTo(Integer.MIN_VALUE);
        for (var terms : List.of(List.of(Integer.MAX_VALUE - 1, Integer.MAX_VALUE),
                List.of(Integer.MIN_VALUE + 1, Integer.MIN_VALUE),
                List.of(Integer.MIN_VALUE, Integer.MAX_VALUE),
                List.of(Integer.MAX_VALUE, Integer.MIN_VALUE))) {
            var operands = new ExerciseOperands.Sequence(terms);
            assertThatIllegalArgumentException().isThrownBy(() -> SequenceMath.solution(operands));
            assertThatIllegalArgumentException().isThrownBy(() -> new ExerciseDefinition(
                    "sequence", operands, VisualModel.NUMBER_LINE, Difficulty.EASY, 0));
            for (int answer : List.of(Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE)) {
                assertThat(SequenceMath.isCorrect(operands, answer)).isFalse();
            }
        }
        var wrapping = new ExerciseOperands.Sequence(List.of(Integer.MIN_VALUE, 0, Integer.MIN_VALUE));
        assertThatIllegalArgumentException().isThrownBy(() -> SequenceMath.solution(wrapping));
        assertThat(SequenceMath.isCorrect(wrapping, 0)).isFalse();
    }

    @Test
    void requiresOperands() {
        assertThatNullPointerException().isThrownBy(() -> SequenceMath.solution(null)).withMessage("operands");
        assertThatNullPointerException().isThrownBy(() -> SequenceMath.isCorrect(null, 0)).withMessage("operands");
    }
}
