package org.apache.seatunnel.web.api.scheduler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SchedulerTokenVerifierTest {

    @Test
    void verifyShouldRejectWhenTokenIsNotConfigured() {
        SchedulerTokenVerification result = verifier(true, "").verify("seatunnel-lab-token");

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(HttpStatus.FORBIDDEN, result.getHttpStatus());
        Assertions.assertEquals("scheduler api disabled", result.getMessage());
    }

    @Test
    void verifyShouldRejectWrongToken() {
        SchedulerTokenVerification result = verifier(true, "seatunnel-lab-token").verify("wrong");

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED, result.getHttpStatus());
        Assertions.assertTrue(result.isInvalidToken());
    }

    @Test
    void verifyShouldAcceptCorrectToken() {
        SchedulerTokenVerification result =
                verifier(true, "seatunnel-lab-token").verify("seatunnel-lab-token");

        Assertions.assertTrue(result.isAllowed());
        Assertions.assertEquals(HttpStatus.OK, result.getHttpStatus());
    }

    private SchedulerTokenVerifier verifier(boolean enabled, String token) {
        SchedulerProperties properties = new SchedulerProperties();
        properties.setEnabled(enabled);
        properties.setToken(token);
        return new SchedulerTokenVerifier(properties);
    }
}
