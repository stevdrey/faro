// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import static io.github.stevdrey.faro.assessment.AssessmentTrace.Rule.*;

import io.github.stevdrey.faro.exercises.ExerciseOperands;
import io.github.stevdrey.faro.exercises.MissingAddendMath;
import io.github.stevdrey.faro.exercises.SubtractionDecompositionMath;
import java.util.List;
import java.util.Objects;

/** Pure, conservative aggregation; speech observations are always unconfirmed hypotheses. */
public final class EvidenceAggregator {
    private EvidenceAggregator() {}

    public enum SpeechStatus { AVAILABLE, UNAVAILABLE }

    /** No feedback policy or rule provenance is selected here. */
    public record Result(ActionMathState actionMathState, StrategyEvidenceState strategyEvidenceState) {}

    /** A decision and the rules applied to produce it. */
    public record Traced<T>(T value, AssessmentTrace trace) {
        public Traced {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(trace, "trace");
        }
    }

    /** A null answer represents an unfinished visual attempt, not an incorrect answer. */
    public static Result missingAddend(ExerciseOperands.MissingAddend operands, Integer answer,
            SpeechStatus status, List<ExplanationInterpreter.Evidence> speech) {
        return missingAddendTraced(operands, answer, status, speech).value();
    }

    public static Traced<Result> missingAddendTraced(ExerciseOperands.MissingAddend operands, Integer answer,
            SpeechStatus status, List<ExplanationInterpreter.Evidence> speech) {
        return missingAddendTraced(operands, answer, status, speech, NO_CLAIMS);
    }

    /** {@code emptyRule} records why available speech produced no claims. */
    private static Traced<Result> missingAddendTraced(ExerciseOperands.MissingAddend operands, Integer answer,
            SpeechStatus status, List<ExplanationInterpreter.Evidence> speech, AssessmentTrace.Rule emptyRule) {
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
        if (claims.isEmpty()) return traced(action, StrategyEvidenceState.INSUFICIENTE, actionRule, emptyRule);

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
        return missingAddendHypothesisTraced(interpreter, operands, answer, transcript).value();
    }

    public static Traced<Result> missingAddendHypothesisTraced(ExplanationInterpreter interpreter,
            ExerciseOperands.MissingAddend operands, Integer answer, String transcript) {
        Objects.requireNonNull(interpreter, "interpreter");
        Objects.requireNonNull(transcript, "transcript");
        // Without any letter or digit ("", " .", "...") nothing was said that could be evaluated.
        if (transcript.codePoints().noneMatch(Character::isLetterOrDigit)) {
            return missingAddendTraced(operands, answer, SpeechStatus.UNAVAILABLE, List.of());
        }
        var interpretation = interpreter.interpretation(transcript);
        var claims = interpretation.evidence();
        var emptyRule = interpretation.outcome() == ExplanationInterpreter.Outcome.UNSUPPORTED
                ? UNSUPPORTED_SPEECH : NO_CLAIMS;
        var result = missingAddendTraced(operands, answer, SpeechStatus.AVAILABLE, claims, emptyRule);
        if (answer == null || !claims.isEmpty()) return result;
        var number = interpreter.standaloneNumber(transcript);
        if (number.isEmpty()) return new Traced<>(result.value(), result.trace().then(STANDALONE_ABSENT));
        if (number.getAsInt() == answer) return new Traced<>(result.value(), result.trace().then(STANDALONE_MATCH));
        return new Traced<>(new Result(result.value().actionMathState(), StrategyEvidenceState.CONFLICTO_ASR),
                result.trace().then(STANDALONE_CONFLICT));
    }

    private static Traced<Result> traced(ActionMathState action, StrategyEvidenceState strategy,
            AssessmentTrace.Rule actionRule, AssessmentTrace.Rule evidenceRule) {
        return new Traced<>(new Result(action, strategy), AssessmentTrace.of(actionRule, evidenceRule));
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
        return confirmedSubtractionStepsTraced(operands, confirmedSteps).value();
    }

    public static Traced<StrategyEvidenceState> confirmedSubtractionStepsTraced(ExerciseOperands.Subtraction operands,
            List<SubtractionDecompositionMath.Step> confirmedSteps) {
        var result = SubtractionDecompositionMath.check(operands, confirmedSteps);
        if (result.strategy() != SubtractionDecompositionMath.Strategy.VALID) {
            return new Traced<>(StrategyEvidenceState.INSUFICIENTE, AssessmentTrace.of(SUBTRACTION_INSUFFICIENT));
        }
        boolean error = result.stepArithmeticCorrect().contains(false);
        return new Traced<>(error ? StrategyEvidenceState.ERROR_ARITMETICO_IDENTIFICABLE : StrategyEvidenceState.COHERENTE,
                AssessmentTrace.of(error ? SUBTRACTION_ERROR : SUBTRACTION_COHERENT));
    }
}
