// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import static io.github.stevdrey.faro.assessment.AssessmentTrace.Rule.*;

import io.github.stevdrey.faro.exercises.ExerciseOperands;
import io.github.stevdrey.faro.exercises.MissingAddendMath;
import io.github.stevdrey.faro.exercises.SubtractionDecompositionMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure, conservative aggregation; speech observations are always unconfirmed hypotheses. */
public final class EvidenceAggregator {
    private EvidenceAggregator() {}

    public enum SpeechStatus { AVAILABLE, UNAVAILABLE }

    /** No feedback policy or rule provenance is selected here. */
    public record Result(ActionMathState actionMathState, StrategyEvidenceState strategyEvidenceState) {}

    /** A result and the rules used to produce its two independent states. */
    public record TracedResult(Result result, AssessmentTrace trace) {
        public TracedResult {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(trace, "trace");
        }
    }

    /** A confirmed-subtraction evidence state and its applied rule. */
    public record TracedStrategy(StrategyEvidenceState state, AssessmentTrace trace) {
        public TracedStrategy {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(trace, "trace");
        }
    }

    /** A null answer represents an unfinished visual attempt, not an incorrect answer. */
    public static Result missingAddend(ExerciseOperands.MissingAddend operands, Integer answer,
            SpeechStatus status, List<ExplanationInterpreter.Evidence> speech) {
        return missingAddendTraced(operands, answer, status, speech).result();
    }

    public static TracedResult missingAddendTraced(ExerciseOperands.MissingAddend operands, Integer answer,
            SpeechStatus status, List<ExplanationInterpreter.Evidence> speech) {
        Objects.requireNonNull(operands, "operands");
        Objects.requireNonNull(status, "status");
        var claims = List.copyOf(Objects.requireNonNull(speech, "speech"));
        if (status == SpeechStatus.UNAVAILABLE && !claims.isEmpty()) {
            throw new IllegalArgumentException("Unavailable speech cannot contain evidence");
        }
        var action = answer == null ? ActionMathState.SIN_TERMINAR
                : MissingAddendMath.isCorrect(operands, answer) ? ActionMathState.CORRECTA : ActionMathState.INCORRECTA;
        var actionRule = switch (action) {
            case SIN_TERMINAR -> ACTION_UNFINISHED;
            case CORRECTA -> ACTION_CORRECT;
            case INCORRECTA -> ACTION_INCORRECT;
        };
        if (status == SpeechStatus.UNAVAILABLE) return traced(action, StrategyEvidenceState.NO_EVALUABLE, actionRule, SPEECH_UNAVAILABLE);
        if (answer == null) return traced(action, StrategyEvidenceState.INSUFICIENTE, actionRule, VISUAL_UNFINISHED);
        if (claims.isEmpty()) return traced(action, StrategyEvidenceState.INSUFICIENTE, actionRule, NO_CLAIMS);

        // Only explicit added quantities map directly to the visual answer, compared as a
        // total so that adding in steps (counting on) is not a conflict. Other quantities
        // may describe different objects and must not be compared to it.
        long added = 0;
        boolean hasAdded = false;
        for (var claim : claims) {
            if (claim.action() == ExplanationInterpreter.Action.ADDED) {
                added += claim.quantity();
                hasAdded = true;
            }
        }
        if (hasAdded && added != answer) return traced(action, StrategyEvidenceState.CONFLICTO_ASR, actionRule, ADDED_CONFLICT);
        // A coherent explanation requires the full, ordered literal account, not
        // merely an answer or a plausible isolated number.
        if (claims.size() == 3
                && matches(claims.get(0), ExplanationInterpreter.Action.HAD, operands.knownAddend())
                && claims.get(1).action() == ExplanationInterpreter.Action.ADDED
                && matches(claims.get(2), ExplanationInterpreter.Action.REMAINED, operands.total())
                && action == ActionMathState.CORRECTA) {
            return traced(action, StrategyEvidenceState.COHERENTE, actionRule, COHERENT_ACCOUNT);
        }
        return traced(action, StrategyEvidenceState.INSUFICIENTE, actionRule, OTHER_ACCOUNT);
    }

    /** Interprets a complete hypothesis, including a standalone number as an answer claim. */
    public static Result missingAddendHypothesis(ExplanationInterpreter interpreter,
            ExerciseOperands.MissingAddend operands, Integer answer, String transcript) {
        return missingAddendHypothesisTraced(interpreter, operands, answer, transcript).result();
    }

    public static TracedResult missingAddendHypothesisTraced(ExplanationInterpreter interpreter,
            ExerciseOperands.MissingAddend operands, Integer answer, String transcript) {
        Objects.requireNonNull(interpreter, "interpreter");
        Objects.requireNonNull(transcript, "transcript");
        // Without any letter or digit ("", " .", "...") nothing was said that could be evaluated.
        if (transcript.codePoints().noneMatch(Character::isLetterOrDigit)) {
            return missingAddendTraced(operands, answer, SpeechStatus.UNAVAILABLE, List.of());
        }
        var claims = interpreter.interpret(transcript);
        var result = missingAddendTraced(operands, answer, SpeechStatus.AVAILABLE, claims);
        if (answer == null || !claims.isEmpty()) return result;
        var number = interpreter.standaloneNumber(transcript);
        boolean conflict = number.isPresent() && number.getAsInt() != answer;
        var rules = new ArrayList<>(result.trace().rules());
        rules.add(conflict ? STANDALONE_CONFLICT : STANDALONE_NO_CONFLICT);
        return new TracedResult(new Result(result.result().actionMathState(), conflict
                ? StrategyEvidenceState.CONFLICTO_ASR : result.result().strategyEvidenceState()),
                new AssessmentTrace(AssessmentTrace.VERSION, rules));
    }

    private static TracedResult traced(ActionMathState action, StrategyEvidenceState strategy,
            AssessmentTrace.Rule actionRule, AssessmentTrace.Rule evidenceRule) {
        return new TracedResult(new Result(action, strategy), AssessmentTrace.of(actionRule, evidenceRule));
    }

    private static boolean matches(ExplanationInterpreter.Evidence evidence,
            ExplanationInterpreter.Action action, int quantity) {
        return evidence.action() == action && evidence.quantity() == quantity;
    }

    /**
     * Checks explicitly confirmed equations. Unconfirmed ASR alone must never be
     * passed as confirmed; disconnected or incomplete chains remain insufficient.
     */
    public static StrategyEvidenceState confirmedSubtractionSteps(ExerciseOperands.Subtraction operands,
            List<SubtractionDecompositionMath.Step> confirmedSteps) {
        return confirmedSubtractionStepsTraced(operands, confirmedSteps).state();
    }

    public static TracedStrategy confirmedSubtractionStepsTraced(ExerciseOperands.Subtraction operands,
            List<SubtractionDecompositionMath.Step> confirmedSteps) {
        var result = SubtractionDecompositionMath.check(operands, confirmedSteps);
        if (result.strategy() != SubtractionDecompositionMath.Strategy.VALID) {
            return new TracedStrategy(StrategyEvidenceState.INSUFICIENTE, AssessmentTrace.of(SUBTRACTION_INSUFFICIENT));
        }
        boolean error = result.stepArithmeticCorrect().contains(false);
        return new TracedStrategy(error ? StrategyEvidenceState.ERROR_ARITMETICO_IDENTIFICABLE : StrategyEvidenceState.COHERENTE,
                AssessmentTrace.of(error ? SUBTRACTION_ERROR : SUBTRACTION_COHERENT));
    }
}
