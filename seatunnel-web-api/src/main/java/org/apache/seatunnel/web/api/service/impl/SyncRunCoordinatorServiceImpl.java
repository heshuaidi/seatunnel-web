package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.SyncBatchService;
import org.apache.seatunnel.web.api.service.SyncRunCoordinatorService;
import org.apache.seatunnel.web.api.service.SyncRunService;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.api.service.SyncZetaClient;
import org.apache.seatunnel.web.api.service.model.SyncJobStatusResult;
import org.apache.seatunnel.web.api.service.model.SyncSubmitJobResult;
import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncAuditEntity;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.dao.repository.SyncIncrementalConfigDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskVersionDao;
import org.apache.seatunnel.web.spi.bean.dto.BackfillTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.PreviewHoconRequest;
import org.apache.seatunnel.web.spi.bean.dto.RunTaskRequest;
import org.apache.seatunnel.web.spi.bean.vo.HoconPreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncAuditItemVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SyncRunCoordinatorServiceImpl extends SyncServiceSupport implements SyncRunCoordinatorService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter ID_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private SyncTaskDao syncTaskDao;

    @Resource
    private SyncTaskVersionDao syncTaskVersionDao;

    @Resource
    private SyncIncrementalConfigDao syncIncrementalConfigDao;

    @Resource
    private SyncWatermarkService syncWatermarkService;

    @Resource
    private SyncBatchService syncBatchService;

    @Resource
    private SyncRunService syncRunService;

    @Resource
    private SyncAuditService syncAuditService;

    @Resource
    private HoconRenderService hoconRenderService;

    @Resource
    private SyncZetaClient syncZetaClient;

    @Resource
    private SyncRunProperties syncRunProperties;

    @Override
    public HoconPreviewVO previewHocon(String taskCode, PreviewHoconRequest request) {
        SyncTaskEntity task = loadTaskByCode(taskCode);
        SyncTaskVersionEntity version = loadRunnableVersion(task);
        Map<String, Object> params = request == null ? Map.of() : nullToEmpty(request.getParams());
        WatermarkRange range = syncWatermarkService.calculateNextRange(task.getId(), params);
        SyncIncrementalConfigEntity config = loadConfig(task.getId());
        Map<String, Object> variables = buildVariables(
                task,
                version,
                null,
                null,
                range,
                SyncTriggerType.MANUAL,
                SyncRunMode.NORMAL,
                config,
                params
        );
        variables.put("run_id", "PREVIEW_RUN");
        variables.put("batch_id", "PREVIEW_BATCH");

        String rendered = hoconRenderService.render(version.getHoconTemplate(), variables);
        HoconPreviewVO vo = new HoconPreviewVO();
        vo.setTaskId(task.getId());
        vo.setTaskCode(task.getTaskCode());
        vo.setTaskVersionId(version.getId());
        vo.setRenderedHocon(rendered);
        vo.setHoconHash(hoconRenderService.calculateHash(rendered));
        return vo;
    }

    @Override
    public RunResultVO runTask(String taskCode, RunTaskRequest request) {
        RunTaskRequest safeRequest = request == null ? new RunTaskRequest() : request;
        Map<String, Object> params = nullToEmpty(safeRequest.getParams());
        SyncTriggerType triggerType = parseTriggerType(safeRequest.getTriggerType(), SyncTriggerType.MANUAL);
        SyncRunMode runMode = parseRunMode(safeRequest.getRunMode(), SyncRunMode.NORMAL);
        boolean waitForFinish = safeRequest.getWaitForFinish() == null || Boolean.TRUE.equals(safeRequest.getWaitForFinish());

        return execute(taskCode, params, triggerType, runMode, waitForFinish, false, null);
    }

    @Override
    public RunResultVO backfillTask(String taskCode, BackfillTaskRequest request) {
        if (request == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "backfillRequest");
        }
        Map<String, Object> params = new LinkedHashMap<>(nullToEmpty(request.getParams()));
        putIfNotBlank(params, "startTime", request.getStartTime());
        putIfNotBlank(params, "endTime", request.getEndTime());
        putIfNotBlank(params, "startValue", request.getStartValue());
        putIfNotBlank(params, "endValue", request.getEndValue());
        boolean waitForFinish = request.getWaitForFinish() == null || Boolean.TRUE.equals(request.getWaitForFinish());

        return execute(
                taskCode,
                params,
                SyncTriggerType.BACKFILL,
                SyncRunMode.BACKFILL,
                waitForFinish,
                true,
                request.getAdvanceWatermark()
        );
    }

    @Override
    public RunDetailVO getRun(String runId) {
        SyncRunEntity run = syncRunService.getByRunId(runId);
        if (run == null) {
            throw new ServiceException("Sync run not found, runId=" + runId);
        }

        SyncTaskEntity task = syncTaskDao.queryById(run.getTaskId());
        SyncBatchEntity batch = isBlank(run.getBatchId()) ? null : syncBatchService.getByBatchId(run.getBatchId());
        List<SyncAuditEntity> audits = syncAuditService.listByRunId(run.getRunId());

        RunDetailVO vo = new RunDetailVO();
        vo.setTaskId(run.getTaskId());
        vo.setTaskCode(task == null ? null : task.getTaskCode());
        vo.setRunId(run.getRunId());
        vo.setBatchId(run.getBatchId());
        vo.setTaskVersionId(run.getTaskVersionId());
        vo.setTriggerType(run.getTriggerType() == null ? null : run.getTriggerType().getCode());
        vo.setRunStatus(run.getStatus() == null ? null : run.getStatus().getCode());
        vo.setBatchStatus(batch == null || batch.getStatus() == null ? null : batch.getStatus().getCode());
        vo.setSeatunnelJobId(run.getSeatunnelJobId());
        vo.setSeatunnelJobName(run.getSeatunnelJobName());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setGeneratedHocon(run.getGeneratedHocon());
        vo.setSourceCount(run.getSourceCount());
        vo.setSinkCount(run.getSinkCount());
        vo.setErrorCount(run.getErrorCount());
        vo.setCreateTime(formatDate(run.getCreateTime()));
        vo.setUpdateTime(formatDate(run.getUpdateTime()));
        vo.setSubmitTime(formatDate(run.getSubmitTime()));
        vo.setStartTime(formatDate(run.getStartTime()));
        vo.setEndTime(formatDate(run.getEndTime()));
        vo.setAudits(audits.stream().map(this::toAuditVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public List<WatermarkVO> getWatermark(String taskCode) {
        SyncTaskEntity task = loadTaskByCode(taskCode);
        List<SyncWatermarkEntity> watermarks = syncWatermarkService.listByTaskId(task.getId());
        return watermarks.stream().map(item -> toWatermarkVO(task, item)).collect(Collectors.toList());
    }

    private RunResultVO execute(
            String taskCode,
            Map<String, Object> params,
            SyncTriggerType triggerType,
            SyncRunMode runMode,
            boolean waitForFinish,
            boolean backfill,
            Boolean backfillAdvanceWatermark
    ) {
        SyncTaskEntity task = loadTaskByCode(taskCode);
        validateRunnableTask(task);
        SyncTaskVersionEntity version = loadRunnableVersion(task);
        SyncIncrementalConfigEntity config = loadConfig(task.getId());

        WatermarkRange range = null;
        SyncBatchEntity batch = null;
        SyncRunEntity run = null;

        try {
            syncAuditService.appendInfo(null, null, task.getId(), task.getTaskCode(),
                    SyncAuditEventType.READ_WATERMARK, "Start calculating watermark range", params);
            range = backfill
                    ? syncWatermarkService.calculateBackfillRange(task.getId(), params, backfillAdvanceWatermark)
                    : syncWatermarkService.calculateNextRange(task.getId(), params);
            syncAuditService.appendInfo(null, null, task.getId(), task.getTaskCode(),
                    SyncAuditEventType.READ_WATERMARK, "Watermark range calculated", range);

            batch = syncBatchService.createBatchForRun(task, range, triggerType, runMode);
            syncAuditService.appendInfo(null, batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.CREATE_BATCH, "Sync batch created", batch);

            updateBatchStatus(batch, SyncBatchStatus.READY, null);
            auditStatus(null, batch, task, SyncAuditEventType.CREATE_BATCH, "Sync batch is ready", SyncBatchStatus.READY);

            run = createRun(task, version, batch, triggerType, params);
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.CREATE_RUN, "Sync run created", run);

            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.RENDER_HOCON, "Start rendering HOCON", null);
            Map<String, Object> variables = buildVariables(task, version, run, batch, range, triggerType, runMode, config, params);
            String generatedHocon = hoconRenderService.render(version.getHoconTemplate(), variables);
            String hoconHash = hoconRenderService.calculateHash(generatedHocon);
            syncRunService.updateGeneratedHocon(run.getRunId(), generatedHocon);
            run.setGeneratedHocon(generatedHocon);
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.RENDER_HOCON, "HOCON rendered", Map.of("hoconHash", hoconHash));

            String jobName = buildSeatunnelJobName(task, run);
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.SUBMIT_JOB, "Start submitting SeaTunnel job", Map.of("jobName", jobName));
            SyncSubmitJobResult submitResult = syncZetaClient.submitJob(task.getClientId(), jobName, generatedHocon);
            syncRunService.updateSeatunnelJob(run.getRunId(), submitResult.getJobId(), submitResult.getJobName());
            run.setSeatunnelJobId(submitResult.getJobId());
            run.setSeatunnelJobName(submitResult.getJobName());
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.SUBMIT_JOB, "SeaTunnel job submitted", submitResult.getRawResponse());

            updateBatchStatus(batch, SyncBatchStatus.SUBMITTED, null);
            updateRunStatus(run, SyncRunStatus.SUBMITTED, null);

            if (!waitForFinish) {
                return toRunResult(task, version, batch, run, false);
            }

            updateBatchStatus(batch, SyncBatchStatus.RUNNING, null);
            updateRunStatus(run, SyncRunStatus.RUNNING, null);

            SyncJobStatusResult finalStatus = pollUntilFinished(task, batch, run);
            if (!finalStatus.isSuccess()) {
                throw new ServiceException("SeaTunnel job failed: " + finalStatus.getStatus()
                        + (isBlank(finalStatus.getErrorMessage()) ? "" : ", " + finalStatus.getErrorMessage()));
            }

            updateBatchStatus(batch, SyncBatchStatus.VERIFYING, null);
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.VERIFYING,
                    "SeaTunnel job success, minimal verification passed in current version",
                    finalStatus.getRawResponse());

            boolean watermarkAdvanced = false;
            if (range.isAdvanceWatermark()) {
                syncWatermarkService.advanceWatermark(
                        task.getId(),
                        range.getWatermarkKey(),
                        range.getEndValue(),
                        run.getId(),
                        batch.getBatchId()
                );
                watermarkAdvanced = true;
                syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                        SyncAuditEventType.ADVANCE_WATERMARK,
                        "Watermark advanced after successful run",
                        Map.of("newValue", range.getEndValue(), "watermarkKey", range.getWatermarkKey()));
            } else {
                syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                        SyncAuditEventType.ADVANCE_WATERMARK,
                        "Watermark advance skipped",
                        Map.of("backfill", range.isBackfill(), "advanceWatermark", range.isAdvanceWatermark()));
            }

            updateBatchStatus(batch, SyncBatchStatus.SUCCESS, null);
            updateRunStatus(run, SyncRunStatus.SUCCESS, null);
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.RUN_SUCCESS, "Sync run success", null);

            RunResultVO result = toRunResult(task, version, batch, run, watermarkAdvanced);
            result.setRunStatus(SyncRunStatus.SUCCESS.getCode());
            result.setBatchStatus(SyncBatchStatus.SUCCESS.getCode());
            return result;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            log.error("Sync run failed, taskCode={}, batchId={}, runId={}",
                    task.getTaskCode(),
                    batch == null ? null : batch.getBatchId(),
                    run == null ? null : run.getRunId(),
                    e);

            if (batch != null) {
                updateBatchStatus(batch, SyncBatchStatus.FAILED, message);
            }
            if (run != null) {
                updateRunStatus(run, SyncRunStatus.FAILED, message);
            }
            if (range != null) {
                syncWatermarkService.rollbackOrKeepWatermarkOnFailure(
                        task.getId(),
                        range.getWatermarkKey(),
                        run == null ? null : run.getId(),
                        batch == null ? null : batch.getBatchId()
                );
            }
            syncAuditService.appendError(
                    run == null ? null : run.getRunId(),
                    batch == null ? null : batch.getBatchId(),
                    task.getId(),
                    task.getTaskCode(),
                    SyncAuditEventType.RUN_FAILED,
                    "Sync run failed, watermark is not advanced",
                    Map.of("errorMessage", message)
            );
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            }
            throw new ServiceException(message, e);
        }
    }

    private SyncRunEntity createRun(
            SyncTaskEntity task,
            SyncTaskVersionEntity version,
            SyncBatchEntity batch,
            SyncTriggerType triggerType,
            Map<String, Object> params
    ) {
        Date now = now();
        SyncRunEntity run = SyncRunEntity.builder()
                .runId(generateRunId(task.getTaskCode()))
                .taskId(task.getId())
                .taskVersionId(version.getId())
                .batchId(batch.getBatchId())
                .triggerType(triggerType)
                .runParamJson(JSONUtils.toJsonString(params))
                .status(SyncRunStatus.CREATED)
                .createTime(now)
                .updateTime(now)
                .build();
        syncRunService.create(run);
        return run;
    }

    private SyncJobStatusResult pollUntilFinished(SyncTaskEntity task, SyncBatchEntity batch, SyncRunEntity run)
            throws InterruptedException {
        long started = System.currentTimeMillis();
        SyncJobStatusResult latest = null;

        while (System.currentTimeMillis() - started <= syncRunProperties.getPollTimeoutMs()) {
            latest = syncZetaClient.getJobStatus(task.getClientId(), run.getSeatunnelJobId());
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.POLL_STATUS, "SeaTunnel job status polled", latest);
            if (latest.isEndState()) {
                return latest;
            }
            Thread.sleep(syncRunProperties.getPollIntervalMs());
        }

        throw new ServiceException("SeaTunnel job poll timeout, jobId=" + run.getSeatunnelJobId()
                + ", lastStatus=" + (latest == null ? null : latest.getStatus()));
    }

    private Map<String, Object> buildVariables(
            SyncTaskEntity task,
            SyncTaskVersionEntity version,
            SyncRunEntity run,
            SyncBatchEntity batch,
            WatermarkRange range,
            SyncTriggerType triggerType,
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
        variables.put("run_id", run == null ? null : run.getRunId());
        variables.put("batch_id", batch == null ? null : batch.getBatchId());
        variables.put("trigger_type", triggerType.getCode());
        variables.put("run_mode", runMode.getCode());
        variables.put("last_watermark", lastWatermarkValue(task.getId(), range));
        variables.put("previous_watermark", previousWatermarkValue(task.getId(), range));
        variables.put("batch_start_value", range.getStartValue());
        variables.put("batch_end_value", range.getEndValue());
        variables.put("batch_start_time", range.getStartTime());
        variables.put("batch_end_time", range.getEndTime());
        variables.put("lookback_seconds", config.getLookbackSeconds() == null ? 0 : config.getLookbackSeconds());
        variables.put("biz_date", LocalDate.now().toString());
        variables.put("watermark_key", range.getWatermarkKey());
        variables.put("watermark_field", config.getWatermarkField());
        return variables;
    }

    private String lastWatermarkValue(Long taskId, WatermarkRange range) {
        SyncWatermarkEntity watermark = syncWatermarkService.getByTaskIdAndWatermarkKey(taskId, range.getWatermarkKey());
        if (watermark != null && !isBlank(watermark.getCurrentValue())) {
            return watermark.getCurrentValue();
        }
        return range.getStartValue();
    }

    private String previousWatermarkValue(Long taskId, WatermarkRange range) {
        SyncWatermarkEntity watermark = syncWatermarkService.getByTaskIdAndWatermarkKey(taskId, range.getWatermarkKey());
        if (watermark != null && !isBlank(watermark.getPreviousValue())) {
            return watermark.getPreviousValue();
        }
        return range.getStartValue();
    }

    private void updateBatchStatus(SyncBatchEntity batch, SyncBatchStatus status, String errorMessage) {
        syncBatchService.updateStatus(batch.getBatchId(), status, errorMessage);
        batch.setStatus(status);
        batch.setErrorMessage(errorMessage);
    }

    private void updateRunStatus(SyncRunEntity run, SyncRunStatus status, String errorMessage) {
        syncRunService.updateStatus(run.getRunId(), status, errorMessage);
        run.setStatus(status);
        run.setErrorMessage(errorMessage);
    }

    private void auditStatus(
            SyncRunEntity run,
            SyncBatchEntity batch,
            SyncTaskEntity task,
            SyncAuditEventType eventType,
            String message,
            Object detail
    ) {
        syncAuditService.appendInfo(
                run == null ? null : run.getRunId(),
                batch == null ? null : batch.getBatchId(),
                task.getId(),
                task.getTaskCode(),
                eventType,
                message,
                detail
        );
    }

    private SyncTaskEntity loadTaskByCode(String taskCode) {
        if (isBlank(taskCode)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskCode");
        }
        SyncTaskEntity task = syncTaskDao.queryByTaskCode(taskCode);
        if (task == null) {
            throw new ServiceException("Sync task not found, taskCode=" + taskCode);
        }
        return task;
    }

    private void validateRunnableTask(SyncTaskEntity task) {
        if (task.getStatus() != SyncTaskStatus.PUBLISHED) {
            throw new ServiceException("Only PUBLISHED sync task can run, taskCode=" + task.getTaskCode());
        }
        if (task.getClientId() == null) {
            throw new ServiceException("Sync task clientId is required for SeaTunnel Zeta submit, taskCode="
                    + task.getTaskCode());
        }
    }

    private SyncTaskVersionEntity loadRunnableVersion(SyncTaskEntity task) {
        if (task.getCurrentVersionId() == null) {
            throw new ServiceException("Sync task currentVersionId is required, taskCode=" + task.getTaskCode());
        }
        SyncTaskVersionEntity version = syncTaskVersionDao.queryById(task.getCurrentVersionId());
        if (version == null) {
            throw new ServiceException("Sync task current version not found, versionId=" + task.getCurrentVersionId());
        }
        return version;
    }

    private SyncIncrementalConfigEntity loadConfig(Long taskId) {
        SyncIncrementalConfigEntity config = syncIncrementalConfigDao.queryByTaskId(taskId);
        if (config == null) {
            throw new ServiceException("Sync incremental config not found, taskId=" + taskId);
        }
        return config;
    }

    private SyncTriggerType parseTriggerType(String value, SyncTriggerType defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return SyncTriggerType.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new ServiceException("Unsupported triggerType: " + value);
        }
    }

    private SyncRunMode parseRunMode(String value, SyncRunMode defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return SyncRunMode.valueOf(value.trim().toUpperCase());
        } catch (Exception e) {
            throw new ServiceException("Unsupported runMode: " + value);
        }
    }

    private String generateRunId(String taskCode) {
        return taskCode
                + "_run_"
                + ID_TIME_FORMATTER.format(LocalDateTime.now())
                + "_"
                + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String buildSeatunnelJobName(SyncTaskEntity task, SyncRunEntity run) {
        return task.getTaskCode() + "_" + run.getRunId();
    }

    private RunResultVO toRunResult(
            SyncTaskEntity task,
            SyncTaskVersionEntity version,
            SyncBatchEntity batch,
            SyncRunEntity run,
            boolean watermarkAdvanced
    ) {
        RunResultVO result = new RunResultVO();
        result.setRunId(run.getRunId());
        result.setBatchId(batch.getBatchId());
        result.setTaskId(task.getId());
        result.setTaskCode(task.getTaskCode());
        result.setTaskVersionId(version.getId());
        result.setSeatunnelJobId(run.getSeatunnelJobId());
        result.setSeatunnelJobName(run.getSeatunnelJobName());
        result.setRunStatus(run.getStatus() == null ? null : run.getStatus().getCode());
        result.setBatchStatus(batch.getStatus() == null ? null : batch.getStatus().getCode());
        result.setWatermarkAdvanced(watermarkAdvanced);
        result.setErrorMessage(run.getErrorMessage());
        return result;
    }

    private SyncAuditItemVO toAuditVO(SyncAuditEntity audit) {
        SyncAuditItemVO vo = new SyncAuditItemVO();
        vo.setId(audit.getId());
        vo.setRunId(audit.getRunId());
        vo.setBatchId(audit.getBatchId());
        vo.setTaskId(audit.getTaskId());
        vo.setTaskCode(audit.getTaskCode());
        vo.setEventType(audit.getEventType() == null ? null : audit.getEventType().getCode());
        vo.setEventLevel(audit.getEventLevel() == null ? null : audit.getEventLevel().getCode());
        vo.setEventMessage(audit.getEventMessage());
        vo.setDetailJson(audit.getDetailJson());
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
        vo.setCurrentValueType(watermark.getCurrentValueType() == null
                ? null
                : watermark.getCurrentValueType().getCode());
        vo.setLastSuccessRunId(watermark.getLastSuccessRunId());
        vo.setLastSuccessBatchId(watermark.getLastSuccessBatchId());
        vo.setUpdateTime(formatDate(watermark.getUpdateTime()));
        return vo;
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(
                LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())
        );
    }

    private Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private void putIfNotBlank(Map<String, Object> params, String key, String value) {
        if (!isBlank(value)) {
            params.put(key, value);
        }
    }
}
