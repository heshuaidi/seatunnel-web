package org.apache.seatunnel.web.engine.client.rest;

import org.apache.seatunnel.web.engine.client.exceptions.SeatunnelClientException;
import org.apache.seatunnel.web.engine.client.modal.SeaTunnelClientAuth;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"unchecked", "rawtypes"})
@Service
public class SeaTunnelRestClient {

    private static final String DEFAULT_FINISHED_JOB_STATE = "UNKNOWABLE";
    private static final String DEFAULT_CONFIG_FILE_NAME = "job.conf";
    private static final int ERROR_SUMMARY_MAX_LENGTH = 500;
    private static final Pattern JSON_MESSAGE_PATTERN = Pattern.compile(
            "\"(?:rootCause|root_cause|exception|errorMessage|error_message|message|cause)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private final RestTemplate restTemplate;
    private final SeaTunnelClientResolver seatunnelClientResolver;

    public SeaTunnelRestClient(RestTemplate restTemplate,
                               SeaTunnelClientResolver seatunnelClientResolver) {
        this.restTemplate = restTemplate;
        this.seatunnelClientResolver = seatunnelClientResolver;
    }

    /* ===================== URL ===================== */

    private String url(Long clientId, String path) {
        String baseApiUrl = seatunnelClientResolver.resolveBaseApiUrl(clientId);

        if (baseApiUrl == null || baseApiUrl.trim().isEmpty()) {
            throw new SeatunnelClientException(
                    "SeaTunnel client baseUrl is empty",
                    -1,
                    "",
                    null
            );
        }

        baseApiUrl = trimEndSlash(baseApiUrl.trim());

        if (path == null || path.trim().isEmpty()) {
            return baseApiUrl;
        }

        path = path.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return baseApiUrl + path;
    }

    private String trimEndSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /* ===================== Headers ===================== */

    private HttpHeaders jsonHeaders(Long clientId) {
        return headers(clientId, MediaType.APPLICATION_JSON);
    }

    private HttpHeaders textHeaders(Long clientId) {
        return headers(clientId, MediaType.TEXT_PLAIN);
    }

    private HttpHeaders multipartHeaders(Long clientId) {
        return headers(clientId, MediaType.MULTIPART_FORM_DATA);
    }

    private HttpHeaders getHeaders(Long clientId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        applyBasicAuth(clientId, headers);
        return headers;
    }

    private HttpHeaders headers(Long clientId, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        applyBasicAuth(clientId, headers);
        return headers;
    }

    private void applyBasicAuth(Long clientId, HttpHeaders headers) {
        if (clientId == null) {
            return;
        }

        SeaTunnelClientAuth auth = seatunnelClientResolver.resolveAuth(clientId);

        if (auth == null) {
            return;
        }

        if (!Boolean.TRUE.equals(auth.getAuthEnabled())) {
            return;
        }

        if (isBlank(auth.getUsername()) || isBlank(auth.getPassword())) {
            return;
        }

        headers.setBasicAuth(
                auth.getUsername().trim(),
                auth.getPassword(),
                StandardCharsets.UTF_8
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /* ===================== Common Request ===================== */

    private <T> T get(Long clientId, String requestUrl, Class<T> responseType, String hint) {
        try {
            ResponseEntity<T> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.GET,
                    new HttpEntity<Void>(null, getHeaders(clientId)),
                    responseType
            );
            return response.getBody();
        } catch (Exception e) {
            throw wrap(e, hint);
        }
    }

    private <T> T post(Long clientId,
                       String requestUrl,
                       Object body,
                       HttpHeaders headers,
                       Class<T> responseType,
                       String hint) {
        try {
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            ResponseEntity<T> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.POST,
                    entity,
                    responseType
            );
            return response.getBody();
        } catch (Exception e) {
            throw wrap(e, hint);
        }
    }

    private RuntimeException wrap(Exception e, String hint) {
        if (e instanceof HttpStatusCodeException) {
            HttpStatusCodeException he = (HttpStatusCodeException) e;
            String body = safe(he.getResponseBodyAsString());
            return new SeatunnelClientException(
                    withSummary(hint, body),
                    he.getRawStatusCode(),
                    body,
                    he
            );
        }

        return new SeatunnelClientException(
                hint,
                -1,
                "",
                e
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String withSummary(String hint, String responseBody) {
        String summary = extractErrorSummary(responseBody);
        return isBlank(summary) ? hint : hint + ": " + summary;
    }

    private String extractErrorSummary(String responseBody) {
        if (isBlank(responseBody)) {
            return null;
        }
        Matcher matcher = JSON_MESSAGE_PATTERN.matcher(responseBody);
        while (matcher.find()) {
            String value = normalizeErrorText(matcher.group(1));
            if (!isBlank(value)) {
                return value;
            }
        }

        String normalized = normalizeErrorText(responseBody);
        int accessDeniedIndex = normalized.toLowerCase().indexOf("access denied");
        if (accessDeniedIndex >= 0) {
            normalized = normalized.substring(accessDeniedIndex);
        }
        return abbreviate(normalized, ERROR_SUMMARY_MAX_LENGTH);
    }

    private String normalizeErrorText(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\n", " ")
                .replace("\\r", " ")
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

    /* ===================== GET ===================== */

    public Map overview(Long clientId, Map<String, String> tags) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url(clientId, "/overview"));
            appendQueryParams(builder, tags);

            return get(
                    clientId,
                    builder.build(true).toUriString(),
                    Map.class,
                    "GET /overview failed"
            );
        } catch (Exception e) {
            throw wrap(e, "GET /overview failed");
        }
    }

    /**
     * 保留这个重载，适合没有 clientId 的临时探活场景。
     *
     * 注意：这个方法没有 clientId，所以无法从数据库读取账号密码。
     * 如果 Zeta Engine 开启了 Basic Auth，建议优先使用 overview(Long clientId, Map<String, String> tags)。
     */
    public Map overview(String baseUrl, Map<String, String> tags) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
            appendQueryParams(builder, tags);

            ResponseEntity<Map> response = restTemplate.exchange(
                    builder.build(true).toUri(),
                    HttpMethod.GET,
                    new HttpEntity<Void>(null, new HttpHeaders()),
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw wrap(e, "GET /overview failed");
        }
    }

    public Map overview(String baseUrl, Map<String, String> tags, SeaTunnelClientAuth auth) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
            appendQueryParams(builder, tags);

            ResponseEntity<Map> response = restTemplate.exchange(
                    builder.build(true).toUri(),
                    HttpMethod.GET,
                    new HttpEntity<Void>(null, getHeaders(auth)),
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw wrap(e, "GET /overview failed");
        }
    }

    private HttpHeaders getHeaders(SeaTunnelClientAuth auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        applyBasicAuth(auth, headers);
        return headers;
    }

    private void applyBasicAuth(SeaTunnelClientAuth auth, HttpHeaders headers) {
        if (auth == null) {
            return;
        }

        if (!Boolean.TRUE.equals(auth.getAuthEnabled())) {
            return;
        }

        if (isBlank(auth.getUsername()) || isBlank(auth.getPassword())) {
            return;
        }

        headers.setBasicAuth(
                auth.getUsername().trim(),
                auth.getPassword(),
                StandardCharsets.UTF_8
        );
    }

    public List runningJobs(Long clientId) {
        return get(
                clientId,
                url(clientId, "/running-jobs"),
                List.class,
                "GET /running-jobs failed"
        );
    }

    public Map jobInfo(Long clientId, long jobId) {
        return get(
                clientId,
                url(clientId, "/job-info/" + jobId),
                Map.class,
                "GET /job-info/{jobId} failed"
        );
    }

    public List finishedJobs(Long clientId, String state) {
        if (isBlank(state)) {
            state = DEFAULT_FINISHED_JOB_STATE;
        }

        return get(
                clientId,
                url(clientId, "/finished-jobs/" + state.trim()),
                List.class,
                "GET /finished-jobs/{state} failed"
        );
    }

    public List systemMonitoringInformation(Long clientId) {
        return get(
                clientId,
                url(clientId, "/system-monitoring-information"),
                List.class,
                "GET /system-monitoring-information failed"
        );
    }

    public Object logs(Long clientId, Long jobIdOrNull, String formatOrNull) {
        try {
            String path = jobIdOrNull == null ? "/logs" : "/logs/" + jobIdOrNull;

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url(clientId, path));
            if (!isBlank(formatOrNull)) {
                builder.queryParam("format", formatOrNull.trim());
            }

            return get(
                    clientId,
                    builder.build(true).toUriString(),
                    Object.class,
                    "GET /logs failed"
            );
        } catch (Exception e) {
            throw wrap(e, "GET /logs failed");
        }
    }

    public Object nodeLogs(Long clientId) {
        return get(
                clientId,
                url(clientId, "/log"),
                Object.class,
                "GET /log failed"
        );
    }

    public String metrics(Long clientId, boolean openMetrics) {
        String path = openMetrics ? "/openmetrics" : "/metrics";

        return get(
                clientId,
                url(clientId, path),
                String.class,
                "GET /metrics failed"
        );
    }

    /* ===================== POST ===================== */

    public Map submitJobText(Long clientId,
                             String configText,
                             String format,
                             String jobId,
                             String jobName,
                             Boolean isStartWithSavePoint) {
        try {
            if (isBlank(format)) {
                format = "json";
            }

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url(clientId, "/submit-job"))
                    .queryParam("format", format.trim());

            appendSubmitJobParams(builder, jobId, jobName, isStartWithSavePoint);

            return post(
                    clientId,
                    builder.build(true).toUriString(),
                    configText == null ? "" : configText,
                    textHeaders(clientId),
                    Map.class,
                    "POST /submit-job(text) failed"
            );
        } catch (Exception e) {
            throw wrap(e, "POST /submit-job(text) failed");
        }
    }

    public Map submitJobJson(Long clientId,
                             Object configJsonObject,
                             String jobId,
                             String jobName,
                             Boolean isStartWithSavePoint) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url(clientId, "/submit-job"))
                    .queryParam("format", "json");

            appendSubmitJobParams(builder, jobId, jobName, isStartWithSavePoint);

            return post(
                    clientId,
                    builder.build(true).toUriString(),
                    configJsonObject,
                    jsonHeaders(clientId),
                    Map.class,
                    "POST /submit-job(json) failed"
            );
        } catch (Exception e) {
            throw wrap(e, "POST /submit-job(json) failed");
        }
    }

    public Map submitJobUpload(Long clientId, byte[] fileBytes, String filename) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            ByteArrayResource resource = new ByteArrayResource(fileBytes == null ? new byte[0] : fileBytes) {
                @Override
                public String getFilename() {
                    return isBlank(filename) ? DEFAULT_CONFIG_FILE_NAME : filename.trim();
                }
            };

            body.add("config_file", resource);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(
                    body,
                    multipartHeaders(clientId)
            );

            ResponseEntity<Map> response = restTemplate.exchange(
                    url(clientId, "/submit-job/upload"),
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw wrap(e, "POST /submit-job/upload failed");
        }
    }

    public List submitJobsBatch(Long clientId, List jobConfigs) {
        return post(
                clientId,
                url(clientId, "/submit-jobs"),
                jobConfigs,
                jsonHeaders(clientId),
                List.class,
                "POST /submit-jobs failed"
        );
    }

    public Map stopJob(Long clientId, long jobId, boolean isStopWithSavePoint) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("jobId", jobId);
        body.put("isStopWithSavePoint", isStopWithSavePoint);

        return post(
                clientId,
                url(clientId, "/stop-job"),
                body,
                jsonHeaders(clientId),
                Map.class,
                "POST /stop-job failed"
        );
    }

    public List stopJobsBatch(Long clientId, List<Map<String, Object>> items) {
        return post(
                clientId,
                url(clientId, "/stop-jobs"),
                items,
                jsonHeaders(clientId),
                List.class,
                "POST /stop-jobs failed"
        );
    }

    /* ===================== Query Params ===================== */

    private void appendQueryParams(UriComponentsBuilder builder, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!isBlank(entry.getKey()) && entry.getValue() != null) {
                builder.queryParam(entry.getKey().trim(), entry.getValue());
            }
        }
    }

    private void appendSubmitJobParams(UriComponentsBuilder builder,
                                       String jobId,
                                       String jobName,
                                       Boolean isStartWithSavePoint) {
        if (!isBlank(jobId)) {
            builder.queryParam("jobId", jobId.trim());
        }

        if (!isBlank(jobName)) {
            builder.queryParam("jobName", jobName.trim());
        }

        if (isStartWithSavePoint != null) {
            builder.queryParam("isStartWithSavePoint", isStartWithSavePoint);
        }
    }
}
