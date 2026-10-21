// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import io.github.stevdrey.faro.common.Text;
import java.time.Duration;
import java.util.Objects;

/** Provider selection and inference timeout; contains no endpoint or vendor-specific options. */
public record ProviderConfig(String provider, String language, Duration timeout) {
    public ProviderConfig {
        Text.requireNonBlank(provider, "provider");
        language = LanguageTags.normalize(language, "language");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
