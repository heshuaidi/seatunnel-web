package org.apache.seatunnel.web.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.web.api.scheduler.SchedulerExecutorService;
import org.apache.seatunnel.web.api.scheduler.SchedulerRunFailureException;
import org.apache.seatunnel.web.api.scheduler.SchedulerRunRequest;
import org.apache.seatunnel.web.api.scheduler.SchedulerRunResponse;
import org.apache.seatunnel.web.api.scheduler.SchedulerTokenVerification;
import org.apache.seatunnel.web.api.scheduler.SchedulerTokenVerifier;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.spi.bean.entity.Result;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Tag(name = "SCHEDULER_EXECUTOR_TAG")
@RequestMapping("/api/v1/scheduler")
public class SchedulerExecutorController {

    private final SchedulerExecutorService schedulerExecutorService;
    private final SchedulerTokenVerifier schedulerTokenVerifier;

    public SchedulerExecutorController(
            SchedulerExecutorService schedulerExecutorService,
            SchedulerTokenVerifier schedulerTokenVerifier) {
        this.schedulerExecutorService = schedulerExecutorService;
        this.schedulerTokenVerifier = schedulerTokenVerifier;
    }

    @PostMapping("/job-defines/{jobDefineId}/run")
    @Operation(summary = "runJobByScheduler", description = "Run batch job by external scheduler token")
    public ResponseEntity<Result<SchedulerRunResponse>> runByPost(
            @PathVariable("jobDefineId") String jobDefineId,
            @RequestHeader(value = SchedulerTokenVerifier.HEADER_NAME, required = false) String token,
            @RequestBody(required = false) SchedulerRunRequest request,
            HttpServletRequest servletRequest) {
        return run(jobDefineId, token, request, servletRequest);
    }

    @GetMapping("/job-defines/{jobDefineId}/run")
    @Operation(summary = "runJobBySchedulerGet", description = "Run batch job by external scheduler token")
    public ResponseEntity<Result<SchedulerRunResponse>> runByGet(
            @PathVariable("jobDefineId") String jobDefineId,
            @RequestHeader(value = SchedulerTokenVerifier.HEADER_NAME, required = false) String token,
            HttpServletRequest servletRequest) {
        return run(jobDefineId, token, null, servletRequest);
    }

    @PostMapping("/job-defines/{jobDefineId}/run-sync")
    @Operation(summary = "runJobBySchedulerSync", description = "Run batch job and wait for final status")
    public ResponseEntity<Result<SchedulerRunResponse>> runSyncByPost(
            @PathVariable("jobDefineId") String jobDefineId,
            @RequestHeader(value = SchedulerTokenVerifier.HEADER_NAME, required = false) String token,
            @RequestBody(required = false) SchedulerRunRequest request,
            HttpServletRequest servletRequest) {
        SchedulerTokenVerification verification = verifyToken(token, servletRequest);
        if (!verification.isAllowed()) {
            return authFailure(verification);
        }

        try {
            Long parsedJobDefineId = parseJobDefineId(jobDefineId);
            return ResponseEntity.ok(Result.buildSuc(
                    schedulerExecutorService.runSync(parsedJobDefineId, request)));
        } catch (SchedulerRunFailureException e) {
            return schedulerRunFailure(e);
        } catch (ServiceException e) {
            return serviceFailure(e);
        } catch (Exception e) {
            log.error("scheduler run-sync failed, jobDefineId={}", jobDefineId, e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "scheduler run-sync failed: " + e.getMessage());
        }
    }

    @RequestMapping(
            value = {"/job-defines/run", "/job-defines/run-sync"},
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Result<SchedulerRunResponse>> missingJobDefineId(
            @RequestHeader(value = SchedulerTokenVerifier.HEADER_NAME, required = false) String token,
            HttpServletRequest servletRequest) {
        SchedulerTokenVerification verification = verifyToken(token, servletRequest);
        if (!verification.isAllowed()) {
            return authFailure(verification);
        }
        return failure(HttpStatus.BAD_REQUEST, "jobDefineId is required");
    }

    private ResponseEntity<Result<SchedulerRunResponse>> run(
            String jobDefineId,
            String token,
            SchedulerRunRequest request,
            HttpServletRequest servletRequest) {
        SchedulerTokenVerification verification = verifyToken(token, servletRequest);
        if (!verification.isAllowed()) {
            return authFailure(verification);
        }

        try {
            Long parsedJobDefineId = parseJobDefineId(jobDefineId);
            return ResponseEntity.ok(Result.buildSuc(
                    schedulerExecutorService.run(parsedJobDefineId, request)));
        } catch (ServiceException e) {
            return serviceFailure(e);
        } catch (Exception e) {
            log.error("scheduler run failed, jobDefineId={}", jobDefineId, e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "scheduler run failed: " + e.getMessage());
        }
    }

    private SchedulerTokenVerification verifyToken(String token, HttpServletRequest request) {
        SchedulerTokenVerification verification = schedulerTokenVerifier.verify(token);
        if (!verification.isAllowed()) {
            String remoteAddr = request == null ? "unknown" : request.getRemoteAddr();
            if (verification.isInvalidToken()) {
                log.warn("scheduler token invalid, remoteAddr={}", remoteAddr);
            } else {
                log.warn("scheduler api disabled, remoteAddr={}", remoteAddr);
            }
        }
        return verification;
    }

    private Long parseJobDefineId(String jobDefineId) {
        if (jobDefineId == null || jobDefineId.isBlank()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "jobDefineId");
        }
        try {
            Long parsed = Long.valueOf(jobDefineId.trim());
            if (parsed <= 0) {
                throw new NumberFormatException("jobDefineId must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "jobDefineId");
        }
    }

    private ResponseEntity<Result<SchedulerRunResponse>> schedulerRunFailure(
            SchedulerRunFailureException e) {
        SchedulerRunResponse response = e.getResponse();
        Result<SchedulerRunResponse> result = Result.buildFailure(
                e.getHttpStatus().value(),
                e.getMessage());
        result.setData(response);
        return ResponseEntity.status(e.getHttpStatus()).body(result);
    }

    private ResponseEntity<Result<SchedulerRunResponse>> serviceFailure(ServiceException e) {
        HttpStatus httpStatus = isParamError(e) ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        Result<SchedulerRunResponse> result = Result.buildFailure(e.getCode(), e.getMessage());
        return ResponseEntity.status(httpStatus).body(result);
    }

    private boolean isParamError(ServiceException e) {
        return e != null && e.getCode() == Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode();
    }

    private ResponseEntity<Result<SchedulerRunResponse>> authFailure(
            SchedulerTokenVerification verification) {
        return failure(verification.getHttpStatus(), verification.getMessage());
    }

    private ResponseEntity<Result<SchedulerRunResponse>> failure(
            HttpStatus httpStatus,
            String message) {
        return ResponseEntity
                .status(httpStatus)
                .body(Result.buildFailure(httpStatus.value(), message));
    }
}
