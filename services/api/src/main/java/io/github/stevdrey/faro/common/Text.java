// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.common;

import java.util.Objects;

/** Shared nonblank rule for opaque identifiers; values are preserved as supplied. */
public final class Text {
    private Text() {}

    public static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
