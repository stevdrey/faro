// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment.api;

import io.github.stevdrey.faro.assessment.ActionMathState;
import io.github.stevdrey.faro.assessment.AssessmentResult;
import io.github.stevdrey.faro.assessment.NextAction;
import io.github.stevdrey.faro.assessment.StrategyEvidenceState;
import io.github.stevdrey.faro.common.Text;
import java.util.List;
import java.util.Objects;

/** Version 1 assessment transport, separate from the decision's assessmentVersion. */
public record AssessmentResultPayload(
        int schemaVersion,
        ActionMathState actionMathState,
        StrategyEvidenceState strategyEvidenceState,
        NextAction nextAction,
        List<String> ruleIds,
        String assessmentVersion) {
    public static final int SCHEMA_VERSION = 1;

    public AssessmentResultPayload {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported assessment schema version");
        }
        Objects.requireNonNull(actionMathState, "actionMathState");
        Objects.requireNonNull(strategyEvidenceState, "strategyEvidenceState");
        Objects.requireNonNull(nextAction, "nextAction");
        ruleIds = AssessmentResult.copyRuleIds(ruleIds);
        Text.requireNonBlank(assessmentVersion, "assessmentVersion");
    }

    public AssessmentResult toDomain() {
        return new AssessmentResult(actionMathState, strategyEvidenceState, nextAction, ruleIds, assessmentVersion);
    }

    public static AssessmentResultPayload from(AssessmentResult result) {
        Objects.requireNonNull(result, "result");
        return new AssessmentResultPayload(SCHEMA_VERSION, result.actionMathState(),
                result.strategyEvidenceState(), result.nextAction(), result.ruleIds(), result.assessmentVersion());
    }
}
