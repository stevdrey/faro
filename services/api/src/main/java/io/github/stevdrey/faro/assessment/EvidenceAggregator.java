// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

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

    /** A null answer represents an unfinished visual attempt, not an incorrect answer. */
    public static Result missingAddend(ExerciseOperands.MissingAddend operands, Integer answer,
            SpeechStatus status, List<ExplanationInterpreter.Evidence> speech) {
        Objects.requireNonNull(operands, "operands");
        Objects.requireNonNull(status, "status");
        var claims = List.copyOf(Objects.requireNonNull(speech, "speech"));
        if (status == SpeechStatus.UNAVAILABLE && !claims.isEmpty()) {
            throw new IllegalArgumentException("Unavailable speech cannot contain evidence");
        }
        var action = answer == null ? ActionMathState.SIN_TERMINAR
                : MissingAddendMath.isCorrect(operands, answer) ? ActionMathState.CORRECTA : ActionMathState.INCORRECTA;
        if (status == SpeechStatus.UNAVAILABLE) return new Result(action, StrategyEvidenceState.NO_EVALUABLE);
        if (answer == null || claims.isEmpty()) return new Result(action, StrategyEvidenceState.INSUFICIENTE);

        // Only an explicit added quantity maps directly to the visual answer. Other
        // quantities may describe different objects and must not be compared to it.
        for (var claim : claims) {
            if (claim.action() == ExplanationInterpreter.Action.ADDED && claim.quantity() != answer) {
                return new Result(action, StrategyEvidenceState.CONFLICTO_ASR);
            }
        }
        // A coherent explanation requires the full, ordered literal account, not
        // merely an answer or a plausible isolated number.
        if (claims.size() == 3
                && matches(claims.get(0), ExplanationInterpreter.Action.HAD, operands.knownAddend())
                && matches(claims.get(1), ExplanationInterpreter.Action.ADDED, answer)
                && matches(claims.get(2), ExplanationInterpreter.Action.REMAINED, operands.total())
                && action == ActionMathState.CORRECTA) {
            return new Result(action, StrategyEvidenceState.COHERENTE);
        }
        return new Result(action, StrategyEvidenceState.INSUFICIENTE);
    }

    /** Interprets a complete hypothesis, including a standalone number as an answer claim. */
    public static Result missingAddendHypothesis(ExerciseOperands.MissingAddend operands, Integer answer,
            String transcript) {
        Objects.requireNonNull(transcript, "transcript");
        var claims = new SpanishExplanationInterpreter().interpret(transcript);
        var result = missingAddend(operands, answer, SpeechStatus.AVAILABLE, claims);
        if (answer == null || !claims.isEmpty() || transcript.length() > 16_000) return result;
        var normalized = SpanishNumberNormalizer.normalize(transcript);
        if (normalized.cues().isEmpty() && normalized.numbers().size() == 1) {
            var number = normalized.numbers().getFirst();
            // The complete hypothesis must be the number, with whitespace only around it.
            if (transcript.substring(0, number.start()).isBlank()
                    && transcript.substring(number.end()).isBlank() && number.value() != answer) {
                return new Result(result.actionMathState(), StrategyEvidenceState.CONFLICTO_ASR);
            }
        }
        return result;
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
        var result = SubtractionDecompositionMath.check(operands, confirmedSteps);
        if (result.strategy() != SubtractionDecompositionMath.Strategy.VALID) {
            return StrategyEvidenceState.INSUFICIENTE;
        }
        return result.stepArithmeticCorrect().contains(false)
                ? StrategyEvidenceState.ERROR_ARITMETICO_IDENTIFICABLE : StrategyEvidenceState.COHERENTE;
    }
}
