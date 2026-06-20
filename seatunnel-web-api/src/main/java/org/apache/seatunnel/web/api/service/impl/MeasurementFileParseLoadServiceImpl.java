package org.apache.seatunnel.web.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.api.service.IncrementalTaskLockService;
import org.apache.seatunnel.web.api.service.MeasurementFileParseLoadService;
import org.apache.seatunnel.web.api.service.SyncZetaClient;
import org.apache.seatunnel.web.api.service.file.FileSourceClient;
import org.apache.seatunnel.web.api.service.model.FileDataSourceConfig;
import org.apache.seatunnel.web.api.service.model.FileSourceTestResult;
import org.apache.seatunnel.web.api.service.model.IncrementalLockResult;
import org.apache.seatunnel.web.api.service.model.SyncJobStatusResult;
import org.apache.seatunnel.web.api.service.model.SyncSubmitJobResult;
import org.apache.seatunnel.web.api.utils.HoconSensitiveMaskUtil;
import org.apache.seatunnel.web.common.enums.MeasurementFileStatus;
import org.apache.seatunnel.web.common.enums.MeasurementLoadBatchMode;
import org.apache.seatunnel.web.common.enums.MeasurementLoadMode;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;
import org.apache.seatunnel.web.common.enums.MeasurementRunPhase;
import org.apache.seatunnel.web.common.enums.MeasurementRunStatus;
import org.apache.seatunnel.web.common.enums.SeaTunnelClientHealthStatusEnum;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.DataSource;
import org.apache.seatunnel.web.dao.entity.MeasurementFileEntity;
import org.apache.seatunnel.web.dao.entity.MeasurementFileRunEntity;
import org.apache.seatunnel.web.dao.entity.MeasurementFileSyncTaskEntity;
import org.apache.seatunnel.web.dao.entity.SeaTunnelClient;
import org.apache.seatunnel.web.dao.repository.DataSourceDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileRunDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileSyncTaskDao;
import org.apache.seatunnel.web.dao.repository.SeaTunnelClientDao;
import org.apache.seatunnel.web.engine.client.rest.SeaTunnelRestClient;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementParseLoadRequestDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementPreflightRequestDTO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementCheckItemVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementParseLoadResultVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementParsePreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementPreflightVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementSqlTemplateVO;
import org.apache.seatunnel.web.spi.enums.DbType;
import org.apache.seatunnel.web.spi.enums.Status;
import org.apache.seatunnel.web.spi.measurement.MeasurementFileContext;
import org.apache.seatunnel.web.spi.measurement.MeasurementFileParser;
import org.apache.seatunnel.web.spi.measurement.ParsedMeasurementResult;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MeasurementFileParseLoadServiceImpl implements MeasurementFileParseLoadService {

    private static final String LOCK_KEY_PARSE = "measurement_parse";
    private static final String LOCK_KEY_LOAD = "measurement_load";
    private static final String LOCK_KEY_PARSE_LOAD = "measurement_parse_load";
    private static final String DEFAULT_STAGING_DIR = "/opt/seatunnel-web/staging/measurement";
    private static final int DEFAULT_MAX_FILES_PER_PARSE_RUN = 100;
    private static final int DEFAULT_PREVIEW_ROWS = 20;

    @Resource
    private MeasurementFileSyncTaskDao taskDao;

    @Resource
    private MeasurementFileDao fileDao;

    @Resource
    private MeasurementFileRunDao runDao;

    @Resource
    private DataSourceDao dataSourceDao;

    @Resource
    private SeaTunnelClientDao seaTunnelClientDao;

    @Resource
    private FileSourceClient fileSourceClient;

    @Resource
    private IncrementalTaskLockService lockService;

    @Resource
    private SyncZetaClient syncZetaClient;

    @Resource
    private SeaTunnelRestClient seaTunnelRestClient;

    @Resource
    private SyncRunProperties syncRunProperties;

    private final Map<String, MeasurementFileParser> parserMap = new HashMap<>();

    public MeasurementFileParseLoadServiceImpl(List<MeasurementFileParser> parsers) {
        if (parsers != null) {
            for (MeasurementFileParser parser : parsers) {
                parserMap.put(parser.parserType().toUpperCase(Locale.ROOT), parser);
            }
        }
    }

    @Override
    public MeasurementPreflightVO preflight(Long taskId, MeasurementPreflightRequestDTO request) {
        MeasurementPreflightRequestDTO safeRequest = request == null ? new MeasurementPreflightRequestDTO() : request;
        MeasurementFileSyncTaskEntity task = loadTask(taskId);
        MeasurementPreflightVO result = new MeasurementPreflightVO();

        checkSourceDatasource(task, result);
        checkStagingDir(task, safeRequest, result);
        StarRocksTarget target = checkStarRocksTarget(task, result);
        checkSeaTunnelEngine(task, result);
        addWarning(result,
                "worker_staging_visibility",
                "Please confirm staging_dir is mounted to the SeaTunnel worker container: " + resolveStagingDir(task),
                null);

        if (task.getLoadMode() == MeasurementLoadMode.UPSERT) {
            addError(result, "load_mode", "UPSERT is reserved but not implemented yet.", null);
        }
        if (target != null) {
            checkStarRocksPorts(target, result);
        }
        result.setSuccess(result.getErrors().isEmpty());
        return result;
    }

    @Override
    public MeasurementSqlTemplateVO recommendedDdl(Long taskId) {
        MeasurementFileSyncTaskEntity task = loadTask(taskId);
        MeasurementSqlTemplateVO vo = new MeasurementSqlTemplateVO();
        vo.setSql(recommendedStarRocksDdl(taskTargetDatabase(task), task.getTargetTable()));
        vo.setWarning("APPEND mode with a DUPLICATE KEY table can insert duplicate rows on forced reload. "
                + "Production should use a Primary Key table or cleanup before reloading.");
        return vo;
    }

    @Override
    public MeasurementSqlTemplateVO cleanupSql(Long fileId) {
        MeasurementFileEntity file = loadFile(fileId);
        MeasurementFileSyncTaskEntity task = loadTask(file.getTaskId());
        MeasurementSqlTemplateVO vo = new MeasurementSqlTemplateVO();
        String database = taskTargetDatabase(task);
        String table = task.getTargetTable();
        vo.setSql("DELETE FROM `" + escapeSqlIdentifier(database) + "`.`" + escapeSqlIdentifier(table) + "`\n"
                + "WHERE file_id = " + file.getId() + ";\n\n"
                + "DELETE FROM `" + escapeSqlIdentifier(database) + "`.`" + escapeSqlIdentifier(table) + "`\n"
                + "WHERE batch_id = '" + escapeSqlLiteral(file.getBatchId()) + "';");
        vo.setWarning("Run cleanup only when the target table supports the expected delete semantics.");
        return vo;
    }

    @Override
    public MeasurementSqlTemplateVO generatedHocon(String runId) {
        MeasurementFileRunEntity run = runDao.queryByRunId(runId);
        if (run == null) {
            throw new ServiceException("Measurement file run not found, runId=" + runId);
        }
        MeasurementSqlTemplateVO vo = new MeasurementSqlTemplateVO();
        vo.setSql(run.getGeneratedHocon());
        vo.setWarning("Sensitive fields are masked before the HOCON is stored.");
        return vo;
    }

    @Override
    public MeasurementParseLoadResultVO parseAndLoad(Long taskId, MeasurementParseLoadRequestDTO request) {
        return execute(taskId, safeRequest(request), MeasurementRunPhase.PARSE_LOAD);
    }

    @Override
    public MeasurementParseLoadResultVO parseOnly(Long taskId, MeasurementParseLoadRequestDTO request) {
        return execute(taskId, safeRequest(request), MeasurementRunPhase.PARSE);
    }

    @Override
    public MeasurementParseLoadResultVO loadParsed(Long taskId, MeasurementParseLoadRequestDTO request) {
        return execute(taskId, safeRequest(request), MeasurementRunPhase.LOAD);
    }

    @Override
    public MeasurementParseLoadResultVO retryFailedFile(Long fileId) {
        MeasurementFileEntity file = loadFile(fileId);
        MeasurementParseLoadRequestDTO request = new MeasurementParseLoadRequestDTO();
        request.setFileIds(List.of(fileId));
        request.setRetryParseFailed(true);
        request.setRetryLoadFailed(true);
        request.setWaitForLoadFinish(true);
        request.setTriggerType(SyncTriggerType.MANUAL);
        if (file.getFileStatus() == MeasurementFileStatus.PARSE_FAILED) {
            return parseAndLoad(file.getTaskId(), request);
        }
        if (file.getFileStatus() == MeasurementFileStatus.LOAD_FAILED
                || file.getFileStatus() == MeasurementFileStatus.LOAD_PENDING
                || file.getFileStatus() == MeasurementFileStatus.PARSED) {
            return loadParsed(file.getTaskId(), request);
        }
        return parseAndLoad(file.getTaskId(), request);
    }

    @Override
    public MeasurementParseLoadResultVO markFileFailed(Long fileId) {
        MeasurementFileEntity file = loadFile(fileId);
        MeasurementFileStatus failedStatus = file.getFileStatus() == MeasurementFileStatus.LOADING
                ? MeasurementFileStatus.LOAD_FAILED
                : MeasurementFileStatus.PARSE_FAILED;
        markFileFailed(file, failedStatus, "Manually marked as failed");
        MeasurementFileRunEntity run = manualRepairRun(file, "mark_failed");
        run.setFailedCount(1);
        run.setParseFailedCount(failedStatus == MeasurementFileStatus.PARSE_FAILED ? 1 : 0);
        run.setLoadFailedCount(failedStatus == MeasurementFileStatus.LOAD_FAILED ? 1 : 0);
        runDao.updateById(run);
        return toResult(file.getTaskId(), run, List.of(file));
    }

    @Override
    public MeasurementParseLoadResultVO resetFilePending(Long fileId) {
        MeasurementFileEntity file = loadFile(fileId);
        MeasurementFileStatus nextStatus = file.getFileStatus() == MeasurementFileStatus.LOADING
                || file.getFileStatus() == MeasurementFileStatus.LOAD_FAILED
                || file.getFileStatus() == MeasurementFileStatus.LOADED
                ? MeasurementFileStatus.LOAD_PENDING
                : MeasurementFileStatus.PARSE_PENDING;
        file.setFileStatus(nextStatus);
        file.setErrorMessage(null);
        file.setUpdateTime(new Date());
        fileDao.updateById(file);
        MeasurementFileRunEntity run = manualRepairRun(file, "reset_pending");
        run.setSkippedCount(0);
        runDao.updateById(run);
        return toResult(file.getTaskId(), run, List.of(file));
    }

    @Override
    public MeasurementParseLoadResultVO repairStaleFiles(Long taskId, Integer timeoutMinutes, Boolean resetToPending) {
        MeasurementFileSyncTaskEntity task = loadTask(taskId);
        int minutes = timeoutMinutes == null || timeoutMinutes <= 0 ? 60 : timeoutMinutes;
        Date cutoffTime = Date.from(Instant.now().minusSeconds(minutes * 60L));
        List<MeasurementFileEntity> staleFiles = fileDao.listStaleByTaskAndStatuses(
                task.getId(),
                EnumSet.of(MeasurementFileStatus.PARSING, MeasurementFileStatus.LOADING),
                cutoffTime,
                DEFAULT_MAX_FILES_PER_PARSE_RUN);
        MeasurementFileRunEntity run = manualRepairRun(task, "repair_stale");
        run.setSelectedFileCount(staleFiles.size());
        for (MeasurementFileEntity file : staleFiles) {
            if (Boolean.TRUE.equals(resetToPending)) {
                file.setFileStatus(file.getFileStatus() == MeasurementFileStatus.LOADING
                        ? MeasurementFileStatus.LOAD_PENDING
                        : MeasurementFileStatus.PARSE_PENDING);
                file.setErrorMessage(null);
            } else {
                file.setFileStatus(file.getFileStatus() == MeasurementFileStatus.LOADING
                        ? MeasurementFileStatus.LOAD_FAILED
                        : MeasurementFileStatus.PARSE_FAILED);
                file.setErrorMessage("Stale in-progress status repaired after " + minutes + " minutes");
                if (file.getFileStatus() == MeasurementFileStatus.PARSE_FAILED) {
                    run.setParseFailedCount(zeroInt(run.getParseFailedCount()) + 1);
                } else {
                    run.setLoadFailedCount(zeroInt(run.getLoadFailedCount()) + 1);
                }
            }
            file.setUpdateTime(new Date());
            fileDao.updateById(file);
        }
        run.setStatus(MeasurementRunStatus.SUCCESS);
        run.setEndTime(new Date());
        run.setUpdateTime(new Date());
        runDao.updateById(run);
        return toResult(task.getId(), run, staleFiles);
    }

    @Override
    public MeasurementParsePreviewVO previewParse(Long fileId, Integer maxRows) {
        MeasurementFileEntity file = loadFile(fileId);
        MeasurementFileSyncTaskEntity task = loadTask(file.getTaskId());
        DataSource source = loadSourceDatasource(task);
        FileDataSourceConfig sourceConfig = fileSourceClient.parseConfig(source.getDbType(), source.getConnectionParams());
        MeasurementFileParser parser = resolveParser(task.getParserType());
        int previewRows = maxRows == null || maxRows <= 0 ? DEFAULT_PREVIEW_ROWS : Math.min(maxRows, 200);

        ParsedMeasurementResult parsed = fileSourceClient.withInputStream(
                source.getDbType(),
                sourceConfig,
                file.getFullPath(),
                inputStream -> {
                    MeasurementFileContext context = buildContext(task, file, "preview", "preview", inputStream);
                    context.getMetadata().put("maxRowsInMemory", previewRows);
                    return parser.parse(context);
                }
        );

        MeasurementParsePreviewVO vo = new MeasurementParsePreviewVO();
        vo.setFileId(file.getId());
        vo.setTaskId(file.getTaskId());
        vo.setFileName(file.getFileName());
        vo.setParserType(task.getParserType() == null ? null : task.getParserType().name());
        vo.setSuccess(Boolean.TRUE.equals(parsed.getSuccess()));
        vo.setRowCount(parsed.getRowCount());
        vo.setErrorRowCount(parsed.getErrorRowCount());
        vo.setErrorMessage(parsed.getErrorMessage());
        vo.setRows(parsed.getMeasurementRows());
        vo.setErrorRows(parsed.getErrorRows());
        return vo;
    }

    private MeasurementParseLoadResultVO execute(
            Long taskId,
            MeasurementParseLoadRequestDTO request,
            MeasurementRunPhase phase
    ) {
        MeasurementFileSyncTaskEntity task = loadTask(taskId);
        validatePhaseConfig(task, phase);

        String runId = generateRuntimeId("mfs_" + phase.getCode().toLowerCase(Locale.ROOT) + "_run", task.getTaskCode());
        String batchId = generateRuntimeId("mfs_" + phase.getCode().toLowerCase(Locale.ROOT) + "_batch", task.getTaskCode());
        Date now = new Date();
        MeasurementFileRunEntity run = MeasurementFileRunEntity.builder()
                .runId(runId)
                .batchId(batchId)
                .taskId(task.getId())
                .triggerType(request.getTriggerType() == null ? SyncTriggerType.MANUAL : request.getTriggerType())
                .status(MeasurementRunStatus.RUNNING)
                .runPhase(phase)
                .sourceDatasourceId(task.getSourceDatasourceId())
                .scannedCount(0)
                .discoveredCount(0)
                .skippedCount(0)
                .failedCount(0)
                .selectedFileCount(0)
                .parsedFileCount(0)
                .loadedFileCount(0)
                .parseFailedCount(0)
                .loadFailedCount(0)
                .parsedRowCount(0L)
                .loadedRowCount(0L)
                .stagingDir(resolveStagingDir(task))
                .targetDatasourceId(task.getTargetDatasourceId())
                .targetDatabase(task.getTargetDatabase())
                .targetTable(task.getTargetTable())
                .startTime(now)
                .createTime(now)
                .updateTime(now)
                .build();
        runDao.insert(run);

        String lockKey = lockKey(phase);
        IncrementalLockResult lock = lockService.acquireLock(
                task.getId(),
                lockKey,
                runId,
                batchId,
                task.getLockTtlMinutes() == null ? null : task.getLockTtlMinutes().longValue()
        );
        if (!lock.isAcquired()) {
            String message = "Another measurement file " + phase.getCode() + " run is active";
            run.setStatus(MeasurementRunStatus.SKIPPED);
            run.setSkippedCount(1);
            run.setErrorMessage(message);
            run.setEndTime(new Date());
            run.setUpdateTime(new Date());
            runDao.updateById(run);
            return toResult(task.getId(), run, List.of());
        }

        List<MeasurementFileEntity> processedFiles = new ArrayList<>();
        try {
            List<MeasurementFileEntity> selectedFiles = selectFiles(task, request, phase);
            run.setSelectedFileCount(selectedFiles.size());
            if (selectedFiles.isEmpty()) {
                run.setStatus(MeasurementRunStatus.SKIPPED);
                run.setSkippedCount(1);
                run.setErrorMessage("No eligible measurement files to process");
                run.setEndTime(new Date());
                run.setUpdateTime(new Date());
                runDao.updateById(run);
                return toResult(task.getId(), run, processedFiles);
            }

            RuntimeContext runtime = buildRuntimeContext(task);
            for (MeasurementFileEntity file : selectedFiles) {
                ProcessFileResult fileResult = processFile(task, file, runtime, run, batchId, phase, request);
                processedFiles.add(fileResult.file);
                applyFileResult(run, fileResult);
                if (fileResult.stopRun) {
                    break;
                }
            }

            run.setStatus(run.getParseFailedCount() > 0 || run.getLoadFailedCount() > 0
                    ? MeasurementRunStatus.FAILED
                    : MeasurementRunStatus.SUCCESS);
            run.setEndTime(new Date());
            run.setUpdateTime(new Date());
            runDao.updateById(run);
            return toResult(task.getId(), run, processedFiles);
        } catch (Exception e) {
            String message = rootMessage(e);
            log.error("Measurement file {} failed, taskId={}", phase.getCode(), task.getId(), e);
            run.setStatus(MeasurementRunStatus.FAILED);
            run.setFailedCount(run.getFailedCount() == null ? 1 : run.getFailedCount() + 1);
            run.setErrorMessage(message);
            run.setEndTime(new Date());
            run.setUpdateTime(new Date());
            runDao.updateById(run);
            MeasurementParseLoadResultVO result = toResult(task.getId(), run, processedFiles);
            result.setErrorMessage(message);
            return result;
        } finally {
            lockService.releaseLock(task.getId(), lockKey, lock.getLockToken());
        }
    }

    private ProcessFileResult processFile(
            MeasurementFileSyncTaskEntity task,
            MeasurementFileEntity file,
            RuntimeContext runtime,
            MeasurementFileRunEntity run,
            String batchId,
            MeasurementRunPhase phase,
            MeasurementParseLoadRequestDTO request
    ) {
        ProcessFileResult result = new ProcessFileResult(file);
        String runId = run.getRunId();
        boolean parseNeeded = phase == MeasurementRunPhase.PARSE || phase == MeasurementRunPhase.PARSE_LOAD;
        boolean loadNeeded = phase == MeasurementRunPhase.LOAD || phase == MeasurementRunPhase.PARSE_LOAD;
        boolean forceReload = Boolean.TRUE.equals(request.getForceReload());

        if (forceReload && !Boolean.TRUE.equals(request.getForceReloadConfirmed())) {
            throw new ServiceException("forceReloadConfirmed is required. APPEND mode may create duplicate rows; "
                    + "confirm the target table is idempotent or cleanup file_id/batch_id rows first.");
        }
        if (file.getFileStatus() == MeasurementFileStatus.LOADED && !forceReload) {
            result.skipped = 1;
            return result;
        }

        try {
            if (parseNeeded && shouldParse(file, forceReload)) {
                parseFile(task, file, runtime, runId, batchId);
                result.parsedFileCount = 1;
                result.parsedRowCount = zeroLong(file.getParsedRowCount());
            }

            if (loadNeeded) {
                loadFile(task, file, runtime, run);
                result.loadedFileCount = 1;
                result.loadedRowCount = zeroLong(file.getLoadedRowCount());
            }
        } catch (ParseFailureException e) {
            result.parseFailedCount = 1;
            result.failedCount = 1;
            result.stopRun = Boolean.TRUE.equals(task.getParseFailFast());
        } catch (LoadFailureException e) {
            result.loadFailedCount = 1;
            result.failedCount = 1;
            result.stopRun = Boolean.TRUE.equals(task.getParseFailFast());
        } catch (Exception e) {
            markFileFailed(file, MeasurementFileStatus.FAILED, rootMessage(e));
            result.failedCount = 1;
            result.stopRun = Boolean.TRUE.equals(task.getParseFailFast());
        }
        return result;
    }

    private void parseFile(
            MeasurementFileSyncTaskEntity task,
            MeasurementFileEntity file,
            RuntimeContext runtime,
            String runId,
            String batchId
    ) {
        MeasurementFileParser parser = resolveParser(task.getParserType());
        file.setFileStatus(MeasurementFileStatus.PARSING);
        file.setRunId(runId);
        file.setBatchId(batchId);
        file.setErrorMessage(null);
        file.setUpdateTime(new Date());
        fileDao.updateById(file);

        Path stagingPath = buildStagingPath(task, file, runId);
        try {
            Files.createDirectories(stagingPath.getParent());
        } catch (IOException e) {
            markFileFailed(file, MeasurementFileStatus.PARSE_FAILED,
                    "Create staging directory failed: " + e.getMessage());
            throw new ParseFailureException(e);
        }

        ParsedMeasurementResult parsed;
        try (BufferedWriter writer = Files.newBufferedWriter(stagingPath, StandardCharsets.UTF_8)) {
            parsed = fileSourceClient.withInputStream(
                    runtime.source.getDbType(),
                    runtime.sourceConfig,
                    file.getFullPath(),
                    inputStream -> {
                        MeasurementFileContext context = buildContext(task, file, runId, batchId, inputStream);
                        context.getMetadata().put("maxRowsInMemory", 0);
                        return parser.parse(context, row -> writeJsonLine(writer, row));
                    }
            );
        } catch (UncheckedIOException e) {
            markFileFailed(file, MeasurementFileStatus.PARSE_FAILED,
                    "Write staging JSONL failed: " + e.getCause().getMessage());
            throw new ParseFailureException(e);
        } catch (Exception e) {
            markFileFailed(file, MeasurementFileStatus.PARSE_FAILED, rootMessage(e));
            throw new ParseFailureException(e);
        }

        if (parsed == null || !Boolean.TRUE.equals(parsed.getSuccess())) {
            String message = parsed == null ? "Parser returned null result" : parsed.getErrorMessage();
            file.setFileStatus(MeasurementFileStatus.PARSE_FAILED);
            file.setParseTime(new Date());
            file.setStagingFilePath(stagingPath.toString());
            file.setParsedRowCount(parsed == null ? 0L : zeroLong(parsed.getRowCount()));
            file.setParseErrorCount(parsed == null ? 0 : zeroInt(parsed.getErrorRowCount()));
            file.setParserConfigSnapshot(task.getParserConfigJson());
            file.setErrorMessage(StringUtils.defaultIfBlank(message, "Parse failed"));
            file.setUpdateTime(new Date());
            fileDao.updateById(file);
            throw new ParseFailureException(file.getErrorMessage());
        }

        file.setFileStatus(MeasurementFileStatus.LOAD_PENDING);
        file.setParseTime(new Date());
        file.setStagingFilePath(stagingPath.toString());
        file.setParsedRowCount(zeroLong(parsed.getRowCount()));
        file.setParseErrorCount(zeroInt(parsed.getErrorRowCount()));
        file.setParserConfigSnapshot(task.getParserConfigJson());
        file.setErrorMessage(null);
        file.setUpdateTime(new Date());
        fileDao.updateById(file);
    }

    private void loadFile(
            MeasurementFileSyncTaskEntity task,
            MeasurementFileEntity file,
            RuntimeContext runtime,
            MeasurementFileRunEntity run
    ) {
        if (StringUtils.isBlank(file.getStagingFilePath())) {
            markFileFailed(file, MeasurementFileStatus.LOAD_FAILED, "Missing staging file path");
            throw new LoadFailureException("Missing staging file path");
        }
        if (!Files.exists(Path.of(file.getStagingFilePath()))) {
            markFileFailed(file, MeasurementFileStatus.LOAD_FAILED,
                    "Staging file does not exist: " + file.getStagingFilePath());
            throw new LoadFailureException("Missing staging file");
        }

        file.setFileStatus(MeasurementFileStatus.LOADING);
        file.setRunId(run.getRunId());
        file.setErrorMessage(null);
        file.setUpdateTime(new Date());
        fileDao.updateById(file);

        String jobName = "measurement_file_" + task.getTaskCode() + "_" + file.getId() + "_" + shortId();
        String hocon = buildSeaTunnelHocon(task, file, runtime.target);
        appendGeneratedHocon(run, jobName, file.getId(), hocon);
        try {
            SyncSubmitJobResult submitResult = syncZetaClient.submitJob(runtime.clientId, jobName, hocon);
            file.setLoadJobId(submitResult.getJobId());
            file.setLoadJobName(submitResult.getJobName());
            fileDao.updateById(file);

            SyncJobStatusResult finalStatus = pollUntilFinished(runtime.clientId, submitResult.getJobId());
            if (!finalStatus.isSuccess()) {
                throw new ServiceException("SeaTunnel job failed: " + finalStatus.getStatus()
                        + (StringUtils.isBlank(finalStatus.getErrorMessage()) ? "" : ", " + finalStatus.getErrorMessage()));
            }
            long loadedRows = finalStatus.getSinkCount() == null
                    ? zeroLong(file.getParsedRowCount())
                    : finalStatus.getSinkCount();
            file.setFileStatus(MeasurementFileStatus.LOADED);
            file.setLoadTime(new Date());
            file.setLoadedRowCount(loadedRows);
            file.setErrorMessage(null);
            file.setUpdateTime(new Date());
            fileDao.updateById(file);
        } catch (Exception e) {
            markFileFailed(file, MeasurementFileStatus.LOAD_FAILED, rootMessage(e));
            throw new LoadFailureException(e);
        }
    }

    private SyncJobStatusResult pollUntilFinished(Long clientId, String jobId) throws InterruptedException {
        long started = System.currentTimeMillis();
        SyncJobStatusResult latest = null;
        while (System.currentTimeMillis() - started <= syncRunProperties.getPollTimeoutMs()) {
            latest = syncZetaClient.getJobStatus(clientId, jobId);
            if (latest.isEndState()) {
                return latest;
            }
            Thread.sleep(syncRunProperties.getPollIntervalMs());
        }
        throw new ServiceException("SeaTunnel job poll timeout, jobId=" + jobId
                + ", lastStatus=" + (latest == null ? null : latest.getStatus()));
    }

    private void writeJsonLine(BufferedWriter writer, Map<String, Object> row) {
        try {
            writer.write(JSONUtils.toJsonString(row));
            writer.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean shouldParse(MeasurementFileEntity file, boolean forceReload) {
        if (forceReload) {
            return true;
        }
        return file.getFileStatus() == MeasurementFileStatus.DISCOVERED
                || file.getFileStatus() == MeasurementFileStatus.PARSE_PENDING
                || file.getFileStatus() == MeasurementFileStatus.PARSE_FAILED
                || StringUtils.isBlank(file.getStagingFilePath());
    }

    private List<MeasurementFileEntity> selectFiles(
            MeasurementFileSyncTaskEntity task,
            MeasurementParseLoadRequestDTO request,
            MeasurementRunPhase phase
    ) {
        Set<MeasurementFileStatus> statuses = statusesForPhase(task, request, phase);
        int limit = request.getMaxFiles() == null || request.getMaxFiles() <= 0
                ? positive(task.getMaxFilesPerParseRun(), DEFAULT_MAX_FILES_PER_PARSE_RUN)
                : request.getMaxFiles();
        return fileDao.listByTaskAndStatuses(task.getId(), statuses, request.getFileIds(), limit);
    }

    private Set<MeasurementFileStatus> statusesForPhase(
            MeasurementFileSyncTaskEntity task,
            MeasurementParseLoadRequestDTO request,
            MeasurementRunPhase phase
    ) {
        EnumSet<MeasurementFileStatus> statuses = EnumSet.noneOf(MeasurementFileStatus.class);
        if (phase == MeasurementRunPhase.PARSE || phase == MeasurementRunPhase.PARSE_LOAD) {
            statuses.add(MeasurementFileStatus.DISCOVERED);
            statuses.add(MeasurementFileStatus.PARSE_PENDING);
            if (Boolean.TRUE.equals(request.getRetryParseFailed()) || Boolean.TRUE.equals(task.getRetryParseFailed())) {
                statuses.add(MeasurementFileStatus.PARSE_FAILED);
            }
            if (phase == MeasurementRunPhase.PARSE_LOAD
                    && (Boolean.TRUE.equals(request.getRetryLoadFailed()) || Boolean.TRUE.equals(task.getRetryLoadFailed()))) {
                statuses.add(MeasurementFileStatus.LOAD_FAILED);
            }
        }
        if (phase == MeasurementRunPhase.LOAD) {
            statuses.add(MeasurementFileStatus.PARSED);
            statuses.add(MeasurementFileStatus.LOAD_PENDING);
            if (Boolean.TRUE.equals(request.getRetryLoadFailed()) || Boolean.TRUE.equals(task.getRetryLoadFailed())) {
                statuses.add(MeasurementFileStatus.LOAD_FAILED);
            }
        }
        if (Boolean.TRUE.equals(request.getForceReload())) {
            statuses.add(MeasurementFileStatus.LOADED);
        }
        return statuses;
    }

    private RuntimeContext buildRuntimeContext(MeasurementFileSyncTaskEntity task) {
        RuntimeContext context = new RuntimeContext();
        context.source = loadSourceDatasource(task);
        context.sourceConfig = fileSourceClient.parseConfig(context.source.getDbType(), context.source.getConnectionParams());
        if (task.getTargetDatasourceId() != null) {
            context.target = buildStarRocksTarget(task);
            context.clientId = resolveClientId(task);
        }
        return context;
    }

    private DataSource loadSourceDatasource(MeasurementFileSyncTaskEntity task) {
        DataSource source = dataSourceDao.queryById(task.getSourceDatasourceId());
        if (source == null) {
            throw new ServiceException(Status.DATASOURCE_NOT_EXIST);
        }
        if (!fileSourceClient.supports(source.getDbType())) {
            throw new ServiceException("Measurement source datasource must be LOCAL_FILE/NAS/FTP/SFTP");
        }
        return source;
    }

    private StarRocksTarget buildStarRocksTarget(MeasurementFileSyncTaskEntity task) {
        DataSource target = dataSourceDao.queryById(task.getTargetDatasourceId());
        if (target == null) {
            throw new ServiceException(Status.DATASOURCE_NOT_EXIST);
        }
        if (target.getDbType() != DbType.STARROCKS) {
            throw new ServiceException("Measurement load target only supports StarRocks datasource");
        }
        String connectionParams = StringUtils.defaultString(target.getConnectionParams());
        StarRocksTarget result = new StarRocksTarget();
        result.database = StringUtils.defaultIfBlank(task.getTargetDatabase(),
                JSONUtils.getNodeString(connectionParams, "database"));
        result.table = task.getTargetTable();
        result.username = firstNonBlank(
                JSONUtils.getNodeString(connectionParams, "username"),
                JSONUtils.getNodeString(connectionParams, "user"));
        result.password = JSONUtils.getNodeString(connectionParams, "password");
        result.nodeUrls = resolveNodeUrls(task, connectionParams);
        result.baseUrl = resolveBaseUrl(task, connectionParams);
        if (StringUtils.isAnyBlank(result.database, result.table, result.username, result.nodeUrls, result.baseUrl)) {
            throw new ServiceException("StarRocks target database/table/user/nodeUrls/base-url are required");
        }
        return result;
    }

    private String resolveNodeUrls(MeasurementFileSyncTaskEntity task, String connectionParams) {
        if (StringUtils.isNotBlank(task.getStarrocksNodeUrls())) {
            return normalizeNodeUrls(task.getStarrocksNodeUrls());
        }
        String host = JSONUtils.getNodeString(connectionParams, "host");
        String httpPort = firstNonBlank(JSONUtils.getNodeString(connectionParams, "httpPort"), "8030");
        if (StringUtils.isBlank(host)) {
            return "";
        }
        return host.trim() + ":" + httpPort.trim();
    }

    private String resolveBaseUrl(MeasurementFileSyncTaskEntity task, String connectionParams) {
        if (StringUtils.isNotBlank(task.getStarrocksBaseUrl())) {
            return task.getStarrocksBaseUrl().trim();
        }
        String host = JSONUtils.getNodeString(connectionParams, "host");
        String queryPort = firstNonBlank(
                JSONUtils.getNodeString(connectionParams, "queryPort"),
                JSONUtils.getNodeString(connectionParams, "port"),
                "9030");
        if (StringUtils.isNotBlank(host)) {
            return "jdbc:mysql://" + host.trim() + ":" + queryPort.trim() + "/";
        }
        return firstNonBlank(JSONUtils.getNodeString(connectionParams, "url"),
                JSONUtils.getNodeString(connectionParams, "jdbcUrl"));
    }

    private Long resolveClientId(MeasurementFileSyncTaskEntity task) {
        if (task.getSeatunnelClientId() != null && task.getSeatunnelClientId() > 0) {
            return task.getSeatunnelClientId();
        }
        List<SeaTunnelClient> clients = seaTunnelClientDao.selectList(
                new LambdaQueryWrapper<SeaTunnelClient>()
                        .eq(SeaTunnelClient::getHealthStatus, SeaTunnelClientHealthStatusEnum.LIVE.getCode())
                        .orderByDesc(SeaTunnelClient::getCreateTime)
        );
        if (CollectionUtils.isEmpty(clients)) {
            clients = seaTunnelClientDao.selectList(
                    new LambdaQueryWrapper<SeaTunnelClient>().orderByDesc(SeaTunnelClient::getCreateTime)
            );
        }
        if (CollectionUtils.isEmpty(clients)) {
            throw new ServiceException("No SeaTunnel client configured for measurement load");
        }
        return clients.get(0).getId();
    }

    private String buildSeaTunnelHocon(
            MeasurementFileSyncTaskEntity task,
            MeasurementFileEntity file,
            StarRocksTarget target
    ) {
        return "env {\n"
                + "  job.mode = \"BATCH\"\n"
                + "  parallelism = 1\n"
                + "}\n\n"
                + "source {\n"
                + "  LocalFile {\n"
                + "    plugin_output = \"src\"\n"
                + "    path = \"" + escape(file.getStagingFilePath()) + "\"\n"
                + "    file_format_type = \"json\"\n"
                + "    schema = {\n"
                + "      fields {\n"
                + "        file_id = \"bigint\"\n"
                + "        task_id = \"bigint\"\n"
                + "        batch_id = \"string\"\n"
                + "        run_id = \"string\"\n"
                + "        source_file_name = \"string\"\n"
                + "        source_relative_path = \"string\"\n"
                + "        parser_type = \"string\"\n"
                + "        row_no = \"bigint\"\n"
                + "        lot_id = \"string\"\n"
                + "        wafer_id = \"string\"\n"
                + "        item_name = \"string\"\n"
                + "        item_value = \"double\"\n"
                + "        item_unit = \"string\"\n"
                + "        raw_line = \"string\"\n"
                + "        parse_time = \"timestamp\"\n"
                + "        ingest_time = \"timestamp\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n\n"
                + "sink {\n"
                + "  StarRocks {\n"
                + "    plugin_input = \"src\"\n"
                + "    nodeUrls = [" + quoteList(target.nodeUrls) + "]\n"
                + "    base-url = \"" + escape(target.baseUrl) + "\"\n"
                + "    username = \"" + escape(target.username) + "\"\n"
                + "    password = \"" + escape(target.password) + "\"\n"
                + "    database = \"" + escape(target.database) + "\"\n"
                + "    table = \"" + escape(target.table) + "\"\n"
                + "    batch_max_rows = 5000\n"
                + "    starrocks.config = {\n"
                + "      format = \"JSON\"\n"
                + "      strip_outer_array = true\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    private void checkSourceDatasource(MeasurementFileSyncTaskEntity task, MeasurementPreflightVO result) {
        try {
            DataSource source = loadSourceDatasource(task);
            FileDataSourceConfig config = fileSourceClient.parseConfig(source.getDbType(), source.getConnectionParams());
            config.setRootPath(StringUtils.defaultIfBlank(task.getSourceRootPath(), config.getRootPath()));
            FileSourceTestResult testResult = fileSourceClient.test(source.getDbType(), config);
            addPass(result,
                    "source_datasource",
                    "Source datasource is accessible: " + testResult.getRootPath()
                            + sampleSuffix(testResult.getSampleFiles()));
        } catch (Exception e) {
            addError(result, "source_datasource", rootMessage(e), null);
        }
    }

    private void checkStagingDir(
            MeasurementFileSyncTaskEntity task,
            MeasurementPreflightRequestDTO request,
            MeasurementPreflightVO result
    ) {
        Path stagingPath = Path.of(resolveStagingDir(task));
        try {
            if (!Files.exists(stagingPath)) {
                if (Boolean.TRUE.equals(request.getAllowCreateStagingDir())) {
                    Files.createDirectories(stagingPath);
                    addPass(result, "staging_dir", "Created staging_dir: " + stagingPath);
                } else {
                    addError(result,
                            "staging_dir",
                            "staging_dir does not exist: " + stagingPath,
                            null);
                    return;
                }
            }
            if (!Files.isDirectory(stagingPath)) {
                addError(result, "staging_dir", "staging_dir is not a directory: " + stagingPath, null);
                return;
            }
            if (!Files.isWritable(stagingPath)) {
                addError(result, "staging_dir", "staging_dir is not writable: " + stagingPath, null);
                return;
            }
            addPass(result, "staging_dir", "staging_dir is writable: " + stagingPath);
        } catch (Exception e) {
            addError(result, "staging_dir", "Check staging_dir failed: " + rootMessage(e), null);
        }
    }

    private StarRocksTarget checkStarRocksTarget(MeasurementFileSyncTaskEntity task, MeasurementPreflightVO result) {
        try {
            if (task.getTargetDatasourceId() == null || task.getTargetDatasourceId() <= 0) {
                addError(result,
                        "target_datasource",
                        "target_datasource_id is required for parse and load",
                        recommendedStarRocksDdl(taskTargetDatabase(task), taskTargetTable(task)));
                return null;
            }
            StarRocksTarget target = buildStarRocksTarget(task);
            addPass(result, "target_datasource", "StarRocks datasource config is complete");
            if (targetTableExists(target)) {
                addPass(result,
                        "target_table",
                        "Target table exists: " + target.database + "." + target.table);
            } else {
                addError(result,
                        "target_table",
                        "Target table does not exist: " + target.database + "." + target.table,
                        recommendedStarRocksDdl(target.database, target.table));
            }
            return target;
        } catch (Exception e) {
            addError(result,
                    "target_datasource",
                    rootMessage(e),
                    recommendedStarRocksDdl(taskTargetDatabase(task), taskTargetTable(task)));
            return null;
        }
    }

    private void checkSeaTunnelEngine(MeasurementFileSyncTaskEntity task, MeasurementPreflightVO result) {
        try {
            Long clientId = resolveClientId(task);
            seaTunnelRestClient.systemMonitoringInformation(clientId);
            addPass(result, "seatunnel_engine", "SeaTunnel Engine is reachable, clientId=" + clientId);
        } catch (Exception e) {
            addError(result, "seatunnel_engine", "SeaTunnel Engine check failed: " + rootMessage(e), null);
        }
    }

    private void checkStarRocksPorts(StarRocksTarget target, MeasurementPreflightVO result) {
        boolean nodePortLooksRight = List.of(target.nodeUrls.split("[,\\n]")).stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .allMatch(item -> item.endsWith(":8030"));
        if (!nodePortLooksRight) {
            addWarning(result,
                    "starrocks_node_urls",
                    "nodeUrls should normally use StarRocks FE HTTP port 8030: " + target.nodeUrls,
                    null);
        } else {
            addPass(result, "starrocks_node_urls", "nodeUrls use FE HTTP port 8030");
        }
        if (!target.baseUrl.contains(":9030")) {
            addWarning(result,
                    "starrocks_base_url",
                    "base-url should normally use StarRocks MySQL protocol port 9030: " + target.baseUrl,
                    null);
        } else {
            addPass(result, "starrocks_base_url", "base-url uses MySQL protocol port 9030");
        }
    }

    private boolean targetTableExists(StarRocksTarget target) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // DriverManager can still resolve the driver through service loading in some deployments.
        }
        try (Connection connection = DriverManager.getConnection(target.baseUrl, target.username, target.password);
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from information_schema.tables where table_schema = ? and table_name = ?")) {
            statement.setString(1, target.database);
            statement.setString(2, target.table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        } catch (Exception e) {
            throw new ServiceException("Check StarRocks target table failed: " + rootMessage(e), e);
        }
    }

    private MeasurementFileRunEntity manualRepairRun(MeasurementFileEntity file, String action) {
        MeasurementFileSyncTaskEntity task = loadTask(file.getTaskId());
        return manualRepairRun(task, action);
    }

    private MeasurementFileRunEntity manualRepairRun(MeasurementFileSyncTaskEntity task, String action) {
        Date now = new Date();
        MeasurementFileRunEntity run = MeasurementFileRunEntity.builder()
                .runId(generateRuntimeId("mfs_" + action + "_run", task.getTaskCode()))
                .batchId(generateRuntimeId("mfs_" + action + "_batch", task.getTaskCode()))
                .taskId(task.getId())
                .triggerType(SyncTriggerType.MANUAL)
                .status(MeasurementRunStatus.SUCCESS)
                .runPhase(MeasurementRunPhase.PARSE_LOAD)
                .sourceDatasourceId(task.getSourceDatasourceId())
                .scannedCount(0)
                .discoveredCount(0)
                .skippedCount(0)
                .failedCount(0)
                .selectedFileCount(1)
                .parsedFileCount(0)
                .loadedFileCount(0)
                .parseFailedCount(0)
                .loadFailedCount(0)
                .parsedRowCount(0L)
                .loadedRowCount(0L)
                .stagingDir(resolveStagingDir(task))
                .targetDatasourceId(task.getTargetDatasourceId())
                .targetDatabase(task.getTargetDatabase())
                .targetTable(task.getTargetTable())
                .startTime(now)
                .endTime(now)
                .createTime(now)
                .updateTime(now)
                .build();
        runDao.insert(run);
        return run;
    }

    private String recommendedStarRocksDdl(String database, String table) {
        String safeDatabase = escapeSqlIdentifier(StringUtils.defaultIfBlank(database, "st_test"));
        String safeTable = escapeSqlIdentifier(StringUtils.defaultIfBlank(table, "measurement_item_result"));
        return "CREATE TABLE IF NOT EXISTS `" + safeDatabase + "`.`" + safeTable + "` (\n"
                + "  file_id BIGINT NOT NULL,\n"
                + "  task_id BIGINT NOT NULL,\n"
                + "  batch_id VARCHAR(100),\n"
                + "  run_id VARCHAR(100),\n"
                + "  source_file_name VARCHAR(500),\n"
                + "  source_relative_path VARCHAR(1000),\n"
                + "  parser_type VARCHAR(30),\n"
                + "  row_no BIGINT,\n"
                + "  lot_id VARCHAR(100),\n"
                + "  wafer_id VARCHAR(100),\n"
                + "  item_name VARCHAR(200),\n"
                + "  item_value DOUBLE,\n"
                + "  item_unit VARCHAR(50),\n"
                + "  raw_line VARCHAR(65533),\n"
                + "  parse_time DATETIME,\n"
                + "  ingest_time DATETIME\n"
                + ")\n"
                + "ENGINE=OLAP\n"
                + "DUPLICATE KEY(file_id, row_no)\n"
                + "DISTRIBUTED BY HASH(file_id) BUCKETS 8\n"
                + "PROPERTIES (\n"
                + "  \"replication_num\" = \"1\"\n"
                + ");";
    }

    private String taskTargetDatabase(MeasurementFileSyncTaskEntity task) {
        String database = task.getTargetDatabase();
        if (StringUtils.isBlank(database) && task.getTargetDatasourceId() != null) {
            DataSource target = dataSourceDao.queryById(task.getTargetDatasourceId());
            if (target != null && StringUtils.isNotBlank(target.getConnectionParams())) {
                database = JSONUtils.getNodeString(target.getConnectionParams(), "database");
            }
        }
        return StringUtils.defaultIfBlank(database, "st_test");
    }

    private String taskTargetTable(MeasurementFileSyncTaskEntity task) {
        return StringUtils.defaultIfBlank(task.getTargetTable(), "measurement_item_result");
    }

    private String sampleSuffix(List<String> sampleFiles) {
        if (sampleFiles == null || sampleFiles.isEmpty()) {
            return "";
        }
        return ", sampleFiles=" + sampleFiles.stream().limit(5).collect(Collectors.joining(", "));
    }

    private void addPass(MeasurementPreflightVO result, String name, String message) {
        addCheck(result, name, "PASS", message, null);
    }

    private void addWarning(MeasurementPreflightVO result, String name, String message, String suggestedDdl) {
        addCheck(result, name, "WARN", message, suggestedDdl);
    }

    private void addError(MeasurementPreflightVO result, String name, String message, String suggestedDdl) {
        addCheck(result, name, "ERROR", message, suggestedDdl);
    }

    private void addCheck(
            MeasurementPreflightVO result,
            String name,
            String status,
            String message,
            String suggestedDdl
    ) {
        MeasurementCheckItemVO item = new MeasurementCheckItemVO();
        item.setName(name);
        item.setStatus(status);
        item.setMessage(message);
        item.setSuggestedDdl(suggestedDdl);
        result.getChecks().add(item);
        if ("ERROR".equals(status)) {
            result.getErrors().add(message);
        } else if ("WARN".equals(status)) {
            result.getWarnings().add(message);
        }
    }

    private MeasurementFileContext buildContext(
            MeasurementFileSyncTaskEntity task,
            MeasurementFileEntity file,
            String runId,
            String batchId,
            java.io.InputStream inputStream
    ) {
        return MeasurementFileContext.builder()
                .fileId(file.getId())
                .taskId(task.getId())
                .parserType(task.getParserType())
                .runId(runId)
                .batchId(batchId)
                .sourceType(file.getSourceType() == null ? null : file.getSourceType().name())
                .rootPath(file.getRootPath())
                .relativePath(file.getRelativePath())
                .fullPath(file.getFullPath())
                .fileName(file.getFileName())
                .fileSize(file.getFileSize())
                .lastModifiedTime(file.getLastModifiedTime())
                .parserConfigJson(task.getParserConfigJson())
                .charset(StringUtils.defaultIfBlank(task.getParseCharset(), "UTF-8"))
                .parseMaxErrorRows(task.getParseMaxErrorRows())
                .parseFailFast(task.getParseFailFast())
                .inputStream(inputStream)
                .metadata(new HashMap<>())
                .build();
    }

    private Path buildStagingPath(MeasurementFileSyncTaskEntity task, MeasurementFileEntity file, String runId) {
        String safeName = StringUtils.defaultIfBlank(file.getFileName(), "file_" + file.getId())
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return Path.of(resolveStagingDir(task), "task_" + task.getId(), runId,
                "file_" + file.getId() + "_" + safeName + ".jsonl");
    }

    private void validatePhaseConfig(MeasurementFileSyncTaskEntity task, MeasurementRunPhase phase) {
        if (!Boolean.TRUE.equals(task.getEnabled())) {
            throw new ServiceException("Measurement file sync task is disabled");
        }
        if (task.getParserType() == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "parserType");
        }
        if (phase == MeasurementRunPhase.LOAD || phase == MeasurementRunPhase.PARSE_LOAD) {
            if (task.getLoadBatchMode() != null
                    && task.getLoadBatchMode() != MeasurementLoadBatchMode.ONE_FILE_ONE_JOB) {
                throw new ServiceException("Only ONE_FILE_ONE_JOB is supported in this release");
            }
            if (task.getLoadMode() != null && task.getLoadMode() != MeasurementLoadMode.APPEND) {
                throw new ServiceException("Only APPEND load mode is implemented in this release");
            }
            if (task.getTargetDatasourceId() == null || task.getTargetDatasourceId() <= 0) {
                throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "targetDatasourceId");
            }
            if (StringUtils.isBlank(task.getTargetTable())) {
                throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "targetTable");
            }
            StarRocksTarget target = buildStarRocksTarget(task);
            if (!targetTableExists(target)) {
                throw new ServiceException("StarRocks target table does not exist: "
                        + target.database + "." + target.table
                        + ". Please create it first. Recommended DDL:\n"
                        + recommendedStarRocksDdl(target.database, target.table));
            }
        }
    }

    private MeasurementFileParser resolveParser(MeasurementParserType parserType) {
        if (parserType == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "parserType");
        }
        MeasurementFileParser parser = parserMap.get(parserType.name().toUpperCase(Locale.ROOT));
        if (parser == null) {
            throw new ServiceException("Measurement parser not implemented yet: " + parserType);
        }
        return parser;
    }

    private MeasurementFileSyncTaskEntity loadTask(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskId");
        }
        MeasurementFileSyncTaskEntity task = taskDao.queryById(taskId);
        if (task == null) {
            throw new ServiceException("Measurement file sync task not found, id=" + taskId);
        }
        return task;
    }

    private MeasurementFileEntity loadFile(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "fileId");
        }
        MeasurementFileEntity file = fileDao.queryById(fileId);
        if (file == null) {
            throw new ServiceException("Measurement file not found, id=" + fileId);
        }
        return file;
    }

    private MeasurementParseLoadRequestDTO safeRequest(MeasurementParseLoadRequestDTO request) {
        MeasurementParseLoadRequestDTO safe = request == null ? new MeasurementParseLoadRequestDTO() : request;
        if (safe.getWaitForLoadFinish() == null) {
            safe.setWaitForLoadFinish(true);
        }
        if (safe.getTriggerType() == null) {
            safe.setTriggerType(SyncTriggerType.MANUAL);
        }
        return safe;
    }

    private void applyFileResult(MeasurementFileRunEntity run, ProcessFileResult result) {
        run.setSkippedCount(zeroInt(run.getSkippedCount()) + result.skipped);
        run.setFailedCount(zeroInt(run.getFailedCount()) + result.failedCount);
        run.setParsedFileCount(zeroInt(run.getParsedFileCount()) + result.parsedFileCount);
        run.setLoadedFileCount(zeroInt(run.getLoadedFileCount()) + result.loadedFileCount);
        run.setParseFailedCount(zeroInt(run.getParseFailedCount()) + result.parseFailedCount);
        run.setLoadFailedCount(zeroInt(run.getLoadFailedCount()) + result.loadFailedCount);
        run.setParsedRowCount(zeroLong(run.getParsedRowCount()) + result.parsedRowCount);
        run.setLoadedRowCount(zeroLong(run.getLoadedRowCount()) + result.loadedRowCount);
        if (StringUtils.isNotBlank(result.file.getErrorMessage())) {
            run.setErrorMessage(result.file.getErrorMessage());
        }
        run.setUpdateTime(new Date());
        runDao.updateById(run);
    }

    private void appendGeneratedHocon(
            MeasurementFileRunEntity run,
            String jobName,
            Long fileId,
            String hocon
    ) {
        String maskedHocon = HoconSensitiveMaskUtil.maskSensitiveInfo(hocon);
        String block = "# jobName=" + jobName + ", fileId=" + fileId + "\n" + maskedHocon + "\n";
        run.setGeneratedHocon(StringUtils.isBlank(run.getGeneratedHocon())
                ? block
                : run.getGeneratedHocon() + "\n" + block);
        run.setUpdateTime(new Date());
        runDao.updateById(run);
    }

    private MeasurementParseLoadResultVO toResult(
            Long taskId,
            MeasurementFileRunEntity run,
            List<MeasurementFileEntity> files
    ) {
        MeasurementParseLoadResultVO vo = new MeasurementParseLoadResultVO();
        vo.setTaskId(taskId);
        vo.setRunId(run.getRunId());
        vo.setBatchId(run.getBatchId());
        vo.setRunPhase(run.getRunPhase() == null ? null : run.getRunPhase().name());
        vo.setStatus(run.getStatus());
        vo.setSelectedFileCount(zeroInt(run.getSelectedFileCount()));
        vo.setParsedFileCount(zeroInt(run.getParsedFileCount()));
        vo.setLoadedFileCount(zeroInt(run.getLoadedFileCount()));
        vo.setParseFailedCount(zeroInt(run.getParseFailedCount()));
        vo.setLoadFailedCount(zeroInt(run.getLoadFailedCount()));
        vo.setSkippedCount(zeroInt(run.getSkippedCount()));
        vo.setParsedRowCount(zeroLong(run.getParsedRowCount()));
        vo.setLoadedRowCount(zeroLong(run.getLoadedRowCount()));
        vo.setErrorMessage(run.getErrorMessage());
        vo.setGeneratedHocon(run.getGeneratedHocon());
        vo.setFiles(files.stream().map(this::toFileVO).collect(Collectors.toList()));
        return vo;
    }

    private MeasurementFileVO toFileVO(MeasurementFileEntity entity) {
        MeasurementFileVO vo = new MeasurementFileVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setBatchId(entity.getBatchId());
        vo.setRunId(entity.getRunId());
        vo.setSourceDatasourceId(entity.getSourceDatasourceId());
        vo.setSourceType(entity.getSourceType() == null ? null : entity.getSourceType().name());
        vo.setParserType(entity.getParserType());
        vo.setRootPath(entity.getRootPath());
        vo.setRelativePath(entity.getRelativePath());
        vo.setFileName(entity.getFileName());
        vo.setFullPath(entity.getFullPath());
        vo.setFileSize(entity.getFileSize());
        vo.setLastModifiedTime(entity.getLastModifiedTime());
        vo.setChecksum(entity.getChecksum());
        vo.setChecksumType(entity.getChecksumType());
        vo.setFileStatus(entity.getFileStatus());
        vo.setDiscoverTime(entity.getDiscoverTime());
        vo.setParseTime(entity.getParseTime());
        vo.setLoadTime(entity.getLoadTime());
        vo.setStagingFilePath(entity.getStagingFilePath());
        vo.setParsedRowCount(entity.getParsedRowCount());
        vo.setLoadedRowCount(entity.getLoadedRowCount());
        vo.setParseErrorCount(entity.getParseErrorCount());
        vo.setParserConfigSnapshot(entity.getParserConfigSnapshot());
        vo.setLoadJobId(entity.getLoadJobId());
        vo.setLoadJobName(entity.getLoadJobName());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private void markFileFailed(MeasurementFileEntity file, MeasurementFileStatus status, String message) {
        file.setFileStatus(status);
        if (status == MeasurementFileStatus.PARSE_FAILED) {
            file.setParseTime(new Date());
        }
        if (status == MeasurementFileStatus.LOAD_FAILED) {
            file.setLoadTime(new Date());
        }
        file.setErrorMessage(message);
        file.setUpdateTime(new Date());
        fileDao.updateById(file);
    }

    private String lockKey(MeasurementRunPhase phase) {
        if (phase == MeasurementRunPhase.PARSE) {
            return LOCK_KEY_PARSE;
        }
        if (phase == MeasurementRunPhase.LOAD) {
            return LOCK_KEY_LOAD;
        }
        return LOCK_KEY_PARSE_LOAD;
    }

    private String resolveStagingDir(MeasurementFileSyncTaskEntity task) {
        return StringUtils.defaultIfBlank(task.getStagingDir(), DEFAULT_STAGING_DIR);
    }

    private String generateRuntimeId(String prefix, String taskCode) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + "_" + sanitize(taskCode) + "_" + time + "_" + shortId();
    }

    private String sanitize(String value) {
        return StringUtils.defaultIfBlank(value, "task").replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String normalizeNodeUrls(String nodeUrls) {
        return List.of(nodeUrls.split("[,\\n]")).stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(","));
    }

    private String quoteList(String csv) {
        if (StringUtils.isBlank(csv)) {
            return "";
        }
        return List.of(csv.split("[,\\n]")).stream()
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(item -> "\"" + escape(item) + "\"")
                .collect(Collectors.joining(", "));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeSqlIdentifier(String value) {
        return StringUtils.defaultIfBlank(value, "").replace("`", "``");
    }

    private String escapeSqlLiteral(String value) {
        return StringUtils.defaultIfBlank(value, "").replace("'", "''");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private int positive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int zeroInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long zeroLong(Long value) {
        return value == null ? 0L : value;
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.toString() : cursor.getMessage();
    }

    private static final class RuntimeContext {
        private DataSource source;
        private FileDataSourceConfig sourceConfig;
        private StarRocksTarget target;
        private Long clientId;
    }

    private static final class StarRocksTarget {
        private String nodeUrls;
        private String baseUrl;
        private String username;
        private String password;
        private String database;
        private String table;
    }

    private static final class ProcessFileResult {
        private final MeasurementFileEntity file;
        private int parsedFileCount;
        private int loadedFileCount;
        private int parseFailedCount;
        private int loadFailedCount;
        private int failedCount;
        private int skipped;
        private long parsedRowCount;
        private long loadedRowCount;
        private boolean stopRun;

        private ProcessFileResult(MeasurementFileEntity file) {
            this.file = file;
        }
    }

    private static final class ParseFailureException extends RuntimeException {
        private ParseFailureException(Object message) {
            super(String.valueOf(message));
        }
    }

    private static final class LoadFailureException extends RuntimeException {
        private LoadFailureException(Object message) {
            super(String.valueOf(message));
        }
    }
}
