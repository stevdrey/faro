// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AssessmentResultTests {
    @Test
    void copiesRuleIdsWithoutReorderingOrNormalizingProvenance() {
        var ids = new ArrayList<>(List.of("synthetic.second.v1", "synthetic.first.v2"));
        var result = result(ids, "custom-version");
        ids.clear();
        assertThat(result.ruleIds()).containsExactly("synthetic.second.v1", "synthetic.first.v2");
        assertThat(result.assessmentVersion()).isEqualTo("custom-version");
        assertThatThrownBy(() -> result.ruleIds().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsEmptyRuleIds() {
        assertThat(result(List.of(), "synthetic-v1").ruleIds()).isEmpty();
    }

    @Test
    void requiresEveryStateAndTheRuleList() {
        assertThatThrownBy(() -> new AssessmentResult(null, StrategyEvidenceState.COHERENTE,
                NextAction.CONTINUAR, List.of(), "v1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AssessmentResult(ActionMathState.CORRECTA, null,
                NextAction.CONTINUAR, List.of(), "v1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AssessmentResult(ActionMathState.CORRECTA, StrategyEvidenceState.COHERENTE,
                null, List.of(), "v1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> result(null, "v1")).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsMissingOrBlankVersion(String version) {
        assertThatThrownBy(() -> result(List.of(), version))
                .isInstanceOf(version == null ? NullPointerException.class : IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsMissingOrBlankRuleId(String id) {
        assertThatThrownBy(() -> result(Arrays.asList("synthetic.valid.v1", id), "v1"))
                .isInstanceOf(id == null ? NullPointerException.class : IllegalArgumentException.class);
    }

    private static AssessmentResult result(List<String> ids, String version) {
        return new AssessmentResult(ActionMathState.CORRECTA, StrategyEvidenceState.COHERENTE,
                NextAction.CONTINUAR, ids, version);
    }
}
