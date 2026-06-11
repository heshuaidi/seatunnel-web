package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.repository.SyncTaskDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskVersionDao;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HoconRenderServiceImpl implements HoconRenderService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private SyncTaskDao syncTaskDao;

    @Resource
    private SyncTaskVersionDao syncTaskVersionDao;

    @Override
    public String render(String template, Map<String, Object> variables) {
        if (template == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "template");
        }

        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (!safeVariables.containsKey(variableName) || safeVariables.get(variableName) == null) {
                throw new ServiceException("Missing HOCON template variable: " + variableName);
            }

            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(formatValue(safeVariables.get(variableName)))
            );
        }

        matcher.appendTail(result);
        return result.toString();
    }

    @Override
    public String preview(Long taskId, Map<String, Object> overrideParams) {
        if (taskId == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskId");
        }

        SyncTaskEntity task = syncTaskDao.queryById(taskId);
        if (task == null) {
            throw new ServiceException("Sync task not found, taskId=" + taskId);
        }

        SyncTaskVersionEntity version = loadTaskVersion(task);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("task_id", task.getId());
        variables.put("task_code", task.getTaskCode());
        variables.put("task_name", task.getTaskName());
        variables.put("run_id", "PREVIEW_RUN");
        variables.put("batch_id", "PREVIEW_BATCH");

        if (overrideParams != null) {
            variables.putAll(overrideParams);
        }

        return render(version.getHoconTemplate(), variables);
    }

    @Override
    public String calculateHash(String hocon) {
        if (hocon == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(hocon.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new ServiceException("Calculate HOCON hash failed: " + e.getMessage(), e);
        }
    }

    private SyncTaskVersionEntity loadTaskVersion(SyncTaskEntity task) {
        SyncTaskVersionEntity version = null;
        if (task.getCurrentVersionId() != null) {
            version = syncTaskVersionDao.queryById(task.getCurrentVersionId());
        }
        if (version == null) {
            version = syncTaskVersionDao.queryLatestByTaskId(task.getId());
        }
        if (version == null) {
            throw new ServiceException("Sync task version not found, taskId=" + task.getId());
        }
        return version;
    }

    private String formatValue(Object value) {
        if (value instanceof LocalDateTime) {
            return DATE_TIME_FORMATTER.format((LocalDateTime) value);
        }
        if (value instanceof LocalDate) {
            return DATE_TIME_FORMATTER.format(((LocalDate) value).atStartOfDay());
        }
        if (value instanceof Date) {
            return DATE_TIME_FORMATTER.format(
                    LocalDateTime.ofInstant(((Date) value).toInstant(), java.time.ZoneId.systemDefault())
            );
        }
        return String.valueOf(value);
    }
}
