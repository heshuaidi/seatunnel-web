package org.apache.seatunnel.web.api.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.web.api.service.SyncZetaClient;
import org.apache.seatunnel.web.api.service.model.SyncJobStatusResult;
import org.apache.seatunnel.web.api.service.model.SyncSubmitJobResult;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.engine.client.exceptions.SeatunnelClientException;
import org.apache.seatunnel.web.engine.client.rest.SeaTunnelRestClient;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public class SyncZetaClientImpl implements SyncZetaClient {

    private static final String SUBMIT_JOB_TEXT_FAILED = "POST /submit-job(text) failed";
    private static final int ERROR_SUMMARY_MAX_LENGTH = 500;

    @Resource
    private SeaTunnelRestClient seaTunnelRestClient;

    @Override
    public SyncSubmitJobResult submitJob(Long clientId, String jobName, String hoconText) {
        if (clientId == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "clientId");
        }

        Map response;
        try {
            response = seaTunnelRestClient.submitJobText(
                    clientId,
                    hoconText,
                    "hocon",
                    null,
                    jobName,
                    false
            );
        } catch (Exception e) {
            String message = submitJobErrorMessage(e);
            logSubmitFailure(clientId, jobName, message, e);
            throw new ServiceException(message, e);
        }

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
        SyncJobStatusResult result = new SyncJobStatusResult();
        result.setJobId(jobId);

        Map<String, Object> raw = new LinkedHashMap<>();
        try {
            Map response = seaTunnelRestClient.jobInfo(clientId, engineJobId);
            raw.put("jobInfo", toStringObjectMap(response));
            applyJobInfo(result, response);
            if (!isBlank(result.getStatus())) {
                result.setRawResponse(raw);
                markTerminalFlags(result);
                return result;
            }
        } catch (Exception e) {
            raw.put("jobInfoError", errorMessage(e));
            result.setErrorMessage(errorMessage(e));
        }

        try {
            List running = seaTunnelRestClient.runningJobs(clientId);
            raw.put("runningJobsChecked", true);
            if (containsJob(running, engineJobId)) {
                result.setStatus("RUNNING");
                result.setRawResponse(raw);
                markTerminalFlags(result);
                return result;
            }
        } catch (Exception e) {
            raw.put("runningJobsError", errorMessage(e));
        }

        for (String finishedState : new String[] {"FINISHED", "FAILED", "CANCELED", "CANCELLED", "UNKNOWABLE"}) {
            try {
                List finished = seaTunnelRestClient.finishedJobs(clientId, finishedState);
                raw.put("finishedJobs_" + finishedState + "_checked", true);
                if (containsJob(finished, engineJobId)) {
                    result.setStatus(finishedState);
                    result.setRawResponse(raw);
                    markTerminalFlags(result);
                    return result;
                }
            } catch (Exception e) {
                raw.put("finishedJobs_" + finishedState + "_error", errorMessage(e));
            }
        }

        result.setRawResponse(raw);
        markTerminalFlags(result);
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

    private void applyJobInfo(SyncJobStatusResult result, Map response) {
        String status = firstNonBlank(
                findString(response, "jobStatus"),
                findString(response, "status"),
                findString(response, "state")
        );
        result.setStatus(status);
        result.setErrorMessage(firstNonBlank(
                findString(response, "errorMsg"),
                findString(response, "errorMessage"),
                findString(response, "exception"),
                findString(response, "rootCause"),
                findString(response, "message"),
                findErrorSummary(response)
        ));
        result.setSourceCount(findLong(response,
                "sourceCount",
                "source_count",
                "sourceRows",
                "sourceRowCount",
                "readRowCount",
                "readRows"));
        result.setSinkCount(findLong(response,
                "sinkCount",
                "sink_count",
                "sinkRows",
                "sinkRowCount",
                "writeRowCount",
                "writtenRows"));
        result.setErrorCount(findLong(response,
                "errorCount",
                "error_count",
                "failedCount",
                "errorRows",
                "dirtyCount"));
    }

    private void markTerminalFlags(SyncJobStatusResult result) {
        String normalized = normalizeStatus(result.getStatus());
        result.setSuccess("FINISHED".equals(normalized) || "SUCCESS".equals(normalized));
        result.setEndState(result.isSuccess()
                || "FAILED".equals(normalized)
                || "CANCELED".equals(normalized)
                || "CANCELLED".equals(normalized)
                || "UNKNOWABLE".equals(normalized)
                || "TIMEOUT".equals(normalized));
    }

    private boolean containsJob(List list, Long jobId) {
        if (list == null || list.isEmpty() || jobId == null) {
            return false;
        }
        for (Object item : list) {
            Long itemJobId = null;
            if (item instanceof Map) {
                Map map = (Map) item;
                itemJobId = firstNonNullLong(
                        findLong(map, "jobId", "job_id", "id"),
                        findLong(map, "jobID", "job_id", "jobIdString")
                );
            } else {
                itemJobId = toLong(item);
            }
            if (jobId.equals(itemJobId)) {
                return true;
            }
        }
        return false;
    }

    private String findString(Map response, String key) {
        if (response == null || key == null) {
            return null;
        }
        Object value = response.get(key);
        if (value == null) {
            Object data = response.get("data");
            if (data instanceof Map) {
                value = ((Map) data).get(key);
            }
        }
        return value == null ? null : String.valueOf(value);
    }

    private Long findLong(Map response, String... keys) {
        if (response == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Long value = toLong(response.get(key));
            if (value != null) {
                return value;
            }
        }

        Object data = response.get("data");
        if (data instanceof Map) {
            Long value = findLong((Map) data, keys);
            if (value != null) {
                return value;
            }
        }

        Object metrics = response.get("metrics");
        if (metrics instanceof Map) {
            return findLong((Map) metrics, keys);
        }
        return null;
    }

    private Long firstNonNullLong(Long first, Long second) {
        return first == null ? second : first;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String errorMessage(Exception e) {
        String summary = extractBestErrorSummary(e);
        if (!isBlank(summary)) {
            return summary;
        }
        return e == null || e.getMessage() == null ? String.valueOf(e) : e.getMessage();
    }

    private String submitJobErrorMessage(Exception e) {
        String summary = extractBestErrorSummary(e);
        if (isBlank(summary)) {
            summary = e == null || e.getMessage() == null ? String.valueOf(e) : e.getMessage();
        }
        if (summary != null && summary.startsWith(SUBMIT_JOB_TEXT_FAILED)) {
            return summary;
        }
        return isBlank(summary) ? SUBMIT_JOB_TEXT_FAILED : SUBMIT_JOB_TEXT_FAILED + ": " + summary;
    }

    private void logSubmitFailure(Long clientId, String jobName, String message, Exception e) {
        log.error(
                "Submit SeaTunnel job failed, clientId={}, jobName={}, errorMessage={}, httpStatus={}, responseHeaders={}, responseBody={}",
                clientId,
                jobName,
                message,
                firstNonBlankResponseStatus(e),
                abbreviate(firstNonBlankResponseHeaders(e), 2000),
                abbreviate(firstNonBlankResponseBody(e), 4000),
                e);
    }

    private String extractBestErrorSummary(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String bodySummary = extractErrorSummaryFromBody(responseBody(current));
            if (!isBlank(bodySummary)) {
                return bodySummary;
            }
        }

        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String messageSummary = extractRootCause(current.getMessage());
            if (!isBlank(messageSummary)) {
                return messageSummary;
            }
        }
        return null;
    }

    private String extractErrorSummaryFromBody(String responseBody) {
        if (isBlank(responseBody)) {
            return null;
        }
        try {
            Map<String, Object> body = JSONUtils.parseObject(
                    responseBody,
                    new TypeReference<Map<String, Object>>() {}
            );
            String summary = findErrorSummary(body);
            if (!isBlank(summary)) {
                return summary;
            }
        } catch (Exception ignored) {
            // SeaTunnel may return plain text or a truncated JSON body.
        }
        return extractRootCause(responseBody);
    }

    private String responseBody(Throwable throwable) {
        if (throwable instanceof SeatunnelClientException) {
            return ((SeatunnelClientException) throwable).getResponseBody();
        }
        if (throwable instanceof HttpStatusCodeException) {
            return ((HttpStatusCodeException) throwable).getResponseBodyAsString();
        }
        if (throwable instanceof RestClientResponseException) {
            return ((RestClientResponseException) throwable).getResponseBodyAsString();
        }
        return invokeStringGetter(throwable, "getResponseBodyAsString", "getResponseBody");
    }

    private String responseHeaders(Throwable throwable) {
        if (throwable instanceof SeatunnelClientException) {
            return ((SeatunnelClientException) throwable).getResponseHeaders();
        }
        if (throwable instanceof HttpStatusCodeException) {
            return String.valueOf(((HttpStatusCodeException) throwable).getResponseHeaders());
        }
        if (throwable instanceof RestClientResponseException) {
            return String.valueOf(((RestClientResponseException) throwable).getResponseHeaders());
        }
        return invokeStringGetter(throwable, "getResponseHeaders", "getHeaders");
    }

    private String responseStatus(Throwable throwable) {
        if (throwable instanceof SeatunnelClientException) {
            int status = ((SeatunnelClientException) throwable).getHttpStatus();
            return status < 0 ? null : String.valueOf(status);
        }
        if (throwable instanceof HttpStatusCodeException) {
            return String.valueOf(((HttpStatusCodeException) throwable).getStatusCode().value());
        }
        if (throwable instanceof RestClientResponseException) {
            return String.valueOf(((RestClientResponseException) throwable).getStatusCode().value());
        }
        return invokeStringGetter(throwable, "getStatusCode", "getRawStatusCode");
    }

    private String firstNonBlankResponseBody(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String value = responseBody(current);
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlankResponseHeaders(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String value = responseHeaders(current);
            if (!isBlank(value) && !"null".equalsIgnoreCase(value.trim())) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlankResponseStatus(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String value = responseStatus(current);
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String invokeStringGetter(Throwable throwable, String... methodNames) {
        if (throwable == null || methodNames == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Object value = throwable.getClass().getMethod(methodName).invoke(throwable);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (Exception ignored) {
                // Not every RestTemplate-related exception exposes response metadata.
            }
        }
        return null;
    }

    private String findErrorSummary(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            Map map = (Map) value;
            for (Object key : map.keySet()) {
                String keyText = key == null ? "" : String.valueOf(key);
                Object item = map.get(key);
                if (isErrorKey(keyText) && item != null && !(item instanceof Map) && !(item instanceof List)) {
                    String text = extractRootCause(String.valueOf(item));
                    if (!isBlank(text)) {
                        return text;
                    }
                }
            }
            for (Object item : map.values()) {
                String nested = findErrorSummary(item);
                if (!isBlank(nested)) {
                    return nested;
                }
            }
            return null;
        }
        if (value instanceof List) {
            for (Object item : (List) value) {
                String nested = findErrorSummary(item);
                if (!isBlank(nested)) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean isErrorKey(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("exception")
                || normalized.contains("rootcause")
                || normalized.contains("root_cause")
                || normalized.contains("error")
                || "message".equals(normalized)
                || "cause".equals(normalized);
    }

    private String extractRootCause(String value) {
        String normalized = normalizeErrorText(value);
        if (isBlank(normalized)) {
            return null;
        }

        String[] causedByParts = normalized.split("(?i)Caused by:");
        String deepest = causedByParts.length == 0
                ? normalized
                : causedByParts[causedByParts.length - 1].trim();
        deepest = deepest.replaceFirst("(?i)^root cause:\\s*", "").trim();
        deepest = deepest.replaceFirst("\\s+at\\s+[A-Za-z_$][A-Za-z0-9_.$]*\\(.+$", "").trim();
        deepest = deepest.replaceFirst("\\s+Suppressed:\\s+.+$", "").trim();
        deepest = stripExceptionPrefix(deepest);
        return abbreviate(deepest, ERROR_SUMMARY_MAX_LENGTH);
    }

    private String stripExceptionPrefix(String value) {
        String result = value;
        while (!isBlank(result)) {
            String stripped = result.replaceFirst(
                    "^((?:[A-Za-z_$][A-Za-z0-9_$]*\\.)+[A-Za-z_$][A-Za-z0-9_$]*|[A-Za-z_$][A-Za-z0-9_$]*(?:Exception|Error|Throwable)):\\s+",
                    ""
            ).trim();
            if (stripped.equals(result)) {
                return result;
            }
            result = stripped;
        }
        return result;
    }

    private String normalizeErrorText(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replace("\\\"", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
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
