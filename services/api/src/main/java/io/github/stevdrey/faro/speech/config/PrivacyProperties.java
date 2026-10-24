// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.stevdrey.faro.speech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** MVP privacy policy. Configuration cannot opt into cloud processing. */
@ConfigurationProperties("faro.privacy")
public record PrivacyProperties(@DefaultValue("LOCAL_ONLY") String policy) {
    public PrivacyProperties {
        if (!"LOCAL_ONLY".equals(policy)) {
            throw new IllegalArgumentException("faro.privacy.policy must be LOCAL_ONLY");
        }
    }
}
