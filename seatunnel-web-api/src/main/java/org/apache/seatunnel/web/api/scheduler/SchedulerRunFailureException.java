package org.apache.seatunnel.web.api.scheduler;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SchedulerRunFailureException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final SchedulerRunResponse response;

    public SchedulerRunFailureException(
            HttpStatus httpStatus,
            String message,
            SchedulerRunResponse response) {
        super(message);
        this.httpStatus = httpStatus;
        this.response = response;
    }
}
