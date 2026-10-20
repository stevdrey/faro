// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.exercises;

import java.util.Objects;

/** Shared identifier rule for the domain definition and its public projection. */
public final class ExerciseIds {
    private ExerciseIds() {}

    public static String requireValid(String exerciseId) {
        Objects.requireNonNull(exerciseId, "exerciseId");
        if (exerciseId.isBlank()) {
            throw new IllegalArgumentException("exerciseId must not be blank");
        }
        return exerciseId;
    }
}
