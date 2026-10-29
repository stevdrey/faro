// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Data-minimized provenance for the rules actually applied by the aggregator. */
public record AssessmentTrace(String version, List<Rule> rules) {
    public static final String VERSION = "faro-aggregation-1";
    /** Superseded versions must stay listed so that traces recorded under them remain readable. */
    public static final Set<String> SUPPORTED_VERSIONS = Set.of(VERSION);

    /** Categories, not identifiers or values of individual observations. */
    public enum Input { EXERCISE_OPERANDS, VISUAL_ANSWER, SPEECH_STATUS, EXTRACTED_CLAIMS,
        STANDALONE_NUMBER, CONFIRMED_SUBTRACTION_STEPS }

    /** The position a rule may take in an applied sequence. */
    public enum Stage { ACTION, EVIDENCE, STANDALONE, SUBTRACTION }

    /** Each stable ID names both the applied rule and its structured reason. */
    public enum Rule {
        ACTION_UNFINISHED("action.unfinished.v1", Stage.ACTION, Input.VISUAL_ANSWER),
        ACTION_CORRECT("action.correct.v1", Stage.ACTION, Input.EXERCISE_OPERANDS, Input.VISUAL_ANSWER),
        ACTION_INCORRECT("action.incorrect.v1", Stage.ACTION, Input.EXERCISE_OPERANDS, Input.VISUAL_ANSWER),
        SPEECH_UNAVAILABLE("speech.unavailable.v1", Stage.EVIDENCE, Input.SPEECH_STATUS),
        VISUAL_UNFINISHED("evidence.visual-unfinished.v1", Stage.EVIDENCE, Input.VISUAL_ANSWER, Input.SPEECH_STATUS),
        NO_CLAIMS("evidence.no-claims.v1", Stage.EVIDENCE,
                Input.VISUAL_ANSWER, Input.SPEECH_STATUS, Input.EXTRACTED_CLAIMS),
        UNSUPPORTED_SPEECH("evidence.unsupported-speech.v1", Stage.EVIDENCE,
                Input.VISUAL_ANSWER, Input.SPEECH_STATUS, Input.EXTRACTED_CLAIMS),
        ADDED_CONFLICT("evidence.added-conflict.v1", Stage.EVIDENCE,
                Input.VISUAL_ANSWER, Input.SPEECH_STATUS, Input.EXTRACTED_CLAIMS),
        COHERENT_ACCOUNT("evidence.coherent-account.v1", Stage.EVIDENCE,
                Input.EXERCISE_OPERANDS, Input.VISUAL_ANSWER, Input.SPEECH_STATUS, Input.EXTRACTED_CLAIMS),
        OTHER_ACCOUNT("evidence.other-account.v1", Stage.EVIDENCE,
                Input.EXERCISE_OPERANDS, Input.VISUAL_ANSWER, Input.SPEECH_STATUS, Input.EXTRACTED_CLAIMS),
        STANDALONE_CONFLICT("evidence.standalone-conflict.v1", Stage.STANDALONE,
                Input.VISUAL_ANSWER, Input.EXTRACTED_CLAIMS, Input.STANDALONE_NUMBER),
        STANDALONE_MATCH("evidence.standalone-match.v1", Stage.STANDALONE,
                Input.VISUAL_ANSWER, Input.EXTRACTED_CLAIMS, Input.STANDALONE_NUMBER),
        STANDALONE_ABSENT("evidence.standalone-absent.v1", Stage.STANDALONE,
                Input.EXTRACTED_CLAIMS, Input.STANDALONE_NUMBER),
        SUBTRACTION_INSUFFICIENT("subtraction.incomplete-or-invalid.v1", Stage.SUBTRACTION,
                Input.EXERCISE_OPERANDS, Input.CONFIRMED_SUBTRACTION_STEPS),
        SUBTRACTION_ERROR("subtraction.arithmetic-error.v1", Stage.SUBTRACTION,
                Input.EXERCISE_OPERANDS, Input.CONFIRMED_SUBTRACTION_STEPS),
        SUBTRACTION_COHERENT("subtraction.coherent.v1", Stage.SUBTRACTION,
                Input.EXERCISE_OPERANDS, Input.CONFIRMED_SUBTRACTION_STEPS);

        private final String id;
        private final Stage stage;
        private final List<Input> inputs;

        Rule(String id, Stage stage, Input... inputs) {
            this.id = id;
            this.stage = stage;
            this.inputs = List.of(inputs);
        }

        public String id() { return id; }
        public Stage stage() { return stage; }
        public List<Input> inputs() { return inputs; }
    }

    public AssessmentTrace {
        Objects.requireNonNull(version, "version");
        if (!SUPPORTED_VERSIONS.contains(version)) throw new IllegalArgumentException("Unsupported trace version");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        if (!isAppliedSequence(rules)) throw new IllegalArgumentException("Not a sequence the aggregator applies");
    }

    /** Stable rule IDs in execution order, suitable for {@link AssessmentResult#ruleIds()}. */
    public List<String> ruleIds() { return rules.stream().map(Rule::id).toList(); }

    static AssessmentTrace of(Rule... rules) { return new AssessmentTrace(VERSION, List.of(rules)); }

    AssessmentTrace then(Rule next) {
        var extended = new ArrayList<>(rules);
        extended.add(next);
        return new AssessmentTrace(version, extended);
    }

    // Accepts only the shapes the aggregator can produce, so a trace cannot claim an impossible decision.
    private static boolean isAppliedSequence(List<Rule> rules) {
        if (rules.size() == 1) return rules.getFirst().stage() == Stage.SUBTRACTION;
        if (rules.size() < 2 || rules.size() > 3) return false;
        var action = rules.get(0);
        var evidence = rules.get(1);
        if (action.stage() != Stage.ACTION || evidence.stage() != Stage.EVIDENCE) return false;
        if ((action == Rule.ACTION_UNFINISHED) != (evidence == Rule.VISUAL_UNFINISHED)
                && evidence != Rule.SPEECH_UNAVAILABLE) return false;
        if (evidence == Rule.COHERENT_ACCOUNT && action != Rule.ACTION_CORRECT) return false;
        if (rules.size() == 2) return true;
        return rules.get(2).stage() == Stage.STANDALONE
                && (evidence == Rule.NO_CLAIMS || evidence == Rule.UNSUPPORTED_SPEECH);
    }
}
