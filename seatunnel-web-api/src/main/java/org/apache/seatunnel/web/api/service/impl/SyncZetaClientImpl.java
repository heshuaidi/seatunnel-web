package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncZetaClient;
import org.apache.seatunnel.web.api.service.model.SyncJobStatusResult;
import org.apache.seatunnel.web.api.service.model.SyncSubmitJobResult;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.engine.client.rest.SeaTunnelRestClient;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@SuppressWarnings({"rawtypes", "unchecked"})
public class SyncZetaClientImpl implements SyncZetaClient {

    @Resource
    private SeaTunnelRestClient seaTunnelRestClient;

    @Override
    public SyncSubmitJobResult submitJob(Long clientId, String jobName, String hoconText) {
        if (clientId == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "clientId");
        }

        Map response = seaTunnelRestClient.submitJobText(
                clientId,
                hoconText,
                "hocon",
                null,
                jobName,
                false
        );

        SyncSubmitJobResult result = new SyncSubmitJobResult();
        result.setRawResponse(toStringObjectMap(response));
        result.setJobId(extractJobId(response));
        result.setJobName(jobName);

        if (result.getJobId() == null) {
            throw new ServiceException("SeaTunnel submit response missing jobId: " + response);
        }

        return result;
    }

    @Override
    public SyncJobStatusResult getJobStatus(Long clientId, String jobId) {
        Long engineJobId = parseJobId(jobId);
        Map response = seaTunnelRestClient.jobInfo(clientId, engineJobId);

        String status = firstNonBlank(
                findString(response, "jobStatus"),
                findString(response, "status"),
                findString(response, "state")
        );

        SyncJobStatusResult result = new SyncJobStatusResult();
        result.setJobId(jobId);
        result.setStatus(status);
        result.setRawResponse(toStringObjectMap(response));
        result.setErrorMessage(firstNonBlank(
                findString(response, "errorMsg"),
                findString(response, "errorMessage"),
                findString(response, "exception")
        ));

        String normalized = status == null ? "" : status.toUpperCase(Locale.ROOT);
        result.setSuccess("FINISHED".equals(normalized) || "SUCCESS".equals(normalized));
        result.setEndState(result.isSuccess()
                || "FAILED".equals(normalized)
                || "CANCELED".equals(normalized)
                || "CANCELLED".equals(normalized)
                || "UNKNOWABLE".equals(normalized));

        return result;
    }

    @Override
    public void stopJob(Long clientId, String jobId) {
        seaTunnelRestClient.stopJob(clientId, parseJobId(jobId), false);
    }

    private String extractJobId(Map response) {
        String jobId = firstNonBlank(
                findString(response, "jobId"),
                findString(response, "job_id"),
                findString(response, "id")
        );
        if (jobId != null) {
            return jobId;
        }

        Object data = response == null ? null : response.get("data");
        if (data instanceof Map) {
            Map dataMap = (Map) data;
            return firstNonBlank(
                    findString(dataMap, "jobId"),
                    findString(dataMap, "job_id"),
                    findString(dataMap, "id")
            );
        }
        return null;
    }

    private Long parseJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "jobId");
        }
        try {
            return Long.parseLong(jobId.trim());
        } catch (Exception e) {
            throw new ServiceException("SeaTunnel jobId must be numeric in current version: " + jobId);
        }
    }

    private String findString(Map response, String key) {
        if (response == null || key == null) {
            return null;
        }
        Object value = response.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Object> toStringObjectMap(Map response) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (response == null) {
            return result;
        }
        for (Object key : response.keySet()) {
            if (key != null) {
                result.put(String.valueOf(key), response.get(key));
            }
        }
        return result;
    }
}
