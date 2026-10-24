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
        if (endpoint != null && !isLoopbackEndpoint(endpoint)) {
            // Never echo the value: endpoints may contain credentials or other private configuration.
            throw new IllegalArgumentException("endpoint must be HTTP(S) on literal 127.0.0.1 or [::1]"
                    + " without credentials, query or fragment");
        }
    }

    /** LOCAL_ONLY endpoint rule: literal loopback only, so no DNS lookup can redirect the transport. */
    public static boolean isLoopbackEndpoint(URI endpoint) {
        var scheme = endpoint.getScheme();
        var host = endpoint.getHost();
        var port = endpoint.getPort();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && ("127.0.0.1".equals(host) || "[::1]".equals(host))
                && endpoint.getRawUserInfo() == null && endpoint.getRawQuery() == null
                && endpoint.getRawFragment() == null
                && (port == -1 || port >= 1 && port <= 65535);
    }

    @Override
    public String toString() {
        return "ProviderConfig[provider=" + provider + ", language=" + language + ", timeout=" + timeout
                + ", endpoint=" + (endpoint == null ? "<none>" : "<redacted>") + "]";
    }
}
