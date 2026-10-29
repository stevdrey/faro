// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import java.util.List;
import java.util.Objects;

/** Data-minimized provenance for the rules actually applied by the aggregator. */
public record AssessmentTrace(String assessmentVersion, List<Rule> rules) {
    public static final String VERSION = "faro-aggregation-1";

    /** Categories, not identifiers or values of individual observations. */
    public enum Input { EXERCISE_OPERANDS, VISUAL_ANSWER, SPEECH_STATUS, EXTRACTED_CLAIMS,
        STANDALONE_NUMBER, CONFIRMED_SUBTRACTION_STEPS }

    /** Each stable ID names both the applied rule and its structured reason. */
    public enum Rule {
        ACTION_UNFINISHED("action.unfinished.v1", Input.VISUAL_ANSWER),
        ACTION_CORRECT("action.correct.v1", Input.EXERCISE_OPERANDS, Input.VISUAL_ANSWER),
        ACTION_INCORRECT("action.incorrect.v1", Input.EXERCISE_OPERANDS, Input.VISUAL_ANSWER),
        SPEECH_UNAVAILABLE("speech.unavailable.v1", Input.SPEECH_STATUS),
        VISUAL_UNFINISHED("evidence.visual-unfinished.v1", Input.VISUAL_ANSWER, Input.SPEECH_STATUS),
        NO_CLAIMS("evidence.no-claims.v1", Input.SPEECH_STATUS, Input.EXTRACTED_CLAIMS),
        ADDED_CONFLICT("evidence.added-conflict.v1", Input.VISUAL_ANSWER, Input.EXTRACTED_CLAIMS),
        COHERENT_ACCOUNT("evidence.coherent-account.v1", Input.EXERCISE_OPERANDS, Input.VISUAL_ANSWER, Input.EXTRACTED_CLAIMS),
        OTHER_ACCOUNT("evidence.other-account.v1", Input.EXERCISE_OPERANDS, Input.VISUAL_ANSWER, Input.EXTRACTED_CLAIMS),
        STANDALONE_CONFLICT("evidence.standalone-conflict.v1", Input.VISUAL_ANSWER, Input.STANDALONE_NUMBER),
        STANDALONE_NO_CONFLICT("evidence.standalone-no-conflict.v1", Input.VISUAL_ANSWER, Input.STANDALONE_NUMBER),
        SUBTRACTION_INSUFFICIENT("subtraction.incomplete-or-invalid.v1", Input.EXERCISE_OPERANDS, Input.CONFIRMED_SUBTRACTION_STEPS),
        SUBTRACTION_ERROR("subtraction.arithmetic-error.v1", Input.EXERCISE_OPERANDS, Input.CONFIRMED_SUBTRACTION_STEPS),
        SUBTRACTION_COHERENT("subtraction.coherent.v1", Input.EXERCISE_OPERANDS, Input.CONFIRMED_SUBTRACTION_STEPS);

        private final String id;
        private final List<Input> inputs;

        Rule(String id, Input... inputs) {
            this.id = id;
            this.inputs = List.of(inputs);
        }

        public String id() { return id; }
        public List<Input> inputs() { return inputs; }
    }

    public AssessmentTrace {
        Objects.requireNonNull(assessmentVersion, "assessmentVersion");
        if (!VERSION.equals(assessmentVersion)) throw new IllegalArgumentException("Unsupported assessment version");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        if (rules.isEmpty()) throw new IllegalArgumentException("At least one rule is required");
    }

    static AssessmentTrace of(Rule... rules) { return new AssessmentTrace(VERSION, List.of(rules)); }
}
