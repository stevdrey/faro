// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import static io.github.stevdrey.faro.assessment.AssessmentTrace.Rule.*;
import static io.github.stevdrey.faro.assessment.EvidenceAggregator.*;
import static org.assertj.core.api.Assertions.*;

import io.github.stevdrey.faro.exercises.ExerciseOperands;
import io.github.stevdrey.faro.exercises.SubtractionDecompositionMath.Step;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssessmentTraceTests {
    private final ExerciseOperands.MissingAddend operands = new ExerciseOperands.MissingAddend(18, 12);
    private final SpanishExplanationInterpreter interpreter = new SpanishExplanationInterpreter();

    private TracedResult assess(Integer answer, String text) {
        return missingAddendTraced(operands, answer, SpeechStatus.AVAILABLE, interpreter.interpret(text));
    }

    @Test void tracesEachActionAndEvidenceDecisionWithoutChangingResults() {
        assertThat(assess(null, "puse siete").trace().rules()).containsExactly(ACTION_UNFINISHED, VISUAL_UNFINISHED);
        assertThat(assess(7, "puse seis").trace().rules()).containsExactly(ACTION_INCORRECT, ADDED_CONFLICT);
        assertThat(assess(6, "puse siete").trace().rules()).containsExactly(ACTION_CORRECT, ADDED_CONFLICT);
        assertThat(assess(6, "porque").trace().rules()).containsExactly(ACTION_CORRECT, NO_CLAIMS);
        assertThat(assess(6, "puse seis").trace().rules()).containsExactly(ACTION_CORRECT, OTHER_ACCOUNT);
        assertThat(assess(6, "tenía doce, puse seis, quedaron dieciocho").trace().rules())
                .containsExactly(ACTION_CORRECT, COHERENT_ACCOUNT);
        var unavailable = missingAddendTraced(operands, 6, SpeechStatus.UNAVAILABLE, List.of());
        assertThat(unavailable.trace().rules()).containsExactly(ACTION_CORRECT, SPEECH_UNAVAILABLE);
        assertThat(unavailable.result()).isEqualTo(missingAddend(operands, 6, SpeechStatus.UNAVAILABLE, List.of()));
    }

    @Test void tracesStandaloneOverrideAndNonConflictWithoutTreatingItAsProof() {
        var conflict = missingAddendHypothesisTraced(interpreter, operands, 6, "siete");
        assertThat(conflict.trace().rules()).containsExactly(ACTION_CORRECT, NO_CLAIMS, STANDALONE_CONFLICT);
        assertThat(conflict.result().strategyEvidenceState()).isEqualTo(StrategyEvidenceState.CONFLICTO_ASR);
        for (String text : List.of("seis", "porque")) {
            var result = missingAddendHypothesisTraced(interpreter, operands, 6, text);
            assertThat(result.trace().rules()).containsExactly(ACTION_CORRECT, NO_CLAIMS, STANDALONE_NO_CONFLICT);
            assertThat(result.result().strategyEvidenceState()).isEqualTo(StrategyEvidenceState.INSUFICIENTE);
            assertThat(result.result()).isEqualTo(missingAddendHypothesis(interpreter, operands, 6, text));
        }
        assertThat(missingAddendHypothesisTraced(interpreter, operands, 6, "...").trace().rules())
                .containsExactly(ACTION_CORRECT, SPEECH_UNAVAILABLE);
        assertThat(missingAddendHypothesisTraced(interpreter, operands, null, "siete").trace().rules())
                .containsExactly(ACTION_UNFINISHED, VISUAL_UNFINISHED);
    }

    @Test void tracesConfirmedSubtractionBranches() {
        var operands = new ExerciseOperands.Subtraction(53, 28);
        var valid = List.of(new Step(53, 20, 33), new Step(33, 8, 25));
        var error = List.of(new Step(53, 20, 33), new Step(33, 8, 26));
        var incomplete = List.of(new Step(53, 28, 25));
        var disconnected = List.of(new Step(53, 20, 33), new Step(34, 8, 26));
        var invalid = List.of(new Step(53, 30, 23), new Step(23, 1, 22));
        assertThat(confirmedSubtractionStepsTraced(operands, valid).trace().rules()).containsExactly(SUBTRACTION_COHERENT);
        assertThat(confirmedSubtractionStepsTraced(operands, error).trace().rules()).containsExactly(SUBTRACTION_ERROR);
        for (var steps : List.of(incomplete, disconnected, invalid)) {
            var result = confirmedSubtractionStepsTraced(operands, steps);
            assertThat(result.trace().rules()).containsExactly(SUBTRACTION_INSUFFICIENT);
            assertThat(result.state()).isEqualTo(confirmedSubtractionSteps(operands, steps));
        }
    }

    @Test void traceIsDeterministicImmutableAndContainsNoPrivateInput() {
        String privateText = "puse siete secreto";
        var first = assess(6, privateText);
        assertThat(first).isEqualTo(assess(6, privateText));
        assertThat(first.toString()).doesNotContain(privateText, "secreto", "siete");
        assertThat(first.trace().assessmentVersion()).isEqualTo(AssessmentTrace.VERSION);
        assertThatThrownBy(() -> first.trace().rules().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ADDED_CONFLICT.inputs().clear()).isInstanceOf(UnsupportedOperationException.class);
        var rules = new ArrayList<>(List.of(ACTION_CORRECT));
        var trace = new AssessmentTrace(AssessmentTrace.VERSION, rules);
        rules.clear();
        assertThat(trace.rules()).containsExactly(ACTION_CORRECT);
        assertThat(ADDED_CONFLICT.id()).isEqualTo("evidence.added-conflict.v1");
        assertThat(ADDED_CONFLICT.inputs()).containsExactly(AssessmentTrace.Input.VISUAL_ANSWER, AssessmentTrace.Input.EXTRACTED_CLAIMS);
        assertThatThrownBy(() -> new AssessmentTrace("unknown", List.of(ACTION_CORRECT))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssessmentTrace(AssessmentTrace.VERSION, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(java.util.Arrays.stream(AssessmentTrace.Rule.values()).map(AssessmentTrace.Rule::id).distinct().count())
                .isEqualTo(AssessmentTrace.Rule.values().length);
    }
}
