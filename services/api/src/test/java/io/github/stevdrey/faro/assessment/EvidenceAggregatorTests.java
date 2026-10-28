// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import static io.github.stevdrey.faro.assessment.EvidenceAggregator.*;
import static org.assertj.core.api.Assertions.*;

import io.github.stevdrey.faro.exercises.ExerciseOperands;
import io.github.stevdrey.faro.exercises.SubtractionDecompositionMath.Step;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceAggregatorTests {
    private final ExerciseOperands.MissingAddend operands = new ExerciseOperands.MissingAddend(18, 12);
    private final SpanishExplanationInterpreter interpreter = new SpanishExplanationInterpreter();

    private Result assess(Integer answer, String speech) {
        return missingAddend(operands, answer, SpeechStatus.AVAILABLE, interpreter.interpret(speech));
    }

    @Test void preservesVisualMathWhenSpeechConflictsOrIsVague() {
        assertThat(assess(6, "puse siete"))
                .isEqualTo(new Result(ActionMathState.CORRECTA, StrategyEvidenceState.CONFLICTO_ASR));
        assertThat(assess(6, "porque"))
                .isEqualTo(new Result(ActionMathState.CORRECTA, StrategyEvidenceState.INSUFICIENTE));
        assertThat(assess(7, "puse seis"))
                .isEqualTo(new Result(ActionMathState.INCORRECTA, StrategyEvidenceState.CONFLICTO_ASR));
        assertThat(assess(null, "puse siete").actionMathState()).isEqualTo(ActionMathState.SIN_TERMINAR);
    }

    @Test void requiresCompleteMatchingAccountAndNeverRepairsConflictingClaims() {
        assertThat(assess(6, "tenía doce, puse seis, quedaron dieciocho").strategyEvidenceState())
                .isEqualTo(StrategyEvidenceState.COHERENTE);
        for (String text : List.of("puse seis", "tenía siete", "tenía doce, puse seis, quedaron diecisiete",
                "tenía doce, puse seis, puse siete, quedaron dieciocho")) {
            assertThat(assess(6, text).strategyEvidenceState()).isNotEqualTo(StrategyEvidenceState.COHERENTE);
        }
        assertThat(assess(6, "puse seis, puse siete").strategyEvidenceState())
                .isEqualTo(StrategyEvidenceState.CONFLICTO_ASR);
    }

    @Test void standaloneNumberCanConflictButCannotProveAStrategy() {
        assertThat(missingAddendHypothesis(operands, 6, "siete").strategyEvidenceState())
                .isEqualTo(StrategyEvidenceState.CONFLICTO_ASR);
        assertThat(missingAddendHypothesis(operands, 6, "seis").strategyEvidenceState())
                .isEqualTo(StrategyEvidenceState.INSUFICIENTE);
        for (String text : List.of("no siete", "quizás siete", "siete u ocho", "dijo siete")) {
            assertThat(missingAddendHypothesis(operands, 6, text).strategyEvidenceState())
                    .isEqualTo(StrategyEvidenceState.INSUFICIENTE);
        }
    }

    @Test void unavailableSpeechIsDistinctFromAnUnrecognizedStatement() {
        assertThat(missingAddend(operands, 6, SpeechStatus.UNAVAILABLE, List.of()).strategyEvidenceState())
                .isEqualTo(StrategyEvidenceState.NO_EVALUABLE);
        assertThatIllegalArgumentException().isThrownBy(() -> missingAddend(operands, 6,
                SpeechStatus.UNAVAILABLE, interpreter.interpret("puse seis")));
        assertThatNullPointerException().isThrownBy(() -> missingAddend(operands, 6, null, List.of()));
    }

    @Test void blankHypothesesAreNotEvaluableAndPreserveVisualState() {
        for (String text : List.of("", " \t\n")) {
            assertThat(missingAddendHypothesis(operands, 6, text))
                    .isEqualTo(new Result(ActionMathState.CORRECTA, StrategyEvidenceState.NO_EVALUABLE));
            assertThat(missingAddendHypothesis(operands, 7, text))
                    .isEqualTo(new Result(ActionMathState.INCORRECTA, StrategyEvidenceState.NO_EVALUABLE));
            assertThat(missingAddendHypothesis(operands, null, text))
                    .isEqualTo(new Result(ActionMathState.SIN_TERMINAR, StrategyEvidenceState.NO_EVALUABLE));
        }
        assertThat(missingAddendHypothesis(operands, 6, "porque").strategyEvidenceState())
                .isEqualTo(StrategyEvidenceState.INSUFICIENTE);
    }

    @Test void onlyCompleteConnectedConfirmedEquationsIdentifyArithmeticError() {
        var subtraction = new ExerciseOperands.Subtraction(53, 28);
        assertThat(confirmedSubtractionSteps(subtraction, List.of(new Step(53, 20, 33), new Step(33, 8, 26))))
                .isEqualTo(StrategyEvidenceState.ERROR_ARITMETICO_IDENTIFICABLE);
        assertThat(confirmedSubtractionSteps(subtraction, List.of(new Step(53, 20, 33), new Step(33, 8, 25))))
                .isEqualTo(StrategyEvidenceState.COHERENTE);
        assertThat(confirmedSubtractionSteps(subtraction, List.of(new Step(53, 20, 34), new Step(33, 8, 25))))
                .isEqualTo(StrategyEvidenceState.INSUFICIENTE);
        assertThat(confirmedSubtractionSteps(subtraction, List.of(new Step(53, 28, 26))))
                .isEqualTo(StrategyEvidenceState.INSUFICIENTE);
    }
}
