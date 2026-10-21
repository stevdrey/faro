// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import java.util.List;
import java.util.Objects;

/** Immutable, independent assessment axes and minimal decision provenance; no grading policy. */
public record AssessmentResult(
        ActionMathState actionMathState,
        StrategyEvidenceState strategyEvidenceState,
        NextAction nextAction,
        List<String> ruleIds,
        String assessmentVersion) {

    public AssessmentResult {
        Objects.requireNonNull(actionMathState, "actionMathState");
        Objects.requireNonNull(strategyEvidenceState, "strategyEvidenceState");
        Objects.requireNonNull(nextAction, "nextAction");
        ruleIds = List.copyOf(ruleIds);
        ruleIds.forEach(id -> requireNonBlank(id, "ruleId"));
        requireNonBlank(assessmentVersion, "assessmentVersion");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
