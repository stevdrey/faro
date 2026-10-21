// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import io.github.stevdrey.faro.common.Text;
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
        ruleIds = copyRuleIds(ruleIds);
        Text.requireNonBlank(assessmentVersion, "assessmentVersion");
    }

    /** Validates and copies opaque rule identifiers without reordering or normalizing them. */
    public static List<String> copyRuleIds(List<String> ruleIds) {
        Objects.requireNonNull(ruleIds, "ruleIds");
        ruleIds.forEach(id -> Text.requireNonBlank(id, "ruleId"));
        return List.copyOf(ruleIds);
    }
}
