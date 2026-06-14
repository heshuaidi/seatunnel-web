package org.apache.seatunnel.web.api.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.plugin.datasource.api.utils.DataSourceUtils;
import org.apache.seatunnel.web.api.service.BatchLinkUpIncrementalService;
import org.apache.seatunnel.web.api.service.DataSourceService;
import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.SyncBatchService;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.api.service.SyncCheckResultService;
import org.apache.seatunnel.web.api.service.SyncIncrementalConfigService;
import org.apache.seatunnel.web.api.service.SyncRunService;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.api.service.SyncZetaClient;
import org.apache.seatunnel.web.api.service.model.SyncJobStatusResult;
import org.apache.seatunnel.web.api.service.model.SyncSubmitJobResult;
import org.apache.seatunnel.web.common.constants.SyncConstants;
import org.apache.seatunnel.web.common.enums.ReleaseState;
import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.common.enums.SyncBoundaryMode;
import org.apache.seatunnel.web.common.enums.SyncBoundaryValueSource;
import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.common.enums.SyncCheckType;
import org.apache.seatunnel.web.common.enums.SyncEngineType;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.common.enums.SyncSinkType;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.common.enums.SyncTaskType;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.common.enums.SyncWatermarkValueType;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.core.hocon.JobDefinitionCommandResolver;
import org.apache.seatunnel.web.core.hocon.JobDefinitionHoconBuilder;
import org.apache.seatunnel.web.core.time.TimeVariableJdbcSqlRenderService;
import org.apache.seatunnel.web.dao.entity.DataSource;
import org.apache.seatunnel.web.dao.entity.JobDefinitionContentEntity;
import org.apache.seatunnel.web.dao.entity.JobDefinitionEntity;
import org.apache.seatunnel.web.dao.entity.SyncAuditEntity;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.dao.repository.JobDefinitionContentDao;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalConfigRequest;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalPreviewRequest;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalRunRequest;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalSqlTestRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncWatermarkUpdateRequest;
import org.apache.seatunnel.web.spi.bean.dto.command.BatchJobSaveCommand;
import org.apache.seatunnel.web.spi.bean.dto.command.JobDefinitionSaveCommand;
import org.apache.seatunnel.web.spi.bean.dto.config.JobScheduleConfig;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.vo.BatchLinkUpIncrementalConfigVO;
import org.apache.seatunnel.web.spi.bean.vo.BatchLinkUpIncrementalContextVO;
import org.apache.seatunnel.web.spi.bean.vo.BatchLinkUpIncrementalHoconPreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncAuditItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBatchListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncCheckResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncRunListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchLinkUpIncrementalServiceImpl extends SyncServiceSupport
        implements BatchLinkUpIncrementalService {

    private static final String DEFAULT_CHECK_CODE = "incremental_default_check";
    private static final Pattern FORBIDDEN_SQL_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|REPLACE|MERGE|CALL|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ID_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> RESERVED_SYSTEM_VARIABLES = Set.of(
            "batch_id",
            "run_id",
            "task_id",
            "task_code",
            "batch_start_value",
            "batch_end_value",
            "batch_start_time",
            "batch_end_time",
            "watermark_value",
            "watermark_time",
            "last_success_batch_id",
            "last_success_run_id",
            "biz_date"
    );

    @Resource
    private BatchJobDefinitionQueryService batchJobDefinitionQueryService;

    @Resource
    private JobDefinitionContentDao jobDefinitionContentDao;

    @Resource
    private JobDefinitionCommandResolver jobDefinitionCommandResolver;

    @Resource
    private JobDefinitionHoconBuilder jobDefinitionHoconBuilder;

    @Resource
    private SyncIncrementalConfigService syncIncrementalConfigService;

    @Resource
    private SyncWatermarkService syncWatermarkService;

    @Resource
    private SyncBatchService syncBatchService;

    @Resource
    private SyncRunService syncRunService;

    @Resource
    private SyncAuditService syncAuditService;

    @Resource
    private SyncCheckConfigService syncCheckConfigService;

    @Resource
    private SyncCheckResultService syncCheckResultService;

    private final HoconRenderService hoconRenderService;

    @Resource
    private TimeVariableJdbcSqlRenderService timeVariableJdbcSqlRenderService;

    @Resource
    private SyncZetaClient syncZetaClient;

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private SyncRunProperties syncRunProperties;

    @PostConstruct
    void validateInjectedDependencies() {
        Objects.requireNonNull(hoconRenderService, "hoconRenderService must be injected by Spring");
    }

    @Override
    public BatchLinkUpIncrementalConfigVO getConfig(Long taskId) {
        JobDefinitionEntity definition = loadDefinition(taskId);
        SyncIncrementalConfigEntity config =
                syncIncrementalConfigService.getByBatchLinkUpTaskId(definition.getId());
        if (config == null) {
            config = defaultConfig(definition);
        }
        return toConfigVO(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchLinkUpIncrementalConfigVO saveConfig(
            Long taskId,
            BatchLinkUpIncrementalConfigRequest request
    ) {
        if (request == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "incrementalConfig");
        }
        JobDefinitionEntity definition = loadDefinition(taskId);
        SyncIncrementalConfigEntity existing =
                syncIncrementalConfigService.getByBatchLinkUpTaskId(definition.getId());
        SyncIncrementalConfigEntity entity = existing == null ? new SyncIncrementalConfigEntity() : existing;
        fillConfig(definition, entity, request);
        if (entity.getId() == null) {
            syncIncrementalConfigService.create(entity);
        } else {
            syncIncrementalConfigService.update(entity);
        }
        upsertDefaultCheckConfig(definition.getId(), entity, request);
        return toConfigVO(entity);
    }

    @Override
    public BatchLinkUpIncrementalContextVO previewContext(
            Long taskId,
            BatchLinkUpIncrementalPreviewRequest request
    ) {
        JobDefinitionEntity definition = loadDefinition(taskId);
        SyncIncrementalConfigEntity config = loadEnabledConfig(definition, false);
        HoconBundle hocon = loadHocon(definition.getId());
        IncrementalContext context = buildContext(
                definition,
                config,
                hocon,
                request == null ? Map.of() : nullToEmpty(request.getParams()),
                generateBatchId(taskCode(definition)),
                generateRunId(taskCode(definition)),
                false
        );
        return context.vo;
    }

    @Override
    public BatchLinkUpIncrementalHoconPreviewVO previewHocon(
            Long taskId,
            BatchLinkUpIncrementalPreviewRequest request
    ) {
        JobDefinitionEntity definition = loadDefinition(taskId);
        SyncIncrementalConfigEntity config = loadEnabledConfig(definition, false);
        HoconBundle hocon = loadHocon(definition.getId());
        IncrementalContext context = buildContext(
                definition,
                config,
                hocon,
                request == null ? Map.of() : nullToEmpty(request.getParams()),
                generateBatchId(taskCode(definition)),
                generateRunId(taskCode(definition)),
                false
        );

        BatchLinkUpIncrementalHoconPreviewVO vo = new BatchLinkUpIncrementalHoconPreviewVO();
        vo.setOriginalHocon(hocon.originalHocon);
        vo.setContext(context.vo);
        vo.setVariables(context.variables);
        vo.setExecutedSqls(context.vo.getExecutedSqls());
        vo.setMissingVariables(context.vo.getMissingVariables());
        vo.setDiagnostics(context.vo.getDiagnostics());
        vo.setWarnings(context.vo.getDiagnostics());
        if (context.vo.getMissingVariables().isEmpty()) {
            vo.setRenderedHocon(renderIncrementalHocon(hocon, context));
        }
        return vo;
    }

    @Override
    public RunResultVO runIncremental(Long taskId, BatchLinkUpIncrementalRunRequest request) {
        JobDefinitionEntity definition = loadDefinition(taskId);
        validateRunnableDefinition(definition);
        SyncIncrementalConfigEntity config = loadEnabledConfig(definition, true);
        HoconBundle hocon = loadHocon(definition.getId());
        SyncTaskEntity task = toSyncTask(definition, config);
        SyncTaskVersionEntity version = toSyncVersion(hocon);
        SyncTriggerType triggerType = parseTriggerType(request == null ? null : request.getTriggerType());
        SyncRunMode runMode = parseEnum(
                SyncRunMode.class,
                request == null ? null : request.getRunMode(),
                SyncRunMode.NORMAL
        );
        boolean waitForFinish = request == null
                || request.getWaitForFinish() == null
                || Boolean.TRUE.equals(request.getWaitForFinish());
        Map<String, Object> params = request == null ? Map.of() : nullToEmpty(request.getParams());
        String batchId = generateBatchId(task.getTaskCode());
        String runId = generateRunId(task.getTaskCode());
        SyncBatchEntity batch = createInitialBatch(task, batchId, triggerType, runMode);
        SyncRunEntity run = null;
        IncrementalContext context = null;

        try {
            syncAuditService.appendInfo(null, batchId, task.getId(), task.getTaskCode(),
                    SyncAuditEventType.CREATE_BATCH, "Batch-link-up incremental batch created", batch);

            run = createRun(
                    task,
                    version,
                    batch,
                    runId,
                    triggerType,
                    request == null ? null : request.getSchedulerRunId(),
                    params
            );
            syncAuditService.appendInfo(runId, batchId, task.getId(), task.getTaskCode(),
                    SyncAuditEventType.CREATE_RUN, "Batch-link-up incremental run created", run);

            context = buildContext(definition, config, hocon, params, batchId, runId, true);
            fillBatchRange(batch, context);
            syncBatchService.update(batch);
            updateBatchStatus(batch, SyncBatchStatus.READY, null);

            if (!context.vo.getMissingVariables().isEmpty()) {
                throw new ServiceException("Missing HOCON variables: " + context.vo.getMissingVariables());
            }

            String renderedHocon = renderIncrementalHocon(hocon, context);
            String hoconHash = hoconRenderService.calculateHash(renderedHocon);
            syncRunService.updateGeneratedHocon(runId, renderedHocon);
            run.setGeneratedHocon(renderedHocon);
            syncAuditService.appendInfo(runId, batchId, task.getId(), task.getTaskCode(),
                    SyncAuditEventType.RENDER_HOCON, "Batch-link-up HOCON rendered",
                    Map.of("hoconHash", hoconHash));

            String jobName = task.getTaskCode() + "_" + runId;
            syncAuditService.appendInfo(runId, batchId, task.getId(), task.getTaskCode(),
                    SyncAuditEventType.SUBMIT_JOB, "Start submitting batch-link-up incremental job",
                    Map.of("jobName", jobName));
            SyncSubmitJobResult submitResult = syncZetaClient.submitJob(definition.getClientId(), jobName, renderedHocon);
            syncRunService.updateSeatunnelJob(runId, submitResult.getJobId(), submitResult.getJobName());
            run.setSeatunnelJobId(submitResult.getJobId());
            run.setSeatunnelJobName(submitResult.getJobName());

            updateBatchStatus(batch, SyncBatchStatus.SUBMITTED, null);
            updateRunStatus(run, SyncRunStatus.SUBMITTED, null);
            if (!waitForFinish) {
                return toRunResult(task, version, batch, run, null, false, null, null);
            }

            updateBatchStatus(batch, SyncBatchStatus.RUNNING, null);
            updateRunStatus(run, SyncRunStatus.RUNNING, null);
            SyncJobStatusResult finalStatus = pollUntilFinished(task, batch, run);
            if (!finalStatus.isSuccess()) {
                String terminalError = terminalErrorMessage(finalStatus);
                SyncRunStatus terminalRunStatus = toRunStatus(finalStatus);
                SyncBatchStatus terminalBatchStatus = toBatchStatus(finalStatus);
                updateBatchStatus(batch, terminalBatchStatus, terminalError);
                updateRunStatus(run, terminalRunStatus, terminalError);
                recordWatermarkNotAdvanced(task, batch, run, "job not success");
                syncAuditService.appendError(runId, batchId, task.getId(), task.getTaskCode(),
                        SyncAuditEventType.RUN_FAILED, "Batch-link-up incremental job finished unsuccessfully",
                        finalStatus.getRawResponse());
                return toRunResult(task, version, batch, run, finalStatus, false, null, terminalError);
            }

            applyJobMetrics(batch, run, finalStatus);
            updateBatchStatus(batch, SyncBatchStatus.VERIFYING, null);
            syncAuditService.appendInfo(runId, batchId, task.getId(), task.getTaskCode(),
                    SyncAuditEventType.VERIFYING, "SeaTunnel job success, start incremental check",
                    finalStatus.getRawResponse());

            if (Boolean.TRUE.equals(config.getCheckEnabled()) && !isBlank(config.getCheckSql())) {
                IncrementalCheckResult checkResult = executeIncrementalCheckSql(task, batch, run, config, context.variables);
                applyCheckMetrics(batch, run, checkResult);
                if (!checkResult.passed) {
                    String checkError = firstNonBlank(checkResult.message, "Incremental check_sql returned check_passed=false");
                    updateBatchStatus(batch, SyncBatchStatus.CHECK_FAILED, checkError);
                    updateRunStatus(run, SyncRunStatus.CHECK_FAILED, checkError);
                    recordWatermarkNotAdvanced(task, batch, run, "check_sql not passed");
                    syncAuditService.appendWarn(runId, batchId, task.getId(), task.getTaskCode(),
                            SyncAuditEventType.RUN_FAILED,
                            "Batch-link-up incremental check_sql failed, watermark is not advanced",
                            checkResult.auditDetail());
                    return toRunResult(task, version, batch, run, finalStatus, false, null, checkError);
                }
            } else {
                syncAuditService.appendInfo(runId, batchId, task.getId(), task.getTaskCode(),
                        SyncAuditEventType.VERIFYING, "Incremental check disabled, skip verification", null);
            }

            updateBatchStatus(batch, SyncBatchStatus.SUCCESS, null);
            updateRunStatus(run, SyncRunStatus.SUCCESS, null);
            WatermarkAdvanceResult watermarkResult =
                    advanceWatermarkIfNeeded(task, config, batch, run, context.variables);
            syncAuditService.appendInfo(runId, batchId, task.getId(), task.getTaskCode(),
                    SyncAuditEventType.RUN_SUCCESS, "Batch-link-up incremental run success",
                    Map.of("watermarkAdvanced", watermarkResult.updated));
            return toRunResult(
                    task,
                    version,
                    batch,
                    run,
                    finalStatus,
                    watermarkResult.updated,
                    watermarkResult.value,
                    watermarkResult.errorMessage
            );
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            log.error("Batch-link-up incremental run failed, taskId={}, batchId={}, runId={}",
                    definition.getId(), batchId, runId, e);
            updateBatchStatus(batch, SyncBatchStatus.FAILED, message);
            if (run != null) {
                updateRunStatus(run, SyncRunStatus.FAILED, message);
                recordWatermarkNotAdvanced(task, batch, run, "run failed");
            }
            syncAuditService.appendError(
                    run == null ? null : runId,
                    batchId,
                    task.getId(),
                    task.getTaskCode(),
                    SyncAuditEventType.RUN_FAILED,
                    "Batch-link-up incremental run failed, watermark is not advanced",
                    Map.of("errorMessage", message)
            );
            if (run != null) {
                return toRunResult(
                        task,
                        version,
                        batch,
                        run,
                        failedJobStatus(run.getSeatunnelJobId(), message),
                        false,
                        null,
                        message
                );
            }
            throw e instanceof ServiceException ? (ServiceException) e : new ServiceException(message, e);
        }
    }

    @Override
    public PaginationResult<SyncRunListItemVO> listRuns(
            Long taskId,
            Integer pageNo,
            Integer pageSize,
            String status,
            String startTime,
            String endTime,
            String keyword
    ) {
        JobDefinitionEntity definition = loadDefinition(taskId);
        String code = taskCode(definition);
        Map<String, SyncBatchEntity> batchById = syncBatchService.listByTaskId(definition.getId())
                .stream()
                .collect(Collectors.toMap(SyncBatchEntity::getBatchId, item -> item, (left, right) -> left));
        Date start = parseDateTime(startTime);
        Date end = parseDateTime(endTime);
        List<SyncRunListItemVO> items = syncRunService.listByTaskId(definition.getId())
                .stream()
                .filter(item -> matchesStatus(status, code(item.getStatus())))
                .filter(item -> matchesTimeRange(item.getCreateTime(), start, end))
                .filter(item -> matchesKeyword(item, batchById.get(item.getBatchId()), keyword))
                .map(item -> toRunListItem(code, definition.getJobName(), item, batchById.get(item.getBatchId())))
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public RunDetailVO getRun(Long taskId, String runId) {
        JobDefinitionEntity definition = loadDefinition(taskId);
        SyncRunEntity run = syncRunService.getByRunId(runId);
        if (run == null || !Objects.equals(run.getTaskId(), definition.getId())) {
            throw new ServiceException("Batch-link-up incremental run not found, taskId="
                    + taskId
                    + ", runId="
                    + runId);
        }
        SyncBatchEntity batch = isBlank(run.getBatchId()) ? null : syncBatchService.getByBatchId(run.getBatchId());
        List<SyncAuditEntity> audits = syncAuditService.listByRunId(run.getRunId());
        List<SyncCheckResultEntity> checkResults = syncCheckResultService.listByRunId(run.getRunId());
        return toRunDetail(definition, run, batch, audits, checkResults);
    }

    @Override
    public PaginationResult<SyncBatchListItemVO> listBatches(
            Long taskId,
            Integer pageNo,
            Integer pageSize,
            String status
    ) {
        JobDefinitionEntity definition = loadDefinition(taskId);
        List<SyncBatchListItemVO> items = syncBatchService.listByTaskId(definition.getId())
                .stream()
                .filter(item -> matchesStatus(status, code(item.getStatus())))
                .map(this::toBatchListItem)
                .collect(Collectors.toList());
        return page(items, pageNo, pageSize);
    }

    @Override
    public List<WatermarkVO> getWatermark(Long taskId) {
        JobDefinitionEntity definition = loadDefinition(taskId);
        String code = taskCode(definition);
        return syncWatermarkService.listByTaskId(definition.getId())
                .stream()
                .map(item -> toWatermarkVO(code, item))
                .collect(Collectors.toList());
    }

    @Override
    public WatermarkVO updateWatermark(Long taskId, SyncWatermarkUpdateRequest request) {
        JobDefinitionEntity definition = loadDefinition(taskId);
        SyncIncrementalConfigEntity config = loadEnabledConfig(definition, false);
        if (request == null || isBlank(request.getCurrentValue())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "currentValue");
        }
        if (isBlank(request.getReason())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "reason");
        }
        String watermarkKey = defaultWatermarkKey(request.getWatermarkKey());
        SyncWatermarkEntity existing =
                syncWatermarkService.getByTaskIdAndWatermarkKey(definition.getId(), watermarkKey);
        String oldValue = existing == null ? null : existing.getCurrentValue();
        SyncWatermarkEntity updated;
        if (existing == null) {
            updated = SyncWatermarkEntity.builder()
                    .taskId(definition.getId())
                    .watermarkKey(watermarkKey)
                    .currentValue(request.getCurrentValue())
                    .currentValueType(resolveWatermarkValueType(config))
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
                    ? resolveWatermarkValueType(config)
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
        syncAuditService.appendWarn(null, null, definition.getId(), taskCode(definition),
                SyncAuditEventType.MANUAL_UPDATE_WATERMARK, "Manual update batch-link-up watermark", detail);
        return toWatermarkVO(taskCode(definition), updated);
    }

    @Override
    public BatchLinkUpIncrementalContextVO.SqlExecutionVO testSql(
            Long taskId,
            BatchLinkUpIncrementalSqlTestRequest request
    ) {
        if (request == null || isBlank(request.getSql())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), "请先填写 SQL");
        }

        JobDefinitionEntity definition = loadDefinition(taskId);
        SyncIncrementalConfigEntity config =
                syncIncrementalConfigService.getByBatchLinkUpTaskId(definition.getId());
        Long datasourceId = resolveTestSqlDatasourceId(request, config);
        if (datasourceId == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(), "请选择数据源");
        }

        String renderedSql = hoconRenderService.render(
                request.getSql().trim(),
                buildTestSqlVariables(definition, request)
        );
        QueryResult queryResult = executeQuery(
                resolveTestSqlName(request),
                datasourceId,
                renderedSql,
                Boolean.TRUE.equals(request.getScalar())
        );
        return queryResult.toVo();
    }

    private Map<String, Object> buildTestSqlVariables(
            JobDefinitionEntity definition,
            BatchLinkUpIncrementalSqlTestRequest request
    ) {
        String code = taskCode(definition);
        Map<String, Object> variables = new LinkedHashMap<>(nullToEmpty(request.getParams()));
        variables.put("batch_id", generateBatchId(code));
        variables.put("run_id", generateRunId(code));
        variables.put("task_id", definition.getId());
        variables.put("task_code", code);
        variables.put("task_name", definition.getJobName());
        variables.put("biz_date", LocalDate.now().toString());
        return variables;
    }

    private Long resolveTestSqlDatasourceId(
            BatchLinkUpIncrementalSqlTestRequest request,
            SyncIncrementalConfigEntity config
    ) {
        Long requestDatasourceId = normalizeDatasourceId(request.getDatasourceId());
        if (requestDatasourceId != null) {
            return requestDatasourceId;
        }
        if (config == null) {
            return null;
        }
        if (isCheckSqlTest(request)) {
            Long checkDatasourceId = normalizeDatasourceId(config.getCheckDatasourceId());
            if (checkDatasourceId != null) {
                return checkDatasourceId;
            }
        }
        return normalizeDatasourceId(config.getBoundaryDatasourceId());
    }

    private Long normalizeDatasourceId(Long datasourceId) {
        return datasourceId == null || datasourceId <= 0 ? null : datasourceId;
    }

    private boolean isCheckSqlTest(BatchLinkUpIncrementalSqlTestRequest request) {
        String value = firstNonBlank(request.getFieldName(), request.getSqlType());
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim()
                .replace("-", "_")
                .toLowerCase(Locale.ROOT);
        return "check_sql".equals(normalized)
                || "checksql".equals(normalized)
                || "check".equals(normalized);
    }

    private String resolveTestSqlName(BatchLinkUpIncrementalSqlTestRequest request) {
        String value = firstNonBlank(request.getFieldName(), request.getSqlType());
        return isBlank(value) ? "test_sql" : value.trim();
    }

    private IncrementalContext buildContext(
            JobDefinitionEntity definition,
            SyncIncrementalConfigEntity config,
            HoconBundle hocon,
            Map<String, Object> requestParams,
            String batchId,
            String runId,
            boolean failFast
    ) {
        SyncWatermarkEntity watermark = syncWatermarkService.getByTaskIdAndWatermarkKey(
                definition.getId(),
                defaultWatermarkKey(config.getWatermarkKey())
        );
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> params = new LinkedHashMap<>(renderScheduleParams(hocon.scheduleConfig));
        params.putAll(parseJsonMap(config.getDefaultParamsJson()));
        params.putAll(requestParams == null ? Map.of() : requestParams);
        List<String> reservedParamConflicts = findReservedParamConflicts(params);
        Map<String, Object> customContext = new LinkedHashMap<>(parseJsonMap(config.getCustomContextJson()));

        variables.putAll(params);
        variables.put("batch_id", batchId);
        variables.put("run_id", runId);
        variables.put("task_id", definition.getId());
        variables.put("task_code", taskCode(definition));
        variables.put("task_name", definition.getJobName());
        variables.put("watermark_value", watermark == null ? null : watermark.getCurrentValue());
        variables.put("watermark_time", watermark == null ? null : formatDate(watermark.getUpdateTime()));
        variables.put("watermark_key", defaultWatermarkKey(config.getWatermarkKey()));
        variables.put("last_success_batch_id", watermark == null ? null : watermark.getLastSuccessBatchId());
        variables.put("last_success_run_id", watermark == null || watermark.getLastSuccessRunId() == null
                ? null
                : String.valueOf(watermark.getLastSuccessRunId()));
        variables.put("biz_date", LocalDate.now().toString());
        putCustomVariables(variables, customContext);

        BatchLinkUpIncrementalContextVO vo = new BatchLinkUpIncrementalContextVO();
        vo.setBatchId(batchId);
        vo.setRunId(runId);
        vo.setTaskCode(taskCode(definition));
        vo.setWatermarkValue(valueToString(variables.get("watermark_value")));
        vo.setWatermarkTime(valueToString(variables.get("watermark_time")));
        vo.setLastSuccessBatchId(valueToString(variables.get("last_success_batch_id")));
        vo.setLastSuccessRunId(valueToString(variables.get("last_success_run_id")));
        vo.setBizDate(valueToString(variables.get("biz_date")));
        if (!reservedParamConflicts.isEmpty()) {
            vo.getDiagnostics().add("用户参数包含系统保留变量，已使用系统值覆盖：" + reservedParamConflicts);
        }

        Long datasourceId = resolveBoundaryDatasourceId(definition, config);
        try {
            applyPrepareSql(definition, config, variables, customContext, vo, datasourceId, failFast);
            resolveBoundaryVariable(
                    "batch_start_value",
                    config.getStartValueSource(),
                    config.getBatchStartValueSql(),
                    config.getFixedStartValue(),
                    watermark,
                    definition,
                    config,
                    variables,
                    vo,
                    datasourceId,
                    failFast
            );
            resolveBoundaryVariable(
                    "batch_end_value",
                    config.getEndValueSource(),
                    config.getBatchEndValueSql(),
                    config.getFixedEndValue(),
                    watermark,
                    definition,
                    config,
                    variables,
                    vo,
                    datasourceId,
                    failFast
            );
            resolveBoundaryVariable(
                    "batch_start_time",
                    config.getStartTimeSource(),
                    config.getBatchStartTimeSql(),
                    config.getFixedStartTime(),
                    watermark,
                    definition,
                    config,
                    variables,
                    vo,
                    datasourceId,
                    failFast
            );
            resolveBoundaryVariable(
                    "batch_end_time",
                    config.getEndTimeSource(),
                    config.getBatchEndTimeSql(),
                    config.getFixedEndTime(),
                    watermark,
                    definition,
                    config,
                    variables,
                    vo,
                    datasourceId,
                    failFast
            );
        } catch (ServiceException e) {
            if (failFast) {
                throw e;
            }
            vo.getDiagnostics().add(e.getMessage());
        }

        vo.setBatchStartValue(valueToString(variables.get("batch_start_value")));
        vo.setBatchEndValue(valueToString(variables.get("batch_end_value")));
        vo.setBatchStartTime(valueToString(variables.get("batch_start_time")));
        vo.setBatchEndTime(valueToString(variables.get("batch_end_time")));
        vo.setCustomContext(customContext);
        removePreparedMarkers(variables);
        vo.setVariables(variables);
        vo.setMissingVariables(hoconRenderService.findMissingVariables(hocon.originalHocon, variables));

        IncrementalContext context = new IncrementalContext();
        context.vo = vo;
        context.variables = variables;
        return context;
    }

    private void applyPrepareSql(
            JobDefinitionEntity definition,
            SyncIncrementalConfigEntity config,
            Map<String, Object> variables,
            Map<String, Object> customContext,
            BatchLinkUpIncrementalContextVO vo,
            Long datasourceId,
            boolean failFast
    ) {
        if (isBlank(config.getBatchPrepareSql())) {
            return;
        }
        if (config.getBoundaryMode() != SyncBoundaryMode.PREPARE_SQL
                && config.getBoundaryMode() != SyncBoundaryMode.SEPARATE_SQL) {
            return;
        }
        QueryResult queryResult = executeRenderedSql(
                "batch_prepare_sql",
                datasourceId,
                config.getBatchPrepareSql(),
                variables,
                false,
                vo,
                failFast
        );
        if (queryResult == null || queryResult.rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = queryResult.rows.get(0);
        copyIfPresent(row, variables, "batch_start_value");
        copyIfPresent(row, variables, "batch_end_value");
        copyIfPresent(row, variables, "batch_start_time");
        copyIfPresent(row, variables, "batch_end_time");
        copyIfPresent(row, variables, "biz_date");
        Object customJson = row.get("custom_json");
        if (customJson != null && !StringUtils.isBlank(String.valueOf(customJson))) {
            Map<String, Object> customValues = parseJsonMap(String.valueOf(customJson));
            customContext.putAll(customValues);
            putCustomVariables(variables, customValues);
        }
        variables.putIfAbsent("task_code", taskCode(definition));
    }

    private void resolveBoundaryVariable(
            String variable,
            SyncBoundaryValueSource source,
            String sql,
            String fixedValue,
            SyncWatermarkEntity watermark,
            JobDefinitionEntity definition,
            SyncIncrementalConfigEntity config,
            Map<String, Object> variables,
            BatchLinkUpIncrementalContextVO vo,
            Long datasourceId,
            boolean failFast
    ) {
        if (Boolean.TRUE.equals(variables.get(preparedMarker(variable)))) {
            return;
        }
        SyncBoundaryValueSource actualSource = source == null ? SyncBoundaryValueSource.NONE : source;
        Object value = null;
        switch (actualSource) {
            case WATERMARK:
                value = watermark == null ? null : watermark.getCurrentValue();
                break;
            case SQL:
                QueryResult result = executeRenderedSql(variable + "_sql", datasourceId, sql,
                        variables, true, vo, failFast);
                value = result == null ? null : result.scalarValue;
                break;
            case PARAM:
                value = firstNonNull(variables.get(variable), findCamelParam(variables, variable));
                break;
            case FIXED:
                value = fixedValue;
                break;
            case NOW:
                value = DATE_TIME_FORMATTER.format(LocalDateTime.now());
                break;
            case NONE:
                break;
            default:
                throw new ServiceException("Unsupported boundary value source: " + actualSource);
        }
        if (value != null) {
            variables.put(variable, normalizeValue(value));
        }
        variables.putIfAbsent("task_code", taskCode(definition));
        if (actualSource == SyncBoundaryValueSource.SQL
                && datasourceId == null
                && failFast
                && !isBlank(sql)) {
            throw new ServiceException("boundary_datasource_id is required for " + variable + " SQL");
        }
        if (config.getBoundaryMode() == SyncBoundaryMode.SIMPLE_WATERMARK
                && variable.endsWith("_value")
                && variables.get(variable) == null
                && failFast) {
            throw new ServiceException("Boundary variable is empty: " + variable);
        }
    }

    private QueryResult executeRenderedSql(
            String name,
            Long datasourceId,
            String sqlTemplate,
            Map<String, Object> variables,
            boolean scalar,
            BatchLinkUpIncrementalContextVO vo,
            boolean failFast
    ) {
        if (isBlank(sqlTemplate)) {
            return null;
        }
        if (datasourceId == null) {
            String message = "boundary datasourceId is required for " + name;
            recordSqlFailure(vo, name, null, sqlTemplate, message);
            if (failFast) {
                throw new ServiceException(message);
            }
            return null;
        }
        String renderedSql;
        try {
            renderedSql = hoconRenderService.render(sqlTemplate, variables);
            QueryResult result = executeQuery(name, datasourceId, renderedSql, scalar);
            vo.getExecutedSqls().add(result.toVo());
            return result;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            recordSqlFailure(vo, name, datasourceId, sqlTemplate, message);
            if (failFast) {
                throw e instanceof ServiceException ? (ServiceException) e : new ServiceException(message, e);
            }
            vo.getDiagnostics().add(message);
            return null;
        }
    }

    private QueryResult executeQuery(String name, Long datasourceId, String sql, boolean scalar) {
        validateReadOnlySql(sql);
        if (datasourceId == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "datasourceId");
        }
        DataSource dataSource = dataSourceService.selectById(datasourceId);
        try {
            BaseConnectionParam connectionParam = DataSourceUtils.buildConnectionParams(
                    dataSource.getDbType(),
                    dataSource.getConnectionParams()
            );
            try (Connection connection = DataSourceUtils
                    .getDatasourceProcessor(dataSource.getDbType())
                    .getConnectionManager()
                    .getConnection(connectionParam);
                 Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(syncRunProperties.getCheckSqlTimeoutSeconds());
                try (ResultSet resultSet = statement.executeQuery(sql)) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    if (scalar && columnCount != 1) {
                        throw new ServiceException(name + " must return exactly one column");
                    }
                    QueryResult result = new QueryResult();
                    result.name = name;
                    result.datasourceId = datasourceId;
                    result.renderedSql = sql;
                    for (int i = 1; i <= columnCount; i++) {
                        result.columns.add(metaData.getColumnLabel(i));
                    }
                    if (!resultSet.next()) {
                        result.scalarValue = null;
                        return result;
                    }
                    Map<String, Object> row = readRow(resultSet, metaData);
                    result.rows.add(row);
                    if (scalar) {
                        result.scalarValue = normalizeValue(resultSet.getObject(1));
                    }
                    if (resultSet.next()) {
                        throw new ServiceException(name + " must return at most one row");
                    }
                    return result;
                }
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Execute batch-link-up incremental SQL failed, name={}, datasourceId={}",
                    name, datasourceId, e);
            throw new ServiceException("Execute SQL failed, name=" + name + ", error=" + e.getMessage(), e);
        }
    }

    private Map<String, Object> readRow(ResultSet resultSet, ResultSetMetaData metaData) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            row.put(metaData.getColumnLabel(i), normalizeValue(resultSet.getObject(i)));
        }
        return row;
    }

    private void validateReadOnlySql(String sql) {
        if (isBlank(sql)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "sql");
        }
        String normalized = stripLeadingSqlComments(sql).trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            throw new ServiceException("Boundary/check SQL only supports SELECT or WITH statements");
        }
        if (FORBIDDEN_SQL_PATTERN.matcher(normalized).find()) {
            throw new ServiceException("Boundary/check SQL contains forbidden write or DDL keyword");
        }
    }

    private String stripLeadingSqlComments(String sql) {
        String value = sql == null ? "" : sql.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (value.startsWith("--")) {
                int end = value.indexOf('\n');
                value = end < 0 ? "" : value.substring(end + 1).trim();
                changed = true;
            } else if (value.startsWith("/*")) {
                int end = value.indexOf("*/");
                value = end < 0 ? "" : value.substring(end + 2).trim();
                changed = true;
            }
        }
        return value;
    }

    private SyncBatchEntity createInitialBatch(
            SyncTaskEntity task,
            String batchId,
            SyncTriggerType triggerType,
            SyncRunMode runMode
    ) {
        Date now = now();
        SyncBatchEntity batch = SyncBatchEntity.builder()
                .batchId(batchId)
                .taskId(task.getId())
                .taskCode(task.getTaskCode())
                .triggerType(triggerType)
                .runMode(runMode)
                .status(SyncBatchStatus.CREATED)
                .createTime(now)
                .updateTime(now)
                .build();
        syncBatchService.create(batch);
        return batch;
    }

    private SyncRunEntity createRun(
            SyncTaskEntity task,
            SyncTaskVersionEntity version,
            SyncBatchEntity batch,
            String runId,
            SyncTriggerType triggerType,
            String schedulerRunId,
            Map<String, Object> params
    ) {
        Date now = now();
        SyncRunEntity run = SyncRunEntity.builder()
                .runId(runId)
                .taskId(task.getId())
                .taskVersionId(version.getId())
                .batchId(batch.getBatchId())
                .triggerType(triggerType)
                .schedulerRunId(schedulerRunId)
                .runParamJson(JSONUtils.toJsonString(params))
                .status(SyncRunStatus.CREATED)
                .createTime(now)
                .updateTime(now)
                .build();
        syncRunService.create(run);
        return run;
    }

    private void fillBatchRange(SyncBatchEntity batch, IncrementalContext context) {
        batch.setBatchStartValue(valueToString(context.variables.get("batch_start_value")));
        batch.setBatchEndValue(valueToString(context.variables.get("batch_end_value")));
        batch.setBatchStartTime(toDate(context.variables.get("batch_start_time")));
        batch.setBatchEndTime(toDate(context.variables.get("batch_end_time")));
    }

    private WatermarkAdvanceResult advanceWatermarkIfNeeded(
            SyncTaskEntity task,
            SyncIncrementalConfigEntity config,
            SyncBatchEntity batch,
            SyncRunEntity run,
            Map<String, Object> variables
    ) {
        if (!Boolean.TRUE.equals(config.getSuccessUpdateWatermark())) {
            String message = "Watermark not advanced: successUpdateWatermark=false";
            log.warn("{}, taskId={}, batchId={}, runId={}", message, task.getId(), batch.getBatchId(), run.getRunId());
            syncAuditService.appendWarn(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.ADVANCE_WATERMARK, message, null);
            return WatermarkAdvanceResult.skipped(message);
        }
        String newValue = firstNonBlank(
                valueToString(variables.get("batch_end_value")),
                valueToString(variables.get("batch_end_time"))
        );
        if (isBlank(newValue)) {
            String message = "Watermark not advanced: batch_end_value is null";
            log.error("{}, taskId={}, batchId={}, runId={}", message, task.getId(), batch.getBatchId(), run.getRunId());
            syncAuditService.appendError(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.ADVANCE_WATERMARK, message, null);
            return WatermarkAdvanceResult.failed(message);
        }
        try {
            syncWatermarkService.advanceWatermark(
                    task.getId(),
                    defaultWatermarkKey(config.getWatermarkKey()),
                    newValue,
                    run.getId(),
                    batch.getBatchId()
            );
        } catch (Exception e) {
            String message = "Watermark not advanced: insert/update failed";
            String error = e.getMessage() == null ? e.toString() : e.getMessage();
            log.error("{}, taskId={}, batchId={}, runId={}", message, task.getId(), batch.getBatchId(), run.getRunId(), e);
            syncAuditService.appendError(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.ADVANCE_WATERMARK, message, Map.of("errorMessage", error));
            return WatermarkAdvanceResult.failed(message + ", " + error);
        }
        syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                SyncAuditEventType.ADVANCE_WATERMARK,
                "Watermark advanced after successful batch-link-up incremental run",
                Map.of("newValue", newValue, "watermarkKey", defaultWatermarkKey(config.getWatermarkKey())));
        return WatermarkAdvanceResult.updated(newValue);
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
        SyncJobStatusResult timeout = new SyncJobStatusResult();
        timeout.setJobId(run.getSeatunnelJobId());
        timeout.setStatus("TIMEOUT");
        timeout.setEndState(true);
        timeout.setSuccess(false);
        timeout.setErrorMessage("SeaTunnel job poll timeout, jobId=" + run.getSeatunnelJobId()
                + ", lastStatus=" + (latest == null ? null : latest.getStatus()));
        Map<String, Object> timeoutRaw = new LinkedHashMap<>();
        timeoutRaw.put("lastStatus", latest == null ? null : latest.getStatus());
        timeoutRaw.put("pollTimeoutMs", syncRunProperties.getPollTimeoutMs());
        timeout.setRawResponse(timeoutRaw);
        return timeout;
    }

    private SyncJobStatusResult failedJobStatus(String jobId, String errorMessage) {
        SyncJobStatusResult failed = new SyncJobStatusResult();
        failed.setJobId(jobId);
        failed.setStatus("FAILED");
        failed.setEndState(true);
        failed.setSuccess(false);
        failed.setErrorMessage(errorMessage);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("errorMessage", errorMessage);
        raw.put("watermarkAdvanced", false);
        failed.setRawResponse(raw);
        return failed;
    }

    private void recordWatermarkNotAdvanced(
            SyncTaskEntity task,
            SyncBatchEntity batch,
            SyncRunEntity run,
            String reason
    ) {
        String message = "Watermark not advanced: " + reason;
        log.warn("{}, taskId={}, batchId={}, runId={}", message, task.getId(), batch.getBatchId(), run.getRunId());
        syncAuditService.appendWarn(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                SyncAuditEventType.ADVANCE_WATERMARK, message, null);
    }

    private void applyJobMetrics(SyncBatchEntity batch, SyncRunEntity run, SyncJobStatusResult status) {
        Long sourceCount = status == null ? null : status.getSourceCount();
        Long sinkCount = status == null ? null : status.getSinkCount();
        Long errorCount = status == null ? null : status.getErrorCount();
        if (sourceCount == null && sinkCount == null && errorCount == null) {
            log.warn("SeaTunnel metrics unavailable for incremental run, keep counts as NULL, batchId={}, runId={}",
                    batch.getBatchId(), run.getRunId());
        }
        syncBatchService.updateMetrics(batch.getBatchId(), sourceCount, sinkCount, errorCount);
        syncRunService.updateMetrics(run.getRunId(), sourceCount, sinkCount, errorCount);
        batch.setSourceCount(sourceCount);
        batch.setSinkCount(sinkCount);
        batch.setErrorCount(errorCount);
        run.setSourceCount(sourceCount);
        run.setSinkCount(sinkCount);
        run.setErrorCount(errorCount);
    }

    private IncrementalCheckResult executeIncrementalCheckSql(
            SyncTaskEntity task,
            SyncBatchEntity batch,
            SyncRunEntity run,
            SyncIncrementalConfigEntity config,
            Map<String, Object> variables
    ) {
        Date startTime = now();
        String renderedSql = null;
        try {
            if (config.getCheckDatasourceId() == null) {
                throw new ServiceException("check_datasource_id is required when check_sql is enabled");
            }
            renderedSql = hoconRenderService.render(config.getCheckSql(), variables);
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.VERIFYING,
                    "Incremental check_sql rendered",
                    Map.of("checkDatasourceId", config.getCheckDatasourceId()));

            QueryResult queryResult = executeQuery("check_sql", config.getCheckDatasourceId(), renderedSql, false);
            Map<String, Object> row = queryResult.rows.isEmpty()
                    ? Collections.emptyMap()
                    : queryResult.rows.get(0);

            IncrementalCheckResult result = new IncrementalCheckResult();
            result.queryResult = queryResult;
            result.sourceCount = toLong(rowValue(row, "source_count"));
            result.sinkCount = toLong(rowValue(row, "sink_count"));
            result.errorCount = toLong(rowValue(row, "error_count"));
            result.passed = toBoolean(rowValue(row, "check_passed"), true);
            result.message = valueToString(rowValue(row, "message"));
            result.row = row;

            SyncCheckResultEntity entity = SyncCheckResultEntity.builder()
                    .runId(run.getRunId())
                    .batchId(batch.getBatchId())
                    .taskId(task.getId())
                    .taskCode(task.getTaskCode())
                    .checkCode(DEFAULT_CHECK_CODE)
                    .checkName("Incremental check_sql")
                    .checkType(SyncCheckType.CUSTOM_BOOLEAN)
                    .renderedSql(renderedSql)
                    .actualValue(JSONUtils.toJsonString(row))
                    .expectedValue("true")
                    .passed(result.passed)
                    .failOnMismatch(true)
                    .errorMessage(result.passed ? null : firstNonBlank(result.message, "check_passed=false"))
                    .startTime(startTime)
                    .endTime(now())
                    .createTime(now())
                    .build();
            syncCheckResultService.insertResult(entity);

            if (result.passed) {
                syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                        SyncAuditEventType.VERIFYING,
                        "Incremental check_sql passed",
                        result.auditDetail());
            } else {
                syncAuditService.appendWarn(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                        SyncAuditEventType.VERIFYING,
                        "Incremental check_sql failed",
                        result.auditDetail());
            }
            return result;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            SyncCheckResultEntity entity = SyncCheckResultEntity.builder()
                    .runId(run.getRunId())
                    .batchId(batch.getBatchId())
                    .taskId(task.getId())
                    .taskCode(task.getTaskCode())
                    .checkCode(DEFAULT_CHECK_CODE)
                    .checkName("Incremental check_sql")
                    .checkType(SyncCheckType.CUSTOM_BOOLEAN)
                    .renderedSql(renderedSql)
                    .actualValue(null)
                    .expectedValue("true")
                    .passed(false)
                    .failOnMismatch(true)
                    .errorMessage(message)
                    .startTime(startTime)
                    .endTime(now())
                    .createTime(now())
                    .build();
            try {
                syncCheckResultService.insertResult(entity);
            } catch (Exception insertError) {
                log.warn("Insert incremental check_sql failed result failed, runId={}", run.getRunId(), insertError);
            }
            syncAuditService.appendError(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.VERIFYING,
                    "Incremental check_sql execution failed",
                    Map.of("errorMessage", message));
            throw e instanceof ServiceException
                    ? (ServiceException) e
                    : new ServiceException("Execute incremental check_sql failed: " + message, e);
        }
    }

    private void applyCheckMetrics(SyncBatchEntity batch, SyncRunEntity run, IncrementalCheckResult checkResult) {
        Long sourceCount = checkResult.sourceCount == null ? run.getSourceCount() : checkResult.sourceCount;
        Long sinkCount = checkResult.sinkCount == null ? run.getSinkCount() : checkResult.sinkCount;
        Long errorCount = checkResult.errorCount == null ? run.getErrorCount() : checkResult.errorCount;
        syncBatchService.updateMetrics(batch.getBatchId(), sourceCount, sinkCount, errorCount);
        syncRunService.updateMetrics(run.getRunId(), sourceCount, sinkCount, errorCount);
        batch.setSourceCount(sourceCount);
        batch.setSinkCount(sinkCount);
        batch.setErrorCount(errorCount);
        run.setSourceCount(sourceCount);
        run.setSinkCount(sinkCount);
        run.setErrorCount(errorCount);
    }

    private Object rowValue(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null || isBlank(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null || isBlank(String.valueOf(value))) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text)
                || "1".equals(text)
                || "yes".equalsIgnoreCase(text)
                || "y".equalsIgnoreCase(text);
    }

    private SyncRunStatus toRunStatus(SyncJobStatusResult status) {
        String normalized = normalizeJobStatus(status);
        if ("FINISHED".equals(normalized) || "SUCCESS".equals(normalized)) {
            return SyncRunStatus.SUCCESS;
        }
        if ("CANCELED".equals(normalized) || "CANCELLED".equals(normalized)) {
            return SyncRunStatus.CANCELED;
        }
        return SyncRunStatus.FAILED;
    }

    private SyncBatchStatus toBatchStatus(SyncJobStatusResult status) {
        String normalized = normalizeJobStatus(status);
        if ("FINISHED".equals(normalized) || "SUCCESS".equals(normalized)) {
            return SyncBatchStatus.SUCCESS;
        }
        if ("CANCELED".equals(normalized) || "CANCELLED".equals(normalized)) {
            return SyncBatchStatus.CANCELED;
        }
        return SyncBatchStatus.FAILED;
    }

    private String terminalErrorMessage(SyncJobStatusResult status) {
        String normalized = normalizeJobStatus(status);
        String message = firstNonBlank(status.getErrorMessage(), "SeaTunnel job terminal status: " + normalized);
        if ("TIMEOUT".equals(normalized)) {
            return message;
        }
        if ("CANCELED".equals(normalized) || "CANCELLED".equals(normalized)) {
            return "SeaTunnel job canceled: " + status.getStatus()
                    + (isBlank(status.getErrorMessage()) ? "" : ", " + status.getErrorMessage());
        }
        return "SeaTunnel job failed: " + status.getStatus()
                + (isBlank(status.getErrorMessage()) ? "" : ", " + status.getErrorMessage());
    }

    private String normalizeJobStatus(SyncJobStatusResult status) {
        return status == null || status.getStatus() == null
                ? ""
                : status.getStatus().trim().toUpperCase(Locale.ROOT);
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

    private void fillConfig(
            JobDefinitionEntity definition,
            SyncIncrementalConfigEntity entity,
            BatchLinkUpIncrementalConfigRequest request
    ) {
        entity.setTaskId(definition.getId());
        entity.setBatchLinkUpTaskId(definition.getId());
        entity.setSourceType(SyncSourceType.SQL);
        entity.setStrategy(parseRangeType(request.getRangeType()));
        entity.setWatermarkKey(SyncConstants.DEFAULT_WATERMARK_KEY);
        entity.setWatermarkFieldType(SyncWatermarkValueType.STRING);
        entity.setEnabled(defaultBoolean(request.getEnabled(), true));
        entity.setRangeType(defaultString(request.getRangeType(), "ID_RANGE"));
        entity.setBoundaryMode(parseEnum(SyncBoundaryMode.class, request.getBoundaryMode(), SyncBoundaryMode.SEPARATE_SQL));
        entity.setStartValueSource(parseEnum(
                SyncBoundaryValueSource.class,
                request.getStartValueSource(),
                SyncBoundaryValueSource.WATERMARK
        ));
        entity.setEndValueSource(parseEnum(
                SyncBoundaryValueSource.class,
                request.getEndValueSource(),
                SyncBoundaryValueSource.SQL
        ));
        entity.setStartTimeSource(parseEnum(
                SyncBoundaryValueSource.class,
                request.getStartTimeSource(),
                SyncBoundaryValueSource.NONE
        ));
        entity.setEndTimeSource(parseEnum(
                SyncBoundaryValueSource.class,
                request.getEndTimeSource(),
                SyncBoundaryValueSource.NONE
        ));
        entity.setBoundaryDatasourceId(request.getBoundaryDatasourceId());
        entity.setBatchPrepareSql(request.getBatchPrepareSql());
        entity.setBatchStartValueSql(request.getBatchStartValueSql());
        entity.setBatchEndValueSql(request.getBatchEndValueSql());
        entity.setBatchStartTimeSql(request.getBatchStartTimeSql());
        entity.setBatchEndTimeSql(request.getBatchEndTimeSql());
        entity.setFixedStartValue(request.getFixedStartValue());
        entity.setFixedEndValue(request.getFixedEndValue());
        entity.setFixedStartTime(request.getFixedStartTime());
        entity.setFixedEndTime(request.getFixedEndTime());
        entity.setDefaultParamsJson(request.getDefaultParamsJson());
        entity.setCustomContextJson(request.getCustomContextJson());
        entity.setSuccessUpdateWatermark(defaultBoolean(request.getSuccessUpdateWatermark(), true));
        entity.setCheckEnabled(defaultBoolean(request.getCheckEnabled(), false));
        entity.setCheckDatasourceId(request.getCheckDatasourceId());
        entity.setCheckSql(request.getCheckSql());
    }

    private void upsertDefaultCheckConfig(
            Long taskId,
            SyncIncrementalConfigEntity config,
            BatchLinkUpIncrementalConfigRequest request
    ) {
        SyncCheckConfigEntity existing =
                syncCheckConfigService.getByTaskIdAndCheckCode(taskId, DEFAULT_CHECK_CODE);
        if (!Boolean.TRUE.equals(config.getCheckEnabled()) && existing == null) {
            return;
        }
        SyncCheckConfigEntity entity = existing == null ? new SyncCheckConfigEntity() : existing;
        entity.setTaskId(taskId);
        entity.setCheckCode(DEFAULT_CHECK_CODE);
        entity.setCheckName("Incremental default check");
        entity.setCheckType(parseEnum(SyncCheckType.class, request.getCheckType(), SyncCheckType.CUSTOM_BOOLEAN));
        entity.setDatasourceId(config.getCheckDatasourceId());
        entity.setSqlText(isBlank(config.getCheckSql()) ? "select true" : config.getCheckSql());
        entity.setExpectedOperator(parseEnum(
                SyncCheckExpectedOperator.class,
                request.getCheckExpectedOperator(),
                null
        ));
        entity.setExpectedValue(request.getCheckExpectedValue());
        entity.setFailOnMismatch(true);
        entity.setEnabled(Boolean.TRUE.equals(config.getCheckEnabled()));
        entity.setSortOrder(0);
        entity.setDescription("Managed by batch-link-up incremental config");
        if (entity.getId() == null) {
            syncCheckConfigService.create(entity);
        } else {
            syncCheckConfigService.update(entity);
        }
    }

    private SyncIncrementalConfigEntity defaultConfig(JobDefinitionEntity definition) {
        SyncIncrementalConfigEntity entity = new SyncIncrementalConfigEntity();
        entity.setTaskId(definition.getId());
        entity.setBatchLinkUpTaskId(definition.getId());
        entity.setEnabled(false);
        entity.setRangeType("ID_RANGE");
        entity.setBoundaryMode(SyncBoundaryMode.SEPARATE_SQL);
        entity.setStartValueSource(SyncBoundaryValueSource.WATERMARK);
        entity.setEndValueSource(SyncBoundaryValueSource.SQL);
        entity.setStartTimeSource(SyncBoundaryValueSource.NONE);
        entity.setEndTimeSource(SyncBoundaryValueSource.NONE);
        entity.setSuccessUpdateWatermark(true);
        entity.setCheckEnabled(false);
        entity.setWatermarkKey(SyncConstants.DEFAULT_WATERMARK_KEY);
        return entity;
    }

    private SyncIncrementalConfigEntity loadEnabledConfig(JobDefinitionEntity definition, boolean requireEnabled) {
        SyncIncrementalConfigEntity config =
                syncIncrementalConfigService.getByBatchLinkUpTaskId(definition.getId());
        if (config == null) {
            throw new ServiceException("Batch-link-up incremental config not found, taskId=" + definition.getId());
        }
        if (requireEnabled && !Boolean.TRUE.equals(config.getEnabled())) {
            throw new ServiceException("Batch-link-up incremental config is disabled, taskId=" + definition.getId());
        }
        return config;
    }

    private JobDefinitionEntity loadDefinition(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskId");
        }
        return batchJobDefinitionQueryService.getDefinitionOrThrow(taskId);
    }

    private HoconBundle loadHocon(Long taskId) {
        JobDefinitionContentEntity content = jobDefinitionContentDao.queryLatestByJobDefinitionId(taskId);
        if (content == null) {
            throw new ServiceException("Batch-link-up definition content not found, taskId=" + taskId);
        }
        JobDefinitionSaveCommand command = jobDefinitionCommandResolver.resolve(taskId);
        String hocon = jobDefinitionHoconBuilder.build(command);
        if (isBlank(hocon)) {
            throw new ServiceException("Batch-link-up HOCON is empty, taskId=" + taskId);
        }
        HoconBundle bundle = new HoconBundle();
        bundle.versionId = content.getId();
        bundle.versionNo = content.getVersion();
        bundle.originalHocon = hocon;
        if (command instanceof BatchJobSaveCommand) {
            bundle.scheduleConfig = ((BatchJobSaveCommand) command).getSchedule();
        }
        return bundle;
    }

    private void validateRunnableDefinition(JobDefinitionEntity definition) {
        if (definition.getClientId() == null) {
            throw new ServiceException("batch-link-up clientId is required for incremental run, taskId="
                    + definition.getId());
        }
        if (definition.getReleaseState() != ReleaseState.ONLINE) {
            throw new ServiceException("Only ONLINE batch-link-up task can run incremental, taskId="
                    + definition.getId());
        }
    }

    private SyncTaskEntity toSyncTask(JobDefinitionEntity definition, SyncIncrementalConfigEntity config) {
        return SyncTaskEntity.builder()
                .id(definition.getId())
                .taskCode(taskCode(definition))
                .taskName(definition.getJobName())
                .taskType(SyncTaskType.BATCH)
                .sourceType(SyncSourceType.SQL)
                .sinkType(resolveSinkType(definition))
                .engineType(SyncEngineType.ZETA)
                .clientId(definition.getClientId())
                .incrementalEnabled(Boolean.TRUE.equals(config.getEnabled()))
                .incrementalStrategy(parseRangeType(config.getRangeType()))
                .status(SyncTaskStatus.PUBLISHED)
                .description(definition.getJobDesc())
                .build();
    }

    private SyncTaskVersionEntity toSyncVersion(HoconBundle hocon) {
        SyncTaskVersionEntity version = new SyncTaskVersionEntity();
        version.setId(hocon.versionId);
        version.setVersionNo(hocon.versionNo);
        version.setHoconTemplate(hocon.originalHocon);
        return version;
    }

    private Long resolveBoundaryDatasourceId(JobDefinitionEntity definition, SyncIncrementalConfigEntity config) {
        if (config.getBoundaryDatasourceId() != null) {
            return config.getBoundaryDatasourceId();
        }
        if (definition.getSourceDatasourceId() != null) {
            return definition.getSourceDatasourceId();
        }
        return definition.getSinkDatasourceId();
    }

    private SyncIncrementalStrategy parseRangeType(String value) {
        if (isBlank(value)) {
            return SyncIncrementalStrategy.ID_RANGE;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("CUSTOM".equals(normalized)) {
            return SyncIncrementalStrategy.CUSTOM_SQL_CURSOR;
        }
        return parseEnum(SyncIncrementalStrategy.class, normalized, SyncIncrementalStrategy.ID_RANGE);
    }

    private SyncSinkType resolveSinkType(JobDefinitionEntity definition) {
        if (definition.getSinkType() != null) {
            String sinkType = definition.getSinkType().trim().toUpperCase(Locale.ROOT);
            if (sinkType.contains("STARROCKS")) {
                return SyncSinkType.STARROCKS;
            }
        }
        return SyncSinkType.JDBC;
    }

    private BatchLinkUpIncrementalConfigVO toConfigVO(SyncIncrementalConfigEntity config) {
        BatchLinkUpIncrementalConfigVO vo = new BatchLinkUpIncrementalConfigVO();
        vo.setId(config.getId());
        vo.setTaskId(config.getTaskId());
        vo.setBatchLinkUpTaskId(config.getBatchLinkUpTaskId());
        vo.setEnabled(config.getEnabled());
        vo.setRangeType(config.getRangeType());
        vo.setBoundaryMode(code(config.getBoundaryMode()));
        vo.setStartValueSource(code(config.getStartValueSource()));
        vo.setEndValueSource(code(config.getEndValueSource()));
        vo.setStartTimeSource(code(config.getStartTimeSource()));
        vo.setEndTimeSource(code(config.getEndTimeSource()));
        vo.setBoundaryDatasourceId(config.getBoundaryDatasourceId());
        vo.setBatchPrepareSql(config.getBatchPrepareSql());
        vo.setBatchStartValueSql(config.getBatchStartValueSql());
        vo.setBatchEndValueSql(config.getBatchEndValueSql());
        vo.setBatchStartTimeSql(config.getBatchStartTimeSql());
        vo.setBatchEndTimeSql(config.getBatchEndTimeSql());
        vo.setFixedStartValue(config.getFixedStartValue());
        vo.setFixedEndValue(config.getFixedEndValue());
        vo.setFixedStartTime(config.getFixedStartTime());
        vo.setFixedEndTime(config.getFixedEndTime());
        vo.setDefaultParamsJson(config.getDefaultParamsJson());
        vo.setCustomContextJson(config.getCustomContextJson());
        vo.setSuccessUpdateWatermark(config.getSuccessUpdateWatermark());
        vo.setCheckEnabled(config.getCheckEnabled());
        vo.setCheckDatasourceId(config.getCheckDatasourceId());
        vo.setCheckSql(config.getCheckSql());
        vo.setCreateTime(formatDate(config.getCreateTime()));
        vo.setUpdateTime(formatDate(config.getUpdateTime()));
        return vo;
    }

    private SyncRunListItemVO toRunListItem(
            String taskCode,
            String taskName,
            SyncRunEntity run,
            SyncBatchEntity batch
    ) {
        SyncRunListItemVO vo = new SyncRunListItemVO();
        vo.setId(run.getId());
        vo.setRunId(run.getRunId());
        vo.setBatchId(run.getBatchId());
        vo.setTaskId(run.getTaskId());
        vo.setTaskCode(taskCode);
        vo.setRunType("INCREMENTAL");
        vo.setTriggerType(code(run.getTriggerType()));
        vo.setSchedulerRunId(run.getSchedulerRunId());
        vo.setStatus(code(run.getStatus()));
        vo.setBatchStatus(batch == null ? null : code(batch.getStatus()));
        vo.setSeatunnelJobId(run.getSeatunnelJobId());
        vo.setSeatunnelJobName(run.getSeatunnelJobName());
        vo.setBatchStartValue(batch == null ? null : batch.getBatchStartValue());
        vo.setBatchEndValue(batch == null ? null : batch.getBatchEndValue());
        vo.setBatchStartTime(batch == null ? null : formatDate(batch.getBatchStartTime()));
        vo.setBatchEndTime(batch == null ? null : formatDate(batch.getBatchEndTime()));
        vo.setSubmitTime(formatDate(run.getSubmitTime()));
        vo.setStartTime(formatDate(run.getStartTime()));
        vo.setEndTime(formatDate(run.getEndTime()));
        vo.setCreateTime(formatDate(run.getCreateTime()));
        vo.setUpdateTime(formatDate(run.getUpdateTime()));
        vo.setSourceCount(run.getSourceCount());
        vo.setSinkCount(run.getSinkCount());
        vo.setErrorCount(run.getErrorCount());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setGeneratedHocon(run.getGeneratedHocon());
        return vo;
    }

    private RunDetailVO toRunDetail(
            JobDefinitionEntity definition,
            SyncRunEntity run,
            SyncBatchEntity batch,
            List<SyncAuditEntity> audits,
            List<SyncCheckResultEntity> checkResults
    ) {
        RunDetailVO vo = new RunDetailVO();
        vo.setTaskId(run.getTaskId());
        vo.setTaskCode(taskCode(definition));
        vo.setRunId(run.getRunId());
        vo.setBatchId(run.getBatchId());
        vo.setTaskVersionId(run.getTaskVersionId());
        vo.setRunType("INCREMENTAL");
        vo.setTriggerType(code(run.getTriggerType()));
        vo.setSchedulerRunId(run.getSchedulerRunId());
        vo.setRunStatus(code(run.getStatus()));
        vo.setBatchStatus(batch == null ? null : code(batch.getStatus()));
        vo.setSeatunnelJobId(run.getSeatunnelJobId());
        vo.setSeatunnelJobName(run.getSeatunnelJobName());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setGeneratedHocon(run.getGeneratedHocon());
        vo.setBatchStartValue(batch == null ? null : batch.getBatchStartValue());
        vo.setBatchEndValue(batch == null ? null : batch.getBatchEndValue());
        vo.setBatchStartTime(batch == null ? null : formatDate(batch.getBatchStartTime()));
        vo.setBatchEndTime(batch == null ? null : formatDate(batch.getBatchEndTime()));
        vo.setSourceCount(run.getSourceCount());
        vo.setSinkCount(run.getSinkCount());
        vo.setErrorCount(run.getErrorCount());
        vo.setCreateTime(formatDate(run.getCreateTime()));
        vo.setUpdateTime(formatDate(run.getUpdateTime()));
        vo.setSubmitTime(formatDate(run.getSubmitTime()));
        vo.setStartTime(formatDate(run.getStartTime()));
        vo.setEndTime(formatDate(run.getEndTime()));
        vo.setAudits(audits.stream().map(this::toAuditVO).collect(Collectors.toList()));
        vo.setCheckResults(checkResults.stream().map(this::toCheckResultVO).collect(Collectors.toList()));
        return vo;
    }

    private SyncCheckResultVO toCheckResultVO(SyncCheckResultEntity entity) {
        SyncCheckResultVO vo = new SyncCheckResultVO();
        vo.setId(entity.getId());
        vo.setRunId(entity.getRunId());
        vo.setBatchId(entity.getBatchId());
        vo.setTaskId(entity.getTaskId());
        vo.setTaskCode(entity.getTaskCode());
        vo.setCheckCode(entity.getCheckCode());
        vo.setCheckName(entity.getCheckName());
        vo.setCheckType(code(entity.getCheckType()));
        vo.setRenderedSql(entity.getRenderedSql());
        vo.setActualValue(entity.getActualValue());
        vo.setExpectedOperator(code(entity.getExpectedOperator()));
        vo.setExpectedValue(entity.getExpectedValue());
        vo.setCompareToCheckCode(entity.getCompareToCheckCode());
        vo.setCompareToActualValue(entity.getCompareToActualValue());
        vo.setPassed(entity.getPassed());
        vo.setFailOnMismatch(entity.getFailOnMismatch());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setStartTime(formatDate(entity.getStartTime()));
        vo.setEndTime(formatDate(entity.getEndTime()));
        vo.setCreateTime(formatDate(entity.getCreateTime()));
        return vo;
    }

    private SyncAuditItemVO toAuditVO(SyncAuditEntity entity) {
        SyncAuditItemVO vo = new SyncAuditItemVO();
        vo.setId(entity.getId());
        vo.setRunId(entity.getRunId());
        vo.setBatchId(entity.getBatchId());
        vo.setTaskId(entity.getTaskId());
        vo.setTaskCode(entity.getTaskCode());
        vo.setEventType(code(entity.getEventType()));
        vo.setEventLevel(code(entity.getEventLevel()));
        vo.setEventMessage(entity.getEventMessage());
        vo.setDetailJson(entity.getDetailJson());
        vo.setCreateTime(formatDate(entity.getCreateTime()));
        return vo;
    }

    private SyncBatchListItemVO toBatchListItem(SyncBatchEntity batch) {
        SyncBatchListItemVO vo = new SyncBatchListItemVO();
        vo.setBatchId(batch.getBatchId());
        vo.setTaskCode(batch.getTaskCode());
        vo.setStatus(code(batch.getStatus()));
        vo.setBatchStartValue(batch.getBatchStartValue());
        vo.setBatchEndValue(batch.getBatchEndValue());
        vo.setBatchStartTime(formatDate(batch.getBatchStartTime()));
        vo.setBatchEndTime(formatDate(batch.getBatchEndTime()));
        vo.setSourceCount(batch.getSourceCount());
        vo.setSinkCount(batch.getSinkCount());
        vo.setErrorCount(batch.getErrorCount());
        vo.setErrorMessage(batch.getErrorMessage());
        vo.setCreateTime(formatDate(batch.getCreateTime()));
        vo.setUpdateTime(formatDate(batch.getUpdateTime()));
        return vo;
    }

    private WatermarkVO toWatermarkVO(String taskCode, SyncWatermarkEntity watermark) {
        WatermarkVO vo = new WatermarkVO();
        vo.setId(watermark.getId());
        vo.setTaskId(watermark.getTaskId());
        vo.setTaskCode(taskCode);
        vo.setWatermarkKey(watermark.getWatermarkKey());
        vo.setCurrentValue(watermark.getCurrentValue());
        vo.setPreviousValue(watermark.getPreviousValue());
        vo.setCurrentValueType(code(watermark.getCurrentValueType()));
        vo.setLastSuccessRunId(watermark.getLastSuccessRunId());
        vo.setLastSuccessBatchId(watermark.getLastSuccessBatchId());
        vo.setUpdateTime(formatDate(watermark.getUpdateTime()));
        return vo;
    }

    private RunResultVO toRunResult(
            SyncTaskEntity task,
            SyncTaskVersionEntity version,
            SyncBatchEntity batch,
            SyncRunEntity run,
            SyncJobStatusResult jobStatus,
            boolean watermarkAdvanced,
            String watermarkValue,
            String errorMessage
    ) {
        RunResultVO result = new RunResultVO();
        result.setRunId(run.getRunId());
        result.setBatchId(batch.getBatchId());
        result.setTaskId(task.getId());
        result.setTaskCode(task.getTaskCode());
        result.setTaskVersionId(version.getId());
        result.setSeatunnelJobId(run.getSeatunnelJobId());
        result.setSeatunnelJobName(run.getSeatunnelJobName());
        result.setJobStatus(jobStatus == null ? null : jobStatus.getStatus());
        result.setStatus(code(run.getStatus()));
        result.setRunStatus(code(run.getStatus()));
        result.setBatchStatus(code(batch.getStatus()));
        result.setSourceCount(run.getSourceCount());
        result.setSinkCount(run.getSinkCount());
        result.setErrorCount(run.getErrorCount());
        result.setWatermarkUpdated(watermarkAdvanced);
        result.setWatermarkAdvanced(watermarkAdvanced);
        result.setWatermarkValue(watermarkValue);
        result.setErrorMessage(firstNonBlank(errorMessage, run.getErrorMessage()));
        return result;
    }

    private <T> PaginationResult<T> page(List<T> items, Integer pageNo, Integer pageSize) {
        int safePageNo = pageNo == null || pageNo <= 0 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize <= 0 ? 20 : pageSize;
        int from = Math.min((safePageNo - 1) * safePageSize, items.size());
        int to = Math.min(from + safePageSize, items.size());
        return PaginationResult.buildSuc(items.subList(from, to), (long) items.size(), safePageNo, safePageSize);
    }

    private boolean matchesStatus(String expected, String actual) {
        return isBlank(expected) || Objects.equals(expected.trim().toUpperCase(Locale.ROOT), actual);
    }

    private boolean matchesTimeRange(Date value, Date start, Date end) {
        if (value == null) {
            return start == null && end == null;
        }
        if (start != null && value.before(start)) {
            return false;
        }
        return end == null || !value.after(end);
    }

    private boolean matchesKeyword(SyncRunEntity run, SyncBatchEntity batch, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(run.getRunId(), normalized)
                || containsIgnoreCase(run.getBatchId(), normalized)
                || containsIgnoreCase(run.getSeatunnelJobId(), normalized)
                || containsIgnoreCase(run.getSeatunnelJobName(), normalized)
                || containsIgnoreCase(run.getErrorMessage(), normalized)
                || (batch != null && containsIgnoreCase(batch.getErrorMessage(), normalized));
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private Date parseDateTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Date.from(LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
        } catch (DateTimeParseException e) {
            throw new ServiceException("Invalid date time: " + value);
        }
    }

    private void recordSqlFailure(
            BatchLinkUpIncrementalContextVO vo,
            String name,
            Long datasourceId,
            String sql,
            String message
    ) {
        BatchLinkUpIncrementalContextVO.SqlExecutionVO item =
                new BatchLinkUpIncrementalContextVO.SqlExecutionVO();
        item.setName(name);
        item.setDatasourceId(datasourceId);
        item.setRenderedSql(sql);
        item.setSuccess(false);
        item.setErrorMessage(message);
        vo.getExecutedSqls().add(item);
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (isBlank(json)) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = JSONUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
        return parsed == null ? Collections.emptyMap() : parsed;
    }

    private void putCustomVariables(Map<String, Object> variables, Map<String, Object> customContext) {
        if (customContext == null) {
            return;
        }
        customContext.forEach((key, value) -> variables.put("custom." + key, value));
    }

    private void copyIfPresent(Map<String, Object> row, Map<String, Object> variables, String key) {
        Object value = row.get(key);
        if (value != null) {
            variables.put(key, value);
            variables.put(preparedMarker(key), true);
        }
    }

    private String preparedMarker(String key) {
        return "__prepared_" + key;
    }

    private void removePreparedMarkers(Map<String, Object> variables) {
        List<String> keys = variables.keySet()
                .stream()
                .filter(key -> key.startsWith("__prepared_"))
                .collect(Collectors.toList());
        keys.forEach(variables::remove);
    }

    private Object findCamelParam(Map<String, Object> variables, String snakeKey) {
        String[] parts = snakeKey.split("_");
        StringBuilder builder = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                builder.append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT)).append(parts[i].substring(1));
            }
        }
        return variables.get(builder.toString());
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp) {
            return DATE_TIME_FORMATTER.format(((java.sql.Timestamp) value).toLocalDateTime());
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate().toString();
        }
        if (value instanceof Date) {
            return formatDate((Date) value);
        }
        if (value instanceof LocalDateTime) {
            return DATE_TIME_FORMATTER.format((LocalDateTime) value);
        }
        if (value instanceof LocalDate) {
            return value.toString();
        }
        return value;
    }

    private Date toDate(Object value) {
        if (value == null || isBlank(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        String text = String.valueOf(value).trim();
        try {
            return Date.from(LocalDateTime.parse(text, DATE_TIME_FORMATTER)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String taskCode(JobDefinitionEntity definition) {
        return "batch_link_up_" + definition.getId();
    }

    private String generateBatchId(String taskCode) {
        return taskCode + "_" + ID_TIME_FORMATTER.format(LocalDateTime.now())
                + "_" + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String generateRunId(String taskCode) {
        return taskCode + "_run_" + ID_TIME_FORMATTER.format(LocalDateTime.now())
                + "_" + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private SyncWatermarkValueType resolveWatermarkValueType(SyncIncrementalConfigEntity config) {
        return config == null || config.getWatermarkFieldType() == null
                ? SyncWatermarkValueType.STRING
                : config.getWatermarkFieldType();
    }

    private String defaultWatermarkKey(String value) {
        return isBlank(value) ? SyncConstants.DEFAULT_WATERMARK_KEY : value;
    }

    private String renderIncrementalHocon(HoconBundle hocon, IncrementalContext context) {
        return hoconRenderService.render(hocon.originalHocon, context.variables);
    }

    private Map<String, String> renderScheduleParams(JobScheduleConfig scheduleConfig) {
        return timeVariableJdbcSqlRenderService.renderScheduleParams(scheduleConfig);
    }

    private List<String> findReservedParamConflicts(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> conflicts = new ArrayList<>();
        params.keySet().forEach(key -> {
            if (key != null && (RESERVED_SYSTEM_VARIABLES.contains(key) || key.startsWith("custom."))) {
                conflicts.add(key);
            }
        });
        return conflicts;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }

    private String valueToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String formatDate(Date value) {
        if (value == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault()));
    }

    private String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private Boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String code(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return String.valueOf(value.getClass().getMethod("getCode").invoke(value));
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, E defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ServiceException("Unsupported " + enumClass.getSimpleName() + ": " + value);
        }
    }

    private SyncTriggerType parseTriggerType(String value) {
        if (isBlank(value)) {
            return SyncTriggerType.MANUAL;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("SCHEDULE".equals(normalized) || "SCHEDULED".equals(normalized)) {
            return SyncTriggerType.SCHEDULED;
        }
        return parseEnum(SyncTriggerType.class, normalized, SyncTriggerType.MANUAL);
    }

    private Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private static class HoconBundle {
        private Long versionId;
        private Integer versionNo;
        private String originalHocon;
        private JobScheduleConfig scheduleConfig;
    }

    private static class IncrementalContext {
        private BatchLinkUpIncrementalContextVO vo;
        private Map<String, Object> variables;
    }

    private static class WatermarkAdvanceResult {
        private boolean updated;
        private String value;
        private String errorMessage;

        private static WatermarkAdvanceResult updated(String value) {
            WatermarkAdvanceResult result = new WatermarkAdvanceResult();
            result.updated = true;
            result.value = value;
            return result;
        }

        private static WatermarkAdvanceResult skipped(String errorMessage) {
            WatermarkAdvanceResult result = new WatermarkAdvanceResult();
            result.updated = false;
            result.errorMessage = errorMessage;
            return result;
        }

        private static WatermarkAdvanceResult failed(String errorMessage) {
            return skipped(errorMessage);
        }
    }

    private static class IncrementalCheckResult {
        private QueryResult queryResult;
        private Map<String, Object> row = Collections.emptyMap();
        private Long sourceCount;
        private Long sinkCount;
        private Long errorCount;
        private boolean passed = true;
        private String message;

        private Map<String, Object> auditDetail() {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("sourceCount", sourceCount);
            detail.put("sinkCount", sinkCount);
            detail.put("errorCount", errorCount);
            detail.put("checkPassed", passed);
            detail.put("message", message);
            detail.put("renderedSql", queryResult == null ? null : queryResult.renderedSql);
            detail.put("row", row);
            return detail;
        }
    }

    private static class QueryResult {
        private String name;
        private Long datasourceId;
        private String renderedSql;
        private final List<String> columns = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private Object scalarValue;

        private BatchLinkUpIncrementalContextVO.SqlExecutionVO toVo() {
            BatchLinkUpIncrementalContextVO.SqlExecutionVO vo =
                    new BatchLinkUpIncrementalContextVO.SqlExecutionVO();
            vo.setName(name);
            vo.setDatasourceId(datasourceId);
            vo.setRenderedSql(renderedSql);
            vo.setColumns(columns);
            vo.setRows(rows);
            vo.setScalarValue(scalarValue);
            vo.setSuccess(true);
            return vo;
        }
    }
}
