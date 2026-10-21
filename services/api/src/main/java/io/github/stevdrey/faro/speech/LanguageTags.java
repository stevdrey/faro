// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import io.github.stevdrey.faro.common.Text;
import java.util.Locale;

/** Canonical BCP 47 form, so tags compare case-insensitively (es-cr equals es-CR). No aliasing or fallback. */
public final class LanguageTags {
    private LanguageTags() {}

    public static String normalize(String tag, String name) {
        Text.requireNonBlank(tag, name);
        var normalized = Locale.forLanguageTag(tag).toLanguageTag();
        // forLanguageTag silently drops ill-formed subtags; anything beyond a case change is rejected.
        if (!normalized.equalsIgnoreCase(tag)) {
            throw new IllegalArgumentException(name + " must be a well-formed language tag");
        }
        return normalized;
    }
}
