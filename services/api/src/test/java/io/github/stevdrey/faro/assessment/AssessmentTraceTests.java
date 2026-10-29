// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import static io.github.stevdrey.faro.assessment.AssessmentTrace.Rule.*;
import static io.github.stevdrey.faro.assessment.EvidenceAggregator.*;
import static org.assertj.core.api.Assertions.*;

import io.github.stevdrey.faro.exercises.ExerciseOperands;
import io.github.stevdrey.faro.exercises.SubtractionDecompositionMath.Step;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssessmentTraceTests {
    private final ExerciseOperands.MissingAddend operands = new ExerciseOperands.MissingAddend(18, 12);
    private final SpanishExplanationInterpreter interpreter = new SpanishExplanationInterpreter();

    private Traced<Result> assess(Integer answer, String text) {
        return missingAddendTraced(operands, answer, SpeechStatus.AVAILABLE, interpreter.interpret(text));
    }

    private Traced<Result> hypothesis(Integer answer, String text) {
        return missingAddendHypothesisTraced(interpreter, operands, answer, text);
    }

    @Test void tracesEachActionAndEvidenceDecisionWithoutChangingResults() {
        assertThat(assess(null, "puse siete").trace().rules()).containsExactly(ACTION_UNFINISHED, VISUAL_UNFINISHED);
        assertThat(assess(7, "puse seis").trace().rules()).containsExactly(ACTION_INCORRECT, ADDED_CONFLICT);
        assertThat(assess(6, "puse siete").trace().rules()).containsExactly(ACTION_CORRECT, ADDED_CONFLICT);
        assertThat(assess(6, "y luego").trace().rules()).containsExactly(ACTION_CORRECT, NO_CLAIMS);
        assertThat(assess(7, "y luego").trace().rules()).containsExactly(ACTION_INCORRECT, NO_CLAIMS);
        assertThat(assess(6, "puse seis").trace().rules()).containsExactly(ACTION_CORRECT, OTHER_ACCOUNT);
        assertThat(assess(7, "tenía doce, puse siete").trace().rules()).containsExactly(ACTION_INCORRECT, OTHER_ACCOUNT);
        assertThat(assess(6, "tenía doce, puse seis, quedaron dieciocho").trace().rules())
                .containsExactly(ACTION_CORRECT, COHERENT_ACCOUNT);
        var unavailable = missingAddendTraced(operands, 6, SpeechStatus.UNAVAILABLE, List.of());
        assertThat(unavailable.trace().rules()).containsExactly(ACTION_CORRECT, SPEECH_UNAVAILABLE);
        assertThat(unavailable.value()).isEqualTo(missingAddend(operands, 6, SpeechStatus.UNAVAILABLE, List.of()));
    }

    @Test void distinguishesUnsupportedSpeechFromNothingExtracted() {
        var unsupported = hypothesis(6, "tenía doce, creo que puse seis");
        assertThat(unsupported.trace().rules()).containsExactly(ACTION_CORRECT, UNSUPPORTED_SPEECH, STANDALONE_ABSENT);
        assertThat(unsupported.value().strategyEvidenceState()).isEqualTo(StrategyEvidenceState.INSUFICIENTE);
        assertThat(hypothesis(6, "y luego").trace().rules()).containsExactly(ACTION_CORRECT, NO_CLAIMS, STANDALONE_ABSENT);
    }

    @Test void tracesStandaloneOverrideMatchAndAbsenceWithoutTreatingThemAsProof() {
        record Case(Integer answer, String text, AssessmentTrace.Rule action, AssessmentTrace.Rule standalone,
                    StrategyEvidenceState state) {}
        for (var c : List.of(
                new Case(6, "siete", ACTION_CORRECT, STANDALONE_CONFLICT, StrategyEvidenceState.CONFLICTO_ASR),
                new Case(6, "seis", ACTION_CORRECT, STANDALONE_MATCH, StrategyEvidenceState.INSUFICIENTE),
                new Case(6, "porque", ACTION_CORRECT, STANDALONE_ABSENT, StrategyEvidenceState.INSUFICIENTE),
                new Case(7, "seis", ACTION_INCORRECT, STANDALONE_CONFLICT, StrategyEvidenceState.CONFLICTO_ASR),
                new Case(7, "siete", ACTION_INCORRECT, STANDALONE_MATCH, StrategyEvidenceState.INSUFICIENTE))) {
            var result = hypothesis(c.answer(), c.text());
            assertThat(result.trace().rules()).as(c.text()).containsExactly(c.action(), UNSUPPORTED_SPEECH, c.standalone());
            assertThat(result.value().strategyEvidenceState()).as(c.text()).isEqualTo(c.state());
            assertThat(result.value()).isEqualTo(missingAddendHypothesis(interpreter, operands, c.answer(), c.text()));
        }
        assertThat(hypothesis(6, "...").trace().rules()).containsExactly(ACTION_CORRECT, SPEECH_UNAVAILABLE);
        assertThat(hypothesis(null, "siete").trace().rules()).containsExactly(ACTION_UNFINISHED, VISUAL_UNFINISHED);
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
            assertThat(result.value()).isEqualTo(confirmedSubtractionSteps(operands, steps));
        }
    }

    @Test void everyCatalogRuleIsReachable() {
        var subtraction = new ExerciseOperands.Subtraction(53, 28);
        var emitted = EnumSet.noneOf(AssessmentTrace.Rule.class);
        for (var traced : List.of(assess(null, "puse siete"), assess(6, "puse siete"), assess(7, "y luego"),
                assess(6, "puse seis"), assess(6, "tenía doce, puse seis, quedaron dieciocho"),
                hypothesis(6, "..."), hypothesis(6, "siete"), hypothesis(6, "seis"), hypothesis(6, "porque"))) {
            emitted.addAll(traced.trace().rules());
        }
        for (var steps : List.of(List.of(new Step(53, 20, 33), new Step(33, 8, 25)),
                List.of(new Step(53, 20, 33), new Step(33, 8, 26)), List.of(new Step(53, 28, 25)))) {
            emitted.addAll(confirmedSubtractionStepsTraced(subtraction, steps).trace().rules());
        }
        assertThat(emitted).containsExactlyInAnyOrder(AssessmentTrace.Rule.values());
    }

    @Test void rejectsSequencesTheAggregatorCannotApply() {
        for (var rules : List.of(List.<AssessmentTrace.Rule>of(), List.of(ACTION_CORRECT),
                List.of(SUBTRACTION_COHERENT, ACTION_CORRECT), List.of(ACTION_CORRECT, SUBTRACTION_COHERENT),
                List.of(ACTION_CORRECT, COHERENT_ACCOUNT, STANDALONE_MATCH), List.of(ACTION_INCORRECT, COHERENT_ACCOUNT),
                List.of(ACTION_UNFINISHED, NO_CLAIMS), List.of(ACTION_CORRECT, VISUAL_UNFINISHED),
                List.of(ACTION_UNFINISHED, VISUAL_UNFINISHED, STANDALONE_ABSENT),
                List.of(ACTION_CORRECT, NO_CLAIMS, STANDALONE_ABSENT, STANDALONE_ABSENT),
                List.of(NO_CLAIMS, ACTION_CORRECT))) {
            assertThatIllegalArgumentException().as(rules.toString())
                    .isThrownBy(() -> new AssessmentTrace(AssessmentTrace.VERSION, rules));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentTrace("unknown", List.of(SUBTRACTION_COHERENT)));
        assertThatNullPointerException().isThrownBy(() -> new AssessmentTrace(null, List.of(SUBTRACTION_COHERENT)));
    }

    @Test void traceIsDeterministicImmutableAndStructurallyFreeOfPrivateInput() {
        String privateText = "puse siete secreto";
        var first = hypothesis(6, privateText);
        assertThat(first).isEqualTo(hypothesis(6, privateText));
        assertThat(first.toString()).doesNotContain(privateText, "secreto", "siete");
        assertThat(first.trace().version()).isEqualTo(AssessmentTrace.VERSION);
        assertThatThrownBy(() -> first.trace().rules().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ADDED_CONFLICT.inputs().clear()).isInstanceOf(UnsupportedOperationException.class);
        var rules = new ArrayList<>(List.of(SUBTRACTION_COHERENT));
        var trace = new AssessmentTrace(AssessmentTrace.VERSION, rules);
        rules.clear();
        assertThat(trace.rules()).containsExactly(SUBTRACTION_COHERENT);

        // Only a version string and a list of catalog enums can be stored, whatever the input.
        RecordComponent[] components = AssessmentTrace.class.getRecordComponents();
        assertThat(components).extracting(RecordComponent::getName).containsExactly("version", "rules");
        assertThat(components[0].getType()).isEqualTo(String.class);
        assertThat(((ParameterizedType) components[1].getGenericType()).getActualTypeArguments())
                .containsExactly(AssessmentTrace.Rule.class);
        assertThat(AssessmentTrace.Rule.class.isEnum() && AssessmentTrace.Input.class.isEnum()).isTrue();
    }

    @Test void catalogIdsAreStableUniqueAndMapToResultRuleIds() {
        assertThat(ADDED_CONFLICT.id()).isEqualTo("evidence.added-conflict.v1");
        assertThat(ADDED_CONFLICT.inputs()).containsExactly(AssessmentTrace.Input.VISUAL_ANSWER,
                AssessmentTrace.Input.SPEECH_STATUS, AssessmentTrace.Input.EXTRACTED_CLAIMS);
        assertThat(java.util.Arrays.stream(AssessmentTrace.Rule.values()).map(AssessmentTrace.Rule::id).distinct().count())
                .isEqualTo(AssessmentTrace.Rule.values().length);
        var trace = hypothesis(6, "siete").trace();
        assertThat(trace.ruleIds()).containsExactly("action.correct.v1", "evidence.unsupported-speech.v1",
                "evidence.standalone-conflict.v1");
        assertThat(AssessmentResult.copyRuleIds(trace.ruleIds())).isEqualTo(trace.ruleIds());
    }
}
