package org.apache.seatunnel.web.api.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.api.service.BatchJobExecutorService;
import org.apache.seatunnel.web.api.service.BatchJobInstanceService;
import org.apache.seatunnel.web.common.enums.RunMode;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.spi.bean.vo.JobInstanceVO;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SchedulerExecutorServiceImpl implements SchedulerExecutorService {

    private static final String DEFAULT_TRIGGERED_BY = "SCHEDULER";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_TIMEOUT = "TIMEOUT";
    private static final String STATUS_NOT_IMPLEMENTED = "NOT_IMPLEMENTED";

    private final BatchJobExecutorService batchJobExecutorService;
    private final BatchJobInstanceService batchJobInstanceService;
    private final SchedulerProperties schedulerProperties;

    public SchedulerExecutorServiceImpl(
            BatchJobExecutorService batchJobExecutorService,
            BatchJobInstanceService batchJobInstanceService,
            SchedulerProperties schedulerProperties) {
        this.batchJobExecutorService = batchJobExecutorService;
        this.batchJobInstanceService = batchJobInstanceService;
        this.schedulerProperties = schedulerProperties;
    }

    @Override
    public SchedulerRunResponse run(Long jobDefineId, SchedulerRunRequest request) {
        validateJobDefineId(jobDefineId);

        SchedulerRunRequest safeRequest = safeRequest(request);
        String triggeredBy = normalizeTriggeredBy(safeRequest.getTriggeredBy());
        String externalBatchId = normalizeOptional(safeRequest.getExternalBatchId());

        log.info("scheduler run request received, jobDefineId={}, triggeredBy={}",
                jobDefineId, triggeredBy);

        Long jobInstanceId = batchJobExecutorService.jobExecute(
                jobDefineId,
                RunMode.SCHEDULED,
                externalBatchId);

        log.info("scheduler run submitted, jobDefineId={}, jobInstanceId={}",
                jobDefineId, jobInstanceId);

        SchedulerRunResponse response = baseResponse(jobDefineId, safeRequest, triggeredBy);
        response.setJobInstanceId(jobInstanceId);
        response.setStatus(STATUS_SUBMITTED);
        response.setMessage("submitted");
        return response;
    }

    @Override
    public SchedulerRunResponse runSync(Long jobDefineId, SchedulerRunRequest request) {
        SchedulerRunResponse response = run(jobDefineId, request);
        if (response.getJobInstanceId() == null) {
            response.setStatus(STATUS_NOT_IMPLEMENTED);
            response.setMessage("run-sync requires a jobInstanceId, but existing execute logic returned null");
            log.info("scheduler run-sync finished, jobDefineId={}, jobInstanceId={}, finalStatus={}",
                    jobDefineId, null, STATUS_NOT_IMPLEMENTED);
            throw new SchedulerRunFailureException(
                    HttpStatus.NOT_IMPLEMENTED,
                    response.getMessage(),
                    response);
        }

        SchedulerRunRequest safeRequest = safeRequest(request);
        int timeoutSeconds = resolveTimeoutSeconds(safeRequest);
        int pollIntervalSeconds = resolvePollIntervalSeconds(safeRequest);

        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        Long jobInstanceId = response.getJobInstanceId();

        while (true) {
            JobInstanceVO instance = batchJobInstanceService.selectById(jobInstanceId);
            String finalStatus = instance == null ? null : normalizeOptional(instance.getJobStatus());

            if (isSuccessStatus(finalStatus)) {
                response.setStatus(finalStatus);
                response.setMessage("finished");
                log.info("scheduler run-sync finished, jobDefineId={}, jobInstanceId={}, finalStatus={}",
                        jobDefineId, jobInstanceId, finalStatus);
                return response;
            }

            if (isFailedStatus(finalStatus)) {
                response.setStatus(finalStatus);
                response.setMessage("finished with status " + finalStatus);
                log.info("scheduler run-sync finished, jobDefineId={}, jobInstanceId={}, finalStatus={}",
                        jobDefineId, jobInstanceId, finalStatus);
                throw new SchedulerRunFailureException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        response.getMessage(),
                        response);
            }

            if (System.currentTimeMillis() >= deadline) {
                response.setStatus(STATUS_TIMEOUT);
                response.setMessage("wait timeout after " + timeoutSeconds + " seconds");
                log.info("scheduler run-sync finished, jobDefineId={}, jobInstanceId={}, finalStatus={}",
                        jobDefineId, jobInstanceId, STATUS_TIMEOUT);
                throw new SchedulerRunFailureException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        response.getMessage(),
                        response);
            }

            sleep(pollIntervalSeconds);
        }
    }

    private SchedulerRunResponse baseResponse(
            Long jobDefineId,
            SchedulerRunRequest request,
            String triggeredBy) {
        SchedulerRunResponse response = new SchedulerRunResponse();
        response.setJobDefineId(jobDefineId);
        response.setTriggeredBy(triggeredBy);
        response.setExternalBatchId(normalizeOptional(request.getExternalBatchId()));
        response.setOperator(schedulerProperties.getOperator());
        response.setRemark(normalizeOptional(request.getRemark()));
        return response;
    }

    private SchedulerRunRequest safeRequest(SchedulerRunRequest request) {
        return request == null ? new SchedulerRunRequest() : request;
    }

    private void validateJobDefineId(Long jobDefineId) {
        if (jobDefineId == null || jobDefineId <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "jobDefineId");
        }
    }

    private int resolveTimeoutSeconds(SchedulerRunRequest request) {
        int timeoutSeconds = request.getTimeoutSeconds() == null
                ? schedulerProperties.getRunSyncDefaultTimeoutSeconds()
                : request.getTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "timeoutSeconds");
        }
        return timeoutSeconds;
    }

    private int resolvePollIntervalSeconds(SchedulerRunRequest request) {
        int pollIntervalSeconds = request.getPollIntervalSeconds() == null
                ? schedulerProperties.getRunSyncDefaultPollIntervalSeconds()
                : request.getPollIntervalSeconds();
        if (pollIntervalSeconds <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "pollIntervalSeconds");
        }
        return pollIntervalSeconds;
    }

    private String normalizeTriggeredBy(String triggeredBy) {
        String normalized = normalizeOptional(triggeredBy);
        return normalized == null ? DEFAULT_TRIGGERED_BY : normalized;
    }

    private String normalizeOptional(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isSuccessStatus(String status) {
        return "FINISHED".equalsIgnoreCase(status);
    }

    private boolean isFailedStatus(String status) {
        if (status == null) {
            return false;
        }
        return "FAILED".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status)
                || "STOPPED".equalsIgnoreCase(status)
                || "UNKNOWABLE".equalsIgnoreCase(status);
    }

    private void sleep(int pollIntervalSeconds) {
        try {
            Thread.sleep(pollIntervalSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SchedulerRunFailureException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "scheduler run-sync interrupted",
                    null);
        }
    }
}
