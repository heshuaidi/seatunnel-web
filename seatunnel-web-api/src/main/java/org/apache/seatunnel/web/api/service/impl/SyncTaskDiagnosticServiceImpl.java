package org.apache.seatunnel.web.api.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.DataSourceService;
import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.SyncBatchService;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.api.service.SyncCheckSqlExecutor;
import org.apache.seatunnel.web.api.service.SyncIncrementalConfigService;
import org.apache.seatunnel.web.api.service.SyncRunService;
import org.apache.seatunnel.web.api.service.SyncTaskDiagnosticService;
import org.apache.seatunnel.web.api.service.SyncTaskService;
import org.apache.seatunnel.web.api.service.SyncTaskVersionService;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.api.utils.HoconSensitiveMaskUtil;
import org.apache.seatunnel.web.api.utils.SyncCheckCompareUtils;
import org.apache.seatunnel.web.api.utils.SyncSensitiveMaskUtils;
import org.apache.seatunnel.web.common.constants.SyncConstants;
import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.common.enums.SyncCheckType;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SeaTunnelClient;
import org.apache.seatunnel.web.dao.entity.SyncAuditEntity;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.dao.repository.SeaTunnelClientDao;
import org.apache.seatunnel.web.spi.bean.dto.SyncCheckDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncHoconDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncRangePreviewRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncTaskDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncWatermarkUpdateRequest;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.vo.SyncAuditItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBatchListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncCheckDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncHoconDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncRangePreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncRunListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncTaskDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SyncTaskDiagnosticServiceImpl extends SyncServiceSupport implements SyncTaskDiagnosticService {

    private static final int PREVIEW_LIMIT = 2000;

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<String> SENSITIVE_KEYWORDS =
            Set.of("password", "passwd", "secret", "token", "key");

    @Resource
    private SyncTaskService syncTaskService;

    @Resource
    private SyncTaskVersionService syncTaskVersionService;

    @Resource
    private SyncIncrementalConfigService syncIncrementalConfigService;

    @Resource
    private SyncWatermarkService syncWatermarkService;

    @Resource
    private SyncCheckConfigService syncCheckConfigService;

    @Resource
    private SyncCheckSqlExecutor syncCheckSqlExecutor;

    @Resource
    private SyncRunService syncRunService;

    @Resource
    private SyncBatchService syncBatchService;

    @Resource
    private SyncAuditService syncAuditService;

    @Resource
    private HoconRenderService hoconRenderService;

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private SeaTunnelClientDao seaTunnelClientDao;

    @Override
    public SyncTaskDiagnosticVO diagnoseTask(String taskCode, SyncTaskDiagnoseRequest request) {
        SyncTaskDiagnoseRequest safeRequest = request == null ? new SyncTaskDiagnoseRequest() : request;
        Map<String, Object> params = nullToEmpty(safeRequest.getParams());
        List<String> messages = new ArrayList<>();

        SyncTaskEntity task = loadTask(taskCode);
        SyncTaskVersionEntity version = loadVersionIfExists(task);
        SyncIncrementalConfigEntity config = loadConfigIfExists(task.getId());
        SyncWatermarkEntity watermark = loadWatermark(task, config);

        if (task.getStatus() != SyncTaskStatus.PUBLISHED) {
            messages.add("WARN: task.status is not PUBLISHED, current=" + enumCode(task.getStatus()));
        }
        if (version == null) {
            messages.add("ERROR: task version does not exist");
        }
        if (isIncrementalEnabled(task) && config == null) {
            messages.add("ERROR: incremental_config does not exist");
        }
        if (task.getClientId() == null) {
            messages.add("ERROR: task.clientId is empty");
        }

        SyncRangePreviewVO rangePreview = null;
        if (isIncrementalEnabled(task) && config != null && !isFileTask(task, config)) {
            try {
                SyncRangePreviewRequest rangeRequest = new SyncRangePreviewRequest();
                rangeRequest.setRunMode(SyncRunMode.NORMAL.getCode());
                rangeRequest.setParams(params);
                rangePreview = previewRange(taskCode, rangeRequest);
                if (rangePreview.getWarnings() != null) {
                    rangePreview.getWarnings().forEach(item -> messages.add("WARN: " + item));
                }
            } catch (Exception e) {
                messages.add("ERROR: " + messageOf(e));
            }
        } else if (isFileTask(task, config)) {
            messages.add("WARN: file source diagnostics are paused in this round");
        }

        SyncHoconDiagnosticVO hocon = null;
        if (Boolean.TRUE.equals(safeRequest.getIncludeHoconPreview()) && version != null) {
            try {
                SyncHoconDiagnoseRequest hoconRequest = new SyncHoconDiagnoseRequest();
                hoconRequest.setParams(params);
                hoconRequest.setIncludeRenderedHocon(false);
                hocon = diagnoseHocon(taskCode, hoconRequest);
                if (!Boolean.TRUE.equals(hocon.getRenderable())) {
                    messages.add("ERROR: HOCON is not renderable");
                }
            } catch (Exception e) {
                hocon = failedHoconDiagnostic(params, messageOf(e));
                messages.add("ERROR: " + messageOf(e));
            }
        }

        SyncCheckDiagnosticVO checks = null;
        if (Boolean.TRUE.equals(safeRequest.getIncludeCheckPreview())) {
            SyncCheckDiagnoseRequest checkRequest = new SyncCheckDiagnoseRequest();
            checkRequest.setParams(params);
            checkRequest.setExecuteSql(false);
            checks = diagnoseChecks(taskCode, checkRequest);
            if (!Boolean.TRUE.equals(checks.getRenderable())) {
                messages.add("ERROR: one or more check SQL templates are not renderable");
            }
            if (checks.getMissingDatasourceIds() != null && !checks.getMissingDatasourceIds().isEmpty()) {
                messages.add("WARN: check datasourceId is missing or invalid: " + checks.getMissingDatasourceIds());
            }
        } else {
            checks = checkSummary(task, Boolean.TRUE.equals(safeRequest.getIncludeDatasourceCheck()));
            if (checks.getMissingDatasourceIds() != null && !checks.getMissingDatasourceIds().isEmpty()) {
                messages.add("WARN: check datasourceId is missing or invalid: " + checks.getMissingDatasourceIds());
            }
        }

        SyncTaskDiagnosticVO vo = new SyncTaskDiagnosticVO();
        vo.setTask(toTaskInfo(task));
        vo.setVersion(toVersionInfo(version));
        vo.setIncrementalConfig(toConfigInfo(config));
        vo.setWatermark(toWatermarkInfo(watermark, config));
        vo.setRangePreview(rangePreview);
        vo.setHocon(hocon);
        vo.setChecks(checks);
        vo.setClient(toClientInfo(task.getClientId()));
        if (vo.getClient() != null && !isBlank(vo.getClient().getWarning())) {
            messages.add("WARN: " + vo.getClient().getWarning());
        }
        vo.setDiagnostics(toDiagnostics(messages));
        return vo;
    }

    @Override
    public SyncRangePreviewVO previewRange(String taskCode, SyncRangePreviewRequest request) {
        SyncTaskEntity task = loadTask(taskCode);
        SyncIncrementalConfigEntity config = loadConfigIfExists(task.getId());
        if (!isIncrementalEnabled(task)) {
            throw new ServiceException("Sync task incremental is disabled, taskCode=" + taskCode);
        }
        if (config == null) {
            throw new ServiceException("Sync incremental config not found, taskCode=" + taskCode);
        }
        if (isFileTask(task, config)) {
            throw new ServiceException("File source range preview is not supported in this round, taskCode=" + taskCode);
        }

        SyncRangePreviewRequest safeRequest = request == null ? new SyncRangePreviewRequest() : request;
        SyncRunMode runMode = parseRunMode(safeRequest.getRunMode(), SyncRunMode.NORMAL);
        WatermarkRange range = syncWatermarkService.previewRange(task.getId(), runMode, nullToEmpty(safeRequest.getParams()));
        return toRangePreview(task, config, range);
    }

    @Override
    public SyncHoconDiagnosticVO diagnoseHocon(String taskCode, SyncHoconDiagnoseRequest request) {
        SyncTaskEntity task = loadTask(taskCode);
        SyncTaskVersionEntity version = loadVersion(task);
        SyncIncrementalConfigEntity config = loadConfigIfExists(task.getId());
        Map<String, Object> params = request == null ? Map.of() : nullToEmpty(request.getParams());
        Boolean includeRenderedHocon = request != null && Boolean.TRUE.equals(request.getIncludeRenderedHocon());

        WatermarkRange range = null;
        if (isIncrementalEnabled(task) && config != null && !isFileTask(task, config)) {
            try {
                range = syncWatermarkService.previewRange(task.getId(), SyncRunMode.NORMAL, params);
            } catch (Exception e) {
                SyncHoconDiagnosticVO vo = failedHoconDiagnostic(params, messageOf(e));
                vo.setVariablesUsed(hoconRenderService.extractVariables(version.getHoconTemplate()));
                vo.setMissingVariables(hoconRenderService.findMissingVariables(version.getHoconTemplate(), params));
                return vo;
            }
        }

        Map<String, Object> variables = buildVariables(task, version, null, null, range, SyncRunMode.NORMAL, config, params);
        return diagnoseHoconWithVariables(version.getHoconTemplate(), variables, params, includeRenderedHocon);
    }

    @Override
    public SyncCheckDiagnosticVO diagnoseChecks(String taskCode, SyncCheckDiagnoseRequest request) {
        SyncTaskEntity task = loadTask(taskCode);
        SyncTaskVersionEntity version = loadVersionIfExists(task);
        SyncIncrementalConfigEntity config = loadConfigIfExists(task.getId());
        Map<String, Object> params = request == null ? Map.of() : nullToEmpty(request.getParams());
        boolean executeSql = request != null && Boolean.TRUE.equals(request.getExecuteSql());

        WatermarkRange range = null;
        if (isIncrementalEnabled(task) && config != null && !isFileTask(task, config)) {
            try {
                range = syncWatermarkService.previewRange(task.getId(), SyncRunMode.NORMAL, params);
            } catch (Exception ignored) {
                range = null;
            }
        }
        Map<String, Object> variables = version == null
                ? new LinkedHashMap<>(params)
                : buildVariables(task, version, null, null, range, SyncRunMode.NORMAL, config, params);
        variables.putAll(params);

        List<SyncCheckConfigEntity> configs = syncCheckConfigService.listByTaskId(task.getId());
        List<SyncCheckDiagnosticVO.CheckItemVO> items = new ArrayList<>();
        List<String> previews = new ArrayList<>();
        List<String> missingDatasourceIds = new ArrayList<>();
        Map<String, String> actualValueByCheckCode = new LinkedHashMap<>();
        boolean allRenderable = true;

        for (SyncCheckConfigEntity configEntity : configs) {
            SyncCheckDiagnosticVO.CheckItemVO item = diagnoseOneCheck(
                    configEntity,
                    variables,
                    params,
                    executeSql,
                    actualValueByCheckCode
            );
            items.add(item);
            if (!Boolean.TRUE.equals(item.getRenderable())) {
                allRenderable = false;
            }
            if (isBlank(configEntity.getDatasourceId() == null ? null : String.valueOf(configEntity.getDatasourceId()))) {
                missingDatasourceIds.add(configEntity.getCheckCode() + ": datasourceId is empty");
            } else if (!datasourceExists(configEntity.getDatasourceId())) {
                missingDatasourceIds.add(configEntity.getCheckCode() + ": datasourceId not found, datasourceId="
                        + configEntity.getDatasourceId());
            }
            if (Boolean.TRUE.equals(item.getRenderable()) && !isBlank(item.getRenderedSqlPreview())) {
                previews.add(item.getRenderedSqlPreview());
            }
            if (item.getActualValue() != null) {
                actualValueByCheckCode.put(configEntity.getCheckCode(), item.getActualValue());
            }
        }

        SyncCheckDiagnosticVO vo = new SyncCheckDiagnosticVO();
        vo.setCheckCount(configs.size());
        vo.setEnabledCheckCount((int) configs.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count());
        vo.setMissingDatasourceIds(missingDatasourceIds);
        vo.setRenderable(allRenderable);
        vo.setCheckSqlPreview(previews);
        vo.setChecks(items);
        vo.setMaskedParams(SyncSensitiveMaskUtils.maskMap(params));
        return vo;
    }

    @Override
    public PaginationResult<SyncRunListItemVO> listRuns(
            String taskCode,
            Integer pageNo,
            Integer pageSize,
            String status,
            String startTime,
            String endTime
    ) {
        SyncTaskEntity task = loadTask(taskCode);
        List<SyncRunListItemVO> filtered = syncRunService.listByTaskId(task.getId())
                .stream()
                .filter(item -> matchesStatus(status, enumCode(item.getStatus())))
                .filter(item -> matchesTimeRange(item.getCreateTime(), startTime, endTime))
                .map(item -> toRunListItem(task, item))
                .collect(Collectors.toList());
        return page(filtered, pageNo, pageSize);
    }

    @Override
    public PaginationResult<SyncBatchListItemVO> listBatches(
            String taskCode,
            Integer pageNo,
            Integer pageSize,
            String status,
            String startTime,
            String endTime
    ) {
        SyncTaskEntity task = loadTask(taskCode);
        List<SyncBatchListItemVO> filtered = syncBatchService.listByTaskId(task.getId())
                .stream()
                .filter(item -> matchesStatus(status, enumCode(item.getStatus())))
                .filter(item -> matchesTimeRange(item.getCreateTime(), startTime, endTime))
                .map(this::toBatchListItem)
                .collect(Collectors.toList());
        return page(filtered, pageNo, pageSize);
    }

    @Override
    public SyncBatchListItemVO getBatch(String batchId) {
        SyncBatchEntity batch = syncBatchService.getByBatchId(batchId);
        if (batch == null) {
            throw new ServiceException("Sync batch not found, batchId=" + batchId);
        }
        return toBatchListItem(batch);
    }

    @Override
    public PaginationResult<SyncAuditItemVO> listRunAudits(
            String runId,
            Integer pageNo,
            Integer pageSize,
            String startTime,
            String endTime
    ) {
        SyncRunEntity run = syncRunService.getByRunId(runId);
        if (run == null) {
            throw new ServiceException("Sync run not found, runId=" + runId);
        }
        List<SyncAuditItemVO> items = syncAuditService.listByRunId(runId)
                .stream()
                .filter(item -> matchesTimeRange(item.getCreateTime(), startTime, endTime))
                .map(this::toAuditVO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public PaginationResult<SyncAuditItemVO> listBatchAudits(
            String batchId,
            Integer pageNo,
            Integer pageSize,
            String startTime,
            String endTime
    ) {
        SyncBatchEntity batch = syncBatchService.getByBatchId(batchId);
        if (batch == null) {
            throw new ServiceException("Sync batch not found, batchId=" + batchId);
        }
        List<SyncAuditItemVO> items = syncAuditService.listByBatchId(batchId)
                .stream()
                .filter(item -> matchesTimeRange(item.getCreateTime(), startTime, endTime))
                .map(this::toAuditVO)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public WatermarkVO updateWatermark(String taskCode, SyncWatermarkUpdateRequest request) {
        SyncTaskEntity task = loadTask(taskCode);
        if (request == null) {
            throw new ServiceException("watermark update request is required");
        }
        if (isBlank(request.getCurrentValue())) {
            throw new ServiceException("currentValue is required");
        }
        if (isBlank(request.getReason())) {
            throw new ServiceException("reason is required");
        }

        String watermarkKey = defaultWatermarkKey(request.getWatermarkKey());
        SyncIncrementalConfigEntity config = syncIncrementalConfigService.getByTaskIdAndWatermarkKey(task.getId(), watermarkKey);
        if (config == null) {
            config = syncIncrementalConfigService.getByTaskId(task.getId());
        }
        if (config == null) {
            throw new ServiceException("Sync incremental config not found, taskCode=" + taskCode);
        }

        SyncWatermarkEntity existing = syncWatermarkService.getByTaskIdAndWatermarkKey(task.getId(), watermarkKey);
        String oldValue = existing == null ? null : existing.getCurrentValue();
        SyncWatermarkEntity updated;
        if (existing == null) {
            updated = SyncWatermarkEntity.builder()
                    .taskId(task.getId())
                    .watermarkKey(watermarkKey)
                    .currentValue(request.getCurrentValue())
                    .previousValue(null)
                    .currentValueType(config.getWatermarkFieldType())
                    .updateTime(now())
                    .build();
            syncWatermarkService.create(updated);
        } else {
            updated = new SyncWatermarkEntity();
            updated.setId(existing.getId());
            updated.setTaskId(existing.getTaskId());
            updated.setWatermarkKey(existing.getWatermarkKey());
            updated.setCurrentValue(request.getCurrentValue());
            updated.setPreviousValue(existing.getCurrentValue());
            updated.setCurrentValueType(existing.getCurrentValueType() == null
                    ? config.getWatermarkFieldType()
                    : existing.getCurrentValueType());
            updated.setLastSuccessRunId(existing.getLastSuccessRunId());
            updated.setLastSuccessBatchId(existing.getLastSuccessBatchId());
            syncWatermarkService.update(updated);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("watermarkKey", watermarkKey);
        detail.put("oldValue", oldValue);
        detail.put("newValue", request.getCurrentValue());
        detail.put("reason", request.getReason());
        syncAuditService.appendWarn(
                null,
                null,
                task.getId(),
                task.getTaskCode(),
                SyncAuditEventType.MANUAL_UPDATE_WATERMARK,
                "Manual update watermark",
                detail
        );
        return toWatermarkVO(task, updated);
    }

    private SyncCheckDiagnosticVO.CheckItemVO diagnoseOneCheck(
            SyncCheckConfigEntity config,
            Map<String, Object> variables,
            Map<String, Object> params,
            boolean executeSql,
            Map<String, String> actualValueByCheckCode
    ) {
        SyncCheckDiagnosticVO.CheckItemVO item = new SyncCheckDiagnosticVO.CheckItemVO();
        item.setCheckCode(config.getCheckCode());
        item.setCheckName(config.getCheckName());
        item.setCheckType(config.getCheckType() == null ? null : config.getCheckType().getCode());
        item.setDatasourceId(config.getDatasourceId());
        item.setEnabled(config.getEnabled());
        item.setExecuteSql(executeSql);
        item.setExecuted(false);

        List<String> missingVariables = hoconRenderService.findMissingVariables(config.getSqlText(), variables);
        item.setMissingVariables(missingVariables);
        if (!missingVariables.isEmpty()) {
            item.setRenderable(false);
            item.setErrorMessage("Missing check SQL template variables: " + missingVariables);
            return item;
        }

        try {
            String renderedSql = hoconRenderService.render(config.getSqlText(), variables);
            item.setRenderable(true);
            item.setRenderedSqlPreview(preview(maskSensitiveText(renderedSql, params)));
            if (!executeSql || !Boolean.TRUE.equals(config.getEnabled())) {
                return item;
            }

            item.setExecuted(true);
            Object scalar = syncCheckSqlExecutor.executeScalar(config, renderedSql);
            String actualValue = scalar == null ? null : String.valueOf(scalar);
            String expectedValue = SyncCheckCompareUtils.resolveExpectedValue(config, actualValueByCheckCode);
            item.setActualValue(actualValue);
            item.setPassed(evaluate(config, actualValue, expectedValue));
        } catch (Exception e) {
            item.setRenderable(Boolean.TRUE.equals(item.getRenderable()));
            item.setErrorMessage(messageOf(e));
            item.setPassed(false);
        }
        return item;
    }

    private SyncCheckDiagnosticVO checkSummary(SyncTaskEntity task, boolean includeDatasourceCheck) {
        List<SyncCheckConfigEntity> configs = syncCheckConfigService.listByTaskId(task.getId());
        List<String> missingDatasourceIds = new ArrayList<>();
        if (includeDatasourceCheck) {
            for (SyncCheckConfigEntity config : configs) {
                if (config.getDatasourceId() == null) {
                    missingDatasourceIds.add(config.getCheckCode() + ": datasourceId is empty");
                } else if (!datasourceExists(config.getDatasourceId())) {
                    missingDatasourceIds.add(config.getCheckCode() + ": datasourceId not found, datasourceId="
                            + config.getDatasourceId());
                }
            }
        } else {
            configs.stream()
                    .filter(item -> item.getDatasourceId() == null)
                    .map(item -> item.getCheckCode() + ": datasourceId is empty")
                    .forEach(missingDatasourceIds::add);
        }

        SyncCheckDiagnosticVO vo = new SyncCheckDiagnosticVO();
        vo.setCheckCount(configs.size());
        vo.setEnabledCheckCount((int) configs.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count());
        vo.setMissingDatasourceIds(missingDatasourceIds);
        vo.setRenderable(true);
        vo.setCheckSqlPreview(Collections.emptyList());
        vo.setChecks(Collections.emptyList());
        vo.setMaskedParams(Collections.emptyMap());
        return vo;
    }

    private SyncHoconDiagnosticVO diagnoseHoconWithVariables(
            String template,
            Map<String, Object> variables,
            Map<String, Object> params,
            boolean includeRenderedHocon
    ) {
        SyncHoconDiagnosticVO vo = new SyncHoconDiagnosticVO();
        vo.setVariablesUsed(hoconRenderService.extractVariables(template));
        vo.setMissingVariables(hoconRenderService.findMissingVariables(template, variables));
        vo.setMaskedParams(SyncSensitiveMaskUtils.maskMap(params));
        vo.setRenderable(vo.getMissingVariables().isEmpty());
        if (!Boolean.TRUE.equals(vo.getRenderable())) {
            return vo;
        }

        try {
            String rendered = hoconRenderService.render(template, variables);
            vo.setRenderedHash(hoconRenderService.calculateHash(rendered));
            String masked = maskSensitiveHocon(rendered, params);
            vo.setRenderedHoconPreview(preview(masked));
            if (includeRenderedHocon) {
                vo.setRenderedHocon(masked);
            }
        } catch (Exception e) {
            vo.setRenderable(false);
            vo.setErrorMessage(messageOf(e));
        }
        return vo;
    }

    private SyncHoconDiagnosticVO failedHoconDiagnostic(Map<String, Object> params, String errorMessage) {
        SyncHoconDiagnosticVO vo = new SyncHoconDiagnosticVO();
        vo.setRenderable(false);
        vo.setMissingVariables(Collections.emptyList());
        vo.setVariablesUsed(Collections.emptySet());
        vo.setMaskedParams(SyncSensitiveMaskUtils.maskMap(params));
        vo.setErrorMessage(errorMessage);
        return vo;
    }

    private Map<String, Object> buildVariables(
            SyncTaskEntity task,
            SyncTaskVersionEntity version,
            String runId,
            String batchId,
            WatermarkRange range,
            SyncRunMode runMode,
            SyncIncrementalConfigEntity config,
            Map<String, Object> params
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.putAll(params == null ? Map.of() : params);
        variables.put("task_id", task.getId());
        variables.put("task_code", task.getTaskCode());
        variables.put("task_name", task.getTaskName());
        variables.put("task_version_id", version.getId());
        variables.put("run_id", isBlank(runId) ? "DIAGNOSE_RUN" : runId);
        variables.put("batch_id", isBlank(batchId) ? "DIAGNOSE_BATCH" : batchId);
        variables.put("trigger_type", "MANUAL");
        variables.put("run_mode", runMode == null ? SyncRunMode.NORMAL.getCode() : runMode.getCode());
        if (range == null) {
            variables.put("last_watermark", "");
            variables.put("previous_watermark", "");
            variables.put("batch_start_value", "");
            variables.put("batch_end_value", "");
            variables.put("batch_start_time", "");
            variables.put("batch_end_time", "");
            variables.put("watermark_key", "");
        } else {
            variables.put("last_watermark", range.getCurrentWatermark() == null ? range.getStartValue() : range.getCurrentWatermark());
            variables.put("previous_watermark", range.getStartValue());
            variables.put("batch_start_value", range.getStartValue());
            variables.put("batch_end_value", range.getEndValue());
            variables.put("batch_start_time", range.getStartTime());
            variables.put("batch_end_time", range.getEndTime());
            variables.put("watermark_key", range.getWatermarkKey());
        }
        variables.put("lookback_seconds", config == null || config.getLookbackSeconds() == null ? 0 : config.getLookbackSeconds());
        variables.put("biz_date", LocalDate.now().toString());
        variables.put("watermark_field", config == null || isBlank(config.getWatermarkField()) ? "" : config.getWatermarkField());
        return variables;
    }

    private SyncTaskEntity loadTask(String taskCode) {
        if (isBlank(taskCode)) {
            throw new ServiceException("taskCode is required");
        }
        SyncTaskEntity task = syncTaskService.getByTaskCode(taskCode);
        if (task == null) {
            throw new ServiceException("Sync task not found, taskCode=" + taskCode);
        }
        return task;
    }

    private SyncTaskVersionEntity loadVersion(SyncTaskEntity task) {
        SyncTaskVersionEntity version = loadVersionIfExists(task);
        if (version == null) {
            throw new ServiceException("Sync task version not found, taskCode=" + task.getTaskCode());
        }
        return version;
    }

    private SyncTaskVersionEntity loadVersionIfExists(SyncTaskEntity task) {
        if (task.getCurrentVersionId() != null) {
            SyncTaskVersionEntity version = syncTaskVersionService.getById(task.getCurrentVersionId());
            if (version != null) {
                return version;
            }
        }
        return syncTaskVersionService.getByTaskId(task.getId());
    }

    private SyncIncrementalConfigEntity loadConfigIfExists(Long taskId) {
        return syncIncrementalConfigService.getByTaskId(taskId);
    }

    private SyncWatermarkEntity loadWatermark(SyncTaskEntity task, SyncIncrementalConfigEntity config) {
        String watermarkKey = config == null ? SyncConstants.DEFAULT_WATERMARK_KEY : defaultWatermarkKey(config.getWatermarkKey());
        return syncWatermarkService.getByTaskIdAndWatermarkKey(task.getId(), watermarkKey);
    }

    private SyncTaskDiagnosticVO.TaskInfoVO toTaskInfo(SyncTaskEntity task) {
        SyncTaskDiagnosticVO.TaskInfoVO vo = new SyncTaskDiagnosticVO.TaskInfoVO();
        vo.setTaskId(task.getId());
        vo.setTaskCode(task.getTaskCode());
        vo.setTaskName(task.getTaskName());
        vo.setStatus(enumCode(task.getStatus()));
        vo.setTaskType(enumCode(task.getTaskType()));
        vo.setSourceType(enumCode(task.getSourceType()));
        vo.setSinkType(enumCode(task.getSinkType()));
        vo.setEngineType(enumCode(task.getEngineType()));
        vo.setClientId(task.getClientId());
        vo.setIncrementalEnabled(isIncrementalEnabled(task));
        vo.setIncrementalStrategy(enumCode(task.getIncrementalStrategy()));
        return vo;
    }

    private SyncTaskDiagnosticVO.VersionInfoVO toVersionInfo(SyncTaskVersionEntity version) {
        SyncTaskDiagnosticVO.VersionInfoVO vo = new SyncTaskDiagnosticVO.VersionInfoVO();
        vo.setExists(version != null);
        if (version != null) {
            vo.setVersionId(version.getId());
            vo.setVersionNo(version.getVersionNo());
            vo.setPublishStatus(enumCode(version.getPublishStatus()));
            vo.setHoconHash(version.getHoconHash());
        }
        return vo;
    }

    private SyncTaskDiagnosticVO.IncrementalConfigInfoVO toConfigInfo(SyncIncrementalConfigEntity config) {
        SyncTaskDiagnosticVO.IncrementalConfigInfoVO vo = new SyncTaskDiagnosticVO.IncrementalConfigInfoVO();
        vo.setExists(config != null);
        if (config != null) {
            vo.setStrategy(enumCode(config.getStrategy()));
            vo.setWatermarkField(config.getWatermarkField());
            vo.setWatermarkFieldType(enumCode(config.getWatermarkFieldType()));
            vo.setStartValue(config.getStartValue());
            vo.setLookbackSeconds(config.getLookbackSeconds());
            vo.setMaxBatchSeconds(config.getMaxBatchSeconds());
        }
        return vo;
    }

    private SyncTaskDiagnosticVO.WatermarkInfoVO toWatermarkInfo(
            SyncWatermarkEntity watermark,
            SyncIncrementalConfigEntity config
    ) {
        SyncTaskDiagnosticVO.WatermarkInfoVO vo = new SyncTaskDiagnosticVO.WatermarkInfoVO();
        vo.setExists(watermark != null);
        vo.setWatermarkKey(watermark == null
                ? config == null ? SyncConstants.DEFAULT_WATERMARK_KEY : defaultWatermarkKey(config.getWatermarkKey())
                : watermark.getWatermarkKey());
        if (watermark != null) {
            vo.setCurrentValue(watermark.getCurrentValue());
            vo.setPreviousValue(watermark.getPreviousValue());
            vo.setCurrentValueType(enumCode(watermark.getCurrentValueType()));
            vo.setLastSuccessRunId(watermark.getLastSuccessRunId());
            vo.setLastSuccessBatchId(watermark.getLastSuccessBatchId());
        }
        return vo;
    }

    private SyncTaskDiagnosticVO.ClientInfoVO toClientInfo(Long clientId) {
        SyncTaskDiagnosticVO.ClientInfoVO vo = new SyncTaskDiagnosticVO.ClientInfoVO();
        vo.setClientId(clientId);
        if (clientId == null) {
            vo.setExists(false);
            vo.setWarning("task clientId is empty");
            return vo;
        }
        SeaTunnelClient client = seaTunnelClientDao.selectById(clientId);
        vo.setExists(client != null);
        if (client == null) {
            vo.setWarning("SeaTunnel client not found, clientId=" + clientId);
        }
        return vo;
    }

    private SyncTaskDiagnosticVO.DiagnosticsVO toDiagnostics(List<String> messages) {
        SyncTaskDiagnosticVO.DiagnosticsVO vo = new SyncTaskDiagnosticVO.DiagnosticsVO();
        if (messages == null || messages.isEmpty()) {
            vo.setLevel("OK");
            vo.setMessages(List.of("OK"));
            return vo;
        }
        if (messages.stream().anyMatch(item -> item.startsWith("ERROR:"))) {
            vo.setLevel("ERROR");
        } else if (messages.stream().anyMatch(item -> item.startsWith("WARN:"))) {
            vo.setLevel("WARN");
        } else {
            vo.setLevel("OK");
        }
        vo.setMessages(messages);
        return vo;
    }

    private SyncRangePreviewVO toRangePreview(
            SyncTaskEntity task,
            SyncIncrementalConfigEntity config,
            WatermarkRange range
    ) {
        SyncRangePreviewVO vo = new SyncRangePreviewVO();
        vo.setTaskCode(task.getTaskCode());
        vo.setStrategy(enumCode(config.getStrategy()));
        vo.setWatermarkKey(range.getWatermarkKey());
        vo.setCurrentWatermark(range.getCurrentWatermark());
        vo.setStartValue(range.getStartValue());
        vo.setEndValue(range.getEndValue());
        vo.setStartTime(formatDateTime(range.getStartTime()));
        vo.setEndTime(formatDateTime(range.getEndTime()));
        vo.setLookbackApplied(Boolean.TRUE.equals(range.getLookbackApplied()));
        vo.setMaxBatchSecondsApplied(Boolean.TRUE.equals(range.getMaxBatchSecondsApplied()));
        vo.setWillAdvanceWatermark(range.isAdvanceWatermark());
        vo.setWarnings(range.getWarnings() == null ? Collections.emptyList() : range.getWarnings());
        return vo;
    }

    private SyncRunListItemVO toRunListItem(SyncTaskEntity task, SyncRunEntity entity) {
        SyncRunListItemVO vo = new SyncRunListItemVO();
        vo.setRunId(entity.getRunId());
        vo.setBatchId(entity.getBatchId());
        vo.setTaskCode(task.getTaskCode());
        vo.setStatus(enumCode(entity.getStatus()));
        vo.setSeatunnelJobId(entity.getSeatunnelJobId());
        vo.setSubmitTime(formatDate(entity.getSubmitTime()));
        vo.setStartTime(formatDate(entity.getStartTime()));
        vo.setEndTime(formatDate(entity.getEndTime()));
        vo.setSourceCount(entity.getSourceCount());
        vo.setSinkCount(entity.getSinkCount());
        vo.setErrorCount(entity.getErrorCount());
        vo.setErrorMessage(entity.getErrorMessage());
        return vo;
    }

    private SyncBatchListItemVO toBatchListItem(SyncBatchEntity entity) {
        SyncBatchListItemVO vo = new SyncBatchListItemVO();
        vo.setBatchId(entity.getBatchId());
        vo.setTaskCode(entity.getTaskCode());
        vo.setStatus(enumCode(entity.getStatus()));
        vo.setBatchStartValue(entity.getBatchStartValue());
        vo.setBatchEndValue(entity.getBatchEndValue());
        vo.setBatchStartTime(formatDate(entity.getBatchStartTime()));
        vo.setBatchEndTime(formatDate(entity.getBatchEndTime()));
        vo.setSourceCount(entity.getSourceCount());
        vo.setSinkCount(entity.getSinkCount());
        vo.setErrorCount(entity.getErrorCount());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreateTime(formatDate(entity.getCreateTime()));
        vo.setUpdateTime(formatDate(entity.getUpdateTime()));
        return vo;
    }

    private SyncAuditItemVO toAuditVO(SyncAuditEntity audit) {
        SyncAuditItemVO vo = new SyncAuditItemVO();
        vo.setId(audit.getId());
        vo.setRunId(audit.getRunId());
        vo.setBatchId(audit.getBatchId());
        vo.setTaskId(audit.getTaskId());
        vo.setTaskCode(audit.getTaskCode());
        vo.setEventType(enumCode(audit.getEventType()));
        vo.setEventLevel(enumCode(audit.getEventLevel()));
        vo.setEventMessage(audit.getEventMessage());
        vo.setDetailJson(maskDetailJson(audit.getDetailJson()));
        vo.setCreateTime(formatDate(audit.getCreateTime()));
        return vo;
    }

    private WatermarkVO toWatermarkVO(SyncTaskEntity task, SyncWatermarkEntity watermark) {
        WatermarkVO vo = new WatermarkVO();
        vo.setId(watermark.getId());
        vo.setTaskId(watermark.getTaskId());
        vo.setTaskCode(task.getTaskCode());
        vo.setWatermarkKey(watermark.getWatermarkKey());
        vo.setCurrentValue(watermark.getCurrentValue());
        vo.setPreviousValue(watermark.getPreviousValue());
        vo.setCurrentValueType(enumCode(watermark.getCurrentValueType()));
        vo.setLastSuccessRunId(watermark.getLastSuccessRunId());
        vo.setLastSuccessBatchId(watermark.getLastSuccessBatchId());
        vo.setUpdateTime(formatDate(watermark.getUpdateTime()));
        return vo;
    }

    private boolean evaluate(SyncCheckConfigEntity config, String actualValue, String expectedValue) {
        if (config.getCheckType() == SyncCheckType.CUSTOM_BOOLEAN
                && config.getExpectedOperator() == null
                && isBlank(config.getCompareToCheckCode())) {
            return SyncCheckCompareUtils.toBoolean(actualValue);
        }
        return SyncCheckCompareUtils.compare(actualValue, config.getExpectedOperator(), expectedValue);
    }

    private boolean datasourceExists(Long datasourceId) {
        if (datasourceId == null) {
            return false;
        }
        try {
            return dataSourceService.selectById(datasourceId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isIncrementalEnabled(SyncTaskEntity task) {
        return task.getIncrementalEnabled() == null || Boolean.TRUE.equals(task.getIncrementalEnabled());
    }

    private boolean isFileTask(SyncTaskEntity task, SyncIncrementalConfigEntity config) {
        SyncSourceType sourceType = config == null || config.getSourceType() == null
                ? task.getSourceType()
                : config.getSourceType();
        return sourceType == SyncSourceType.LOCAL_FILE || sourceType == SyncSourceType.FTP_FILE;
    }

    private boolean matchesStatus(String expected, String actual) {
        if (isBlank(expected)) {
            return true;
        }
        return Objects.equals(expected.trim().toUpperCase(), actual == null ? null : actual.toUpperCase());
    }

    private boolean matchesTimeRange(Date value, String startTime, String endTime) {
        if (value == null) {
            return isBlank(startTime) && isBlank(endTime);
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
        if (!isBlank(startTime) && dateTime.isBefore(parseDateTime(startTime))) {
            return false;
        }
        return isBlank(endTime) || !dateTime.isAfter(parseDateTime(endTime));
    }

    private <T> PaginationResult<T> page(List<T> items, Integer pageNo, Integer pageSize) {
        int safePageNo = pageNo == null || pageNo <= 0 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize <= 0 ? 10 : pageSize;
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return PaginationResult.buildSuc(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    private SyncRunMode parseRunMode(String value, SyncRunMode defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return SyncRunMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("Unsupported runMode: " + value);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            throw new ServiceException("Invalid datetime value, expected yyyy-MM-dd HH:mm:ss: " + value);
        }
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(value);
    }

    private String enumCode(Enum<?> value) {
        if (value == null) {
            return null;
        }
        try {
            return String.valueOf(value.getClass().getMethod("getCode").invoke(value));
        } catch (Exception e) {
            return value.name();
        }
    }

    private String preview(String value) {
        if (value == null || value.length() <= PREVIEW_LIMIT) {
            return value;
        }
        return value.substring(0, PREVIEW_LIMIT);
    }

    private String maskSensitiveHocon(String value, Map<String, Object> params) {
        return maskSensitiveText(HoconSensitiveMaskUtil.maskSensitiveInfo(value), params);
    }

    private String maskSensitiveText(String value, Map<String, Object> params) {
        if (value == null) {
            return null;
        }
        String result = value;
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        for (Map.Entry<String, Object> entry : safeParams.entrySet()) {
            if (isSensitiveKey(entry.getKey()) && entry.getValue() != null) {
                result = result.replace(String.valueOf(entry.getValue()), "******");
            }
        }
        return result;
    }

    private String maskDetailJson(String detailJson) {
        if (isBlank(detailJson)) {
            return detailJson;
        }
        try {
            Object detail = JSONUtils.parseObject(detailJson, new TypeReference<Object>() {
            });
            return JSONUtils.toJsonString(SyncSensitiveMaskUtils.mask(detail));
        } catch (Exception e) {
            return HoconSensitiveMaskUtil.maskSensitiveInfo(detailJson);
        }
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lowerKey = key.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(lowerKey::contains);
    }

    private String defaultWatermarkKey(String watermarkKey) {
        return isBlank(watermarkKey) ? SyncConstants.DEFAULT_WATERMARK_KEY : watermarkKey;
    }

    private Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String messageOf(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }
}
