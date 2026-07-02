package org.apache.seatunnel.web.api.scheduler;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class SchedulerTokenVerifier {

    public static final String HEADER_NAME = "X-ST-SCHEDULER-TOKEN";

    private final SchedulerProperties properties;

    public SchedulerTokenVerifier(SchedulerProperties properties) {
        this.properties = properties;
    }

    public SchedulerTokenVerification verify(String providedToken) {
        String configuredToken = properties.getToken();
        if (!properties.isEnabled() || StringUtils.isBlank(configuredToken)) {
            return SchedulerTokenVerification.disabled();
        }

        if (StringUtils.isBlank(providedToken)) {
            return SchedulerTokenVerification.missing();
        }

        byte[] configuredBytes = configuredToken.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(configuredBytes, providedBytes)) {
            return SchedulerTokenVerification.invalid();
        }

        return SchedulerTokenVerification.ok();
    }
}
