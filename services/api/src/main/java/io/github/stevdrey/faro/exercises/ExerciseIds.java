// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import io.github.stevdrey.faro.common.Text;

/** Shared identifier rule for the domain definition and its public projection. */
public final class ExerciseIds {
    private ExerciseIds() {}

    public static String requireValid(String exerciseId) {
        return Text.requireNonBlank(exerciseId, "exerciseId");
    }
}
