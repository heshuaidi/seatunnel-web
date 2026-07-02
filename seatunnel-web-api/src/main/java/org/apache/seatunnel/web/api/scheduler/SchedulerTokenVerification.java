package org.apache.seatunnel.web.api.scheduler;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SchedulerTokenVerification {

    private final boolean allowed;
    private final HttpStatus httpStatus;
    private final String message;
    private final boolean invalidToken;

    private SchedulerTokenVerification(
            boolean allowed,
            HttpStatus httpStatus,
            String message,
            boolean invalidToken) {
        this.allowed = allowed;
        this.httpStatus = httpStatus;
        this.message = message;
        this.invalidToken = invalidToken;
    }

    public static SchedulerTokenVerification ok() {
        return new SchedulerTokenVerification(true, HttpStatus.OK, "ok", false);
    }

    public static SchedulerTokenVerification disabled() {
        return new SchedulerTokenVerification(
                false,
                HttpStatus.FORBIDDEN,
                "scheduler api disabled",
                false);
    }

    public static SchedulerTokenVerification missing() {
        return new SchedulerTokenVerification(
                false,
                HttpStatus.UNAUTHORIZED,
                "scheduler token missing",
                true);
    }

    public static SchedulerTokenVerification invalid() {
        return new SchedulerTokenVerification(
                false,
                HttpStatus.UNAUTHORIZED,
                "scheduler token invalid",
                true);
    }
}
