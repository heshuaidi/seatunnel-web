package org.apache.seatunnel.web.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.web.api.dolphinscheduler.DolphinSchedulerApiResponse;
import org.apache.seatunnel.web.api.dolphinscheduler.DolphinSchedulerSyncRunRequest;
import org.apache.seatunnel.web.api.dolphinscheduler.DolphinSchedulerSyncRunVO;
import org.apache.seatunnel.web.api.scheduler.SchedulerTokenVerification;
import org.apache.seatunnel.web.api.scheduler.SchedulerTokenVerifier;
import org.apache.seatunnel.web.api.service.SyncRunCoordinatorService;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.spi.bean.dto.RunTaskRequest;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/dolphinscheduler/sync")
public class DolphinSchedulerSyncController {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SyncRunCoordinatorService syncRunCoordinatorService;
    private final SchedulerTokenVerifier schedulerTokenVerifier;

    public DolphinSchedulerSyncController(
            SyncRunCoordinatorService syncRunCoordinatorService,
            SchedulerTokenVerifier schedulerTokenVerifier
    ) {
        this.syncRunCoordinatorService = syncRunCoordinatorService;
        this.schedulerTokenVerifier = schedulerTokenVerifier;
    }

    @PostMapping("/tasks/{taskCode}/run")
    public ResponseEntity<DolphinSchedulerApiResponse<DolphinSchedulerSyncRunVO>> run(
            @PathVariable("taskCode") String taskCode,
            @RequestHeader(value = SchedulerTokenVerifier.HEADER_NAME, required = false) String schedulerToken,
            @RequestHeader(value = AUTHORIZATION_HEADER, required = false) String authorization,
            @RequestBody(required = false) DolphinSchedulerSyncRunRequest request,
            HttpServletRequest servletRequest
    ) {
        SchedulerTokenVerification verification = verifyToken(schedulerToken, authorization, servletRequest);
        if (!verification.isAllowed()) {
            return failure(verification.getHttpStatus(), verification.getMessage());
        }

        try {
            RunResultVO result = syncRunCoordinatorService.runTask(taskCode, toRunTaskRequest(request));
            return ResponseEntity.ok(DolphinSchedulerApiResponse.success(toRunVO(result)));
        } catch (ServiceException e) {
            return serviceFailure(e);
        } catch (Exception e) {
            log.error("DolphinScheduler sync run failed, taskCode={}", taskCode, e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "DolphinScheduler sync run failed: " + e.getMessage());
        }
    }

    @GetMapping("/runs/{extractRunId}")
    public ResponseEntity<DolphinSchedulerApiResponse<DolphinSchedulerSyncRunVO>> getRun(
            @PathVariable("extractRunId") String extractRunId,
            @RequestHeader(value = SchedulerTokenVerifier.HEADER_NAME, required = false) String schedulerToken,
            @RequestHeader(value = AUTHORIZATION_HEADER, required = false) String authorization,
            HttpServletRequest servletRequest
    ) {
        SchedulerTokenVerification verification = verifyToken(schedulerToken, authorization, servletRequest);
        if (!verification.isAllowed()) {
            return failure(verification.getHttpStatus(), verification.getMessage());
        }

        try {
            RunDetailVO detail = syncRunCoordinatorService.getRun(extractRunId);
            return ResponseEntity.ok(DolphinSchedulerApiResponse.success(toRunVO(detail)));
        } catch (ServiceException e) {
            return serviceFailure(e);
        } catch (Exception e) {
            log.error("DolphinScheduler sync status query failed, extractRunId={}", extractRunId, e);
            return failure(HttpStatus.INTERNAL_SERVER_ERROR,
                    "DolphinScheduler sync status query failed: " + e.getMessage());
        }
    }

    private RunTaskRequest toRunTaskRequest(DolphinSchedulerSyncRunRequest request) {
        DolphinSchedulerSyncRunRequest safeRequest = request == null
                ? new DolphinSchedulerSyncRunRequest()
                : request;
        validateVersion(safeRequest.getVersion());

        Map<String, Object> params = new LinkedHashMap<>();
        if (safeRequest.getParams() != null) {
            params.putAll(safeRequest.getParams());
        }
        if (!isBlank(safeRequest.getBizDate())) {
            params.putIfAbsent("bizDate", safeRequest.getBizDate());
            params.putIfAbsent("biz_date", safeRequest.getBizDate());
        }
        if (!isBlank(safeRequest.getIdempotencyKey())) {
            params.putIfAbsent("idempotency_key", safeRequest.getIdempotencyKey());
            params.putIfAbsent("scheduler_run_id", safeRequest.getIdempotencyKey());
        }

        RunTaskRequest runRequest = new RunTaskRequest();
        runRequest.setTriggerType(toInternalTriggerType(safeRequest.getTriggerType()));
        runRequest.setRunMode(toInternalRunMode(safeRequest.getRunMode()));
        runRequest.setParams(params);
        runRequest.setWaitForFinish(false);
        runRequest.setBizDate(safeRequest.getBizDate());
        runRequest.setVersion(safeRequest.getVersion());
        runRequest.setIdempotencyKey(safeRequest.getIdempotencyKey());
        runRequest.setSchedulerRunId(safeRequest.getIdempotencyKey());
        return runRequest;
    }

    private void validateVersion(String version) {
        if (!isBlank(version) && !"latest".equalsIgnoreCase(version.trim())) {
            throw new ServiceException("Only latest sync task version is supported by DolphinScheduler Run API");
        }
    }

    private String toInternalTriggerType(String triggerType) {
        if (isBlank(triggerType)) {
            return SyncTriggerType.SCHEDULED.getCode();
        }
        String normalized = triggerType.trim().toUpperCase(Locale.ROOT);
        if ("DOLPHINSCHEDULER".equals(normalized)) {
            return SyncTriggerType.SCHEDULED.getCode();
        }
        return normalized;
    }

    private String toInternalRunMode(String runMode) {
        if (isBlank(runMode)) {
            return SyncRunMode.NORMAL.getCode();
        }
        String normalized = runMode.trim().toUpperCase(Locale.ROOT);
        if ("SCHEDULE".equals(normalized) || "DOLPHINSCHEDULER".equals(normalized)) {
            return SyncRunMode.NORMAL.getCode();
        }
        return normalized;
    }

    private DolphinSchedulerSyncRunVO toRunVO(RunResultVO result) {
        DolphinSchedulerSyncRunVO vo = new DolphinSchedulerSyncRunVO();
        vo.setExtractRunId(result.getRunId());
        vo.setBatchId(result.getBatchId());
        vo.setTaskCode(result.getTaskCode());
        vo.setStatus(toDolphinSchedulerStatus(firstNonBlank(result.getRunStatus(), result.getStatus())));
        vo.setSeatunnelJobId(result.getSeatunnelJobId());
        vo.setErrorMessage(result.getErrorMessage());
        return vo;
    }

    private DolphinSchedulerSyncRunVO toRunVO(RunDetailVO detail) {
        DolphinSchedulerSyncRunVO vo = new DolphinSchedulerSyncRunVO();
        vo.setExtractRunId(detail.getRunId());
        vo.setBatchId(detail.getBatchId());
        vo.setTaskCode(detail.getTaskCode());
        vo.setStatus(toDolphinSchedulerStatus(detail.getRunStatus()));
        vo.setSeatunnelJobId(detail.getSeatunnelJobId());
        vo.setErrorMessage(detail.getErrorMessage());
        vo.setStartTime(detail.getStartTime());
        vo.setEndTime(detail.getEndTime());
        return vo;
    }

    private String toDolphinSchedulerStatus(String status) {
        if (isBlank(status)) {
            return SyncRunStatus.SUBMITTED.getCode();
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (SyncRunStatus.SUCCESS.getCode().equals(normalized)) {
            return SyncRunStatus.SUCCESS.getCode();
        }
        if (SyncRunStatus.RUNNING.getCode().equals(normalized)) {
            return SyncRunStatus.RUNNING.getCode();
        }
        if (SyncRunStatus.CANCELED.getCode().equals(normalized)) {
            return SyncRunStatus.CANCELED.getCode();
        }
        if (SyncRunStatus.FAILED.getCode().equals(normalized)
                || SyncRunStatus.CHECK_FAILED.getCode().equals(normalized)
                || SyncRunStatus.SKIPPED.getCode().equals(normalized)) {
            return SyncRunStatus.FAILED.getCode();
        }
        return SyncRunStatus.SUBMITTED.getCode();
    }

    private SchedulerTokenVerification verifyToken(
            String schedulerToken,
            String authorization,
            HttpServletRequest request
    ) {
        SchedulerTokenVerification verification = schedulerTokenVerifier.verify(resolveToken(schedulerToken, authorization));
        if (!verification.isAllowed()) {
            String remoteAddr = request == null ? "unknown" : request.getRemoteAddr();
            log.warn("DolphinScheduler sync token rejected, remoteAddr={}, reason={}",
                    remoteAddr,
                    verification.getMessage());
        }
        return verification;
    }

    private String resolveToken(String schedulerToken, String authorization) {
        if (!isBlank(schedulerToken)) {
            return schedulerToken;
        }
        if (isBlank(authorization)) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return trimmed.substring(BEARER_PREFIX.length()).trim();
        }
        return trimmed;
    }

    private ResponseEntity<DolphinSchedulerApiResponse<DolphinSchedulerSyncRunVO>> serviceFailure(ServiceException e) {
        HttpStatus httpStatus = e.getCode() == Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode()
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;
        return failure(httpStatus, e.getMessage());
    }

    private ResponseEntity<DolphinSchedulerApiResponse<DolphinSchedulerSyncRunVO>> failure(
            HttpStatus httpStatus,
            String message
    ) {
        return ResponseEntity
                .status(httpStatus)
                .body(DolphinSchedulerApiResponse.failure(message));
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
