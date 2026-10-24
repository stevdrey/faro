// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech;

import io.github.stevdrey.faro.common.Text;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Provider selection, inference timeout and optional service endpoint; no vendor-specific options. */
public record ProviderConfig(String provider, String language, Duration timeout, URI endpoint) {
    public ProviderConfig(String provider, String language, Duration timeout) {
        this(provider, language, timeout, null);
    }

    public ProviderConfig {
        Text.requireNonBlank(provider, "provider");
        language = LanguageTags.normalize(language, "language");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    @Override
    public String toString() {
        return "ProviderConfig[provider=" + provider + ", language=" + language + ", timeout=" + timeout
                + ", endpoint=<redacted>]";
    }
}
