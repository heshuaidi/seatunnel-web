package org.apache.seatunnel.web.api.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.api.service.IncrementalTaskLockService;
import org.apache.seatunnel.web.api.service.MeasurementFileDiscoveryService;
import org.apache.seatunnel.web.api.service.file.FileSourceClient;
import org.apache.seatunnel.web.api.service.model.DiscoveredFile;
import org.apache.seatunnel.web.api.service.model.FileDataSourceConfig;
import org.apache.seatunnel.web.api.service.model.FileSourceScanRequest;
import org.apache.seatunnel.web.api.service.model.IncrementalLockResult;
import org.apache.seatunnel.web.common.enums.MeasurementDedupStrategy;
import org.apache.seatunnel.web.common.enums.MeasurementDiscoveryMode;
import org.apache.seatunnel.web.common.enums.MeasurementFileStatus;
import org.apache.seatunnel.web.common.enums.MeasurementRunStatus;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.DataSource;
import org.apache.seatunnel.web.dao.entity.MeasurementFileEntity;
import org.apache.seatunnel.web.dao.entity.MeasurementFileRunEntity;
import org.apache.seatunnel.web.dao.entity.MeasurementFileSyncTaskEntity;
import org.apache.seatunnel.web.dao.repository.DataSourceDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileRunDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileSyncTaskDao;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileQueryDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileRunQueryDTO;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileRunVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileScanResultVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileVO;
import org.apache.seatunnel.web.spi.enums.DbType;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MeasurementFileDiscoveryServiceImpl implements MeasurementFileDiscoveryService {

    private static final DateTimeFormatter WATERMARK_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_MAX_FILES = 1000;
    private static final String CHECKSUM_TYPE_SHA256 = "SHA-256";

    @Resource
    private MeasurementFileSyncTaskDao taskDao;

    @Resource
    private MeasurementFileDao fileDao;

    @Resource
    private MeasurementFileRunDao runDao;

    @Resource
    private DataSourceDao dataSourceDao;

    @Resource
    private FileSourceClient fileSourceClient;

    @Resource
    private IncrementalTaskLockService incrementalTaskLockService;

    @Override
    public MeasurementFileScanResultVO testScan(Long taskId) {
        MeasurementFileSyncTaskEntity task = loadTask(taskId);
        ScanContext context = buildScanContext(task);
        ScanStats stats = executeScan(task, context, null, null, true);
        MeasurementFileScanResultVO result = toScanResult(task.getId(), null, null, MeasurementRunStatus.SUCCESS, stats);
        result.setFiles(stats.files);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeasurementFileScanResultVO discover(Long taskId, SyncTriggerType triggerType) {
        MeasurementFileSyncTaskEntity task = loadTask(taskId);
        SyncTriggerType safeTriggerType = triggerType == null ? SyncTriggerType.MANUAL : triggerType;
        String runId = generateRuntimeId("mfs_run", task.getTaskCode());
        String batchId = generateRuntimeId("mfs_batch", task.getTaskCode());
        Date now = new Date();
        MeasurementFileRunEntity run = MeasurementFileRunEntity.builder()
                .runId(runId)
                .batchId(batchId)
                .taskId(task.getId())
                .triggerType(safeTriggerType)
                .status(MeasurementRunStatus.RUNNING)
                .sourceDatasourceId(task.getSourceDatasourceId())
                .scannedCount(0)
                .discoveredCount(0)
                .skippedCount(0)
                .failedCount(0)
                .startTime(now)
                .createTime(now)
                .updateTime(now)
                .build();
        runDao.insert(run);

        if (!Boolean.TRUE.equals(task.getEnabled())) {
            run.setStatus(MeasurementRunStatus.SKIPPED);
            run.setErrorMessage("Measurement file sync task is disabled");
            run.setEndTime(new Date());
            run.setUpdateTime(new Date());
            runDao.updateById(run);
            return toScanResult(task.getId(), runId, batchId, MeasurementRunStatus.SKIPPED, emptyStats(run.getErrorMessage()));
        }

        IncrementalLockResult lock = incrementalTaskLockService.acquireLock(
                task.getId(),
                task.getWatermarkKey(),
                runId,
                batchId,
                task.getLockTtlMinutes() == null ? null : task.getLockTtlMinutes().longValue()
        );
        if (!lock.isAcquired()) {
            String message = "Another measurement file discovery run is active";
            run.setStatus(MeasurementRunStatus.SKIPPED);
            run.setSkippedCount(1);
            run.setErrorMessage(message);
            run.setEndTime(new Date());
            run.setUpdateTime(new Date());
            runDao.updateById(run);
            ScanStats stats = emptyStats(message);
            stats.skippedCount = 1;
            return toScanResult(task.getId(), runId, batchId, MeasurementRunStatus.SKIPPED, stats);
        }

        try {
            ScanContext context = buildScanContext(task);
            ScanStats stats = executeScan(task, context, runId, batchId, false);
            run.setStatus(stats.failedCount > 0 ? MeasurementRunStatus.FAILED : MeasurementRunStatus.SUCCESS);
            run.setScannedCount(stats.scannedCount);
            run.setDiscoveredCount(stats.discoveredCount);
            run.setSkippedCount(stats.skippedCount);
            run.setFailedCount(stats.failedCount);
            run.setErrorMessage(stats.errorMessage);
            run.setEndTime(new Date());
            run.setUpdateTime(new Date());
            runDao.updateById(run);
            updateWatermarkIfNeeded(task, stats);
            return toScanResult(task.getId(), runId, batchId, run.getStatus(), stats);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            log.error("Measurement file discovery failed, taskId={}", task.getId(), e);
            run.setStatus(MeasurementRunStatus.FAILED);
            run.setFailedCount(1);
            run.setErrorMessage(message);
            run.setEndTime(new Date());
            run.setUpdateTime(new Date());
            runDao.updateById(run);
            ScanStats stats = emptyStats(message);
            stats.failedCount = 1;
            return toScanResult(task.getId(), runId, batchId, MeasurementRunStatus.FAILED, stats);
        } finally {
            incrementalTaskLockService.releaseLock(task.getId(), task.getWatermarkKey(), lock.getLockToken());
        }
    }

    @Override
    public PaginationResult<MeasurementFileRunVO> runPage(MeasurementFileRunQueryDTO dto) {
        MeasurementFileRunQueryDTO safeDto = dto == null ? new MeasurementFileRunQueryDTO() : dto;
        IPage<MeasurementFileRunEntity> page = runDao.queryPage(safeDto);
        List<MeasurementFileRunVO> records = page.getRecords().stream()
                .map(this::toRunVO)
                .collect(Collectors.toList());
        return PaginationResult.buildSuc(records, page);
    }

    @Override
    public PaginationResult<MeasurementFileVO> filePage(MeasurementFileQueryDTO dto) {
        MeasurementFileQueryDTO safeDto = dto == null ? new MeasurementFileQueryDTO() : dto;
        IPage<MeasurementFileEntity> page = fileDao.queryPage(safeDto);
        List<MeasurementFileVO> records = page.getRecords().stream()
                .map(this::toFileVO)
                .collect(Collectors.toList());
        return PaginationResult.buildSuc(records, page);
    }

    private ScanStats executeScan(
            MeasurementFileSyncTaskEntity task,
            ScanContext context,
            String runId,
            String batchId,
            boolean dryRun
    ) {
        List<DiscoveredFile> scannedFiles = fileSourceClient.scan(FileSourceScanRequest.builder()
                .sourceType(context.sourceType)
                .config(context.config)
                .rootPath(context.rootPath)
                .includePatterns(task.getIncludePatterns())
                .excludePatterns(task.getExcludePatterns())
                .recursive(task.getRecursive())
                .maxDepth(task.getMaxDepth())
                .maxFiles(task.getMaxFilesPerRun() == null ? DEFAULT_MAX_FILES : task.getMaxFilesPerRun())
                .build());

        ScanStats stats = new ScanStats();
        stats.scannedCount = scannedFiles.size();
        for (DiscoveredFile file : scannedFiles) {
            try {
                handleScannedFile(task, context, file, runId, batchId, dryRun, stats);
            } catch (Exception e) {
                stats.failedCount++;
                stats.errorMessage = appendMessage(stats.errorMessage,
                        file.getRelativePath() + ": " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                if (!dryRun) {
                    upsertFailedFile(task, context, file, runId, batchId, e);
                }
            }
        }
        return stats;
    }

    private void handleScannedFile(
            MeasurementFileSyncTaskEntity task,
            ScanContext context,
            DiscoveredFile file,
            String runId,
            String batchId,
            boolean dryRun,
            ScanStats stats
    ) {
        Date lastModifiedTime = toDate(file.getLastModifiedTime());
        if (task.getMinLastModifiedTime() != null
                && lastModifiedTime != null
                && lastModifiedTime.before(task.getMinLastModifiedTime())) {
            stats.skippedCount++;
            addDryRunFile(task, context, file, MeasurementFileStatus.SKIPPED,
                    "File lastModifiedTime is earlier than minLastModifiedTime", dryRun, stats);
            return;
        }

        String watermarkSkipReason = watermarkSkipReason(task, file, lastModifiedTime);
        if (watermarkSkipReason != null) {
            stats.skippedCount++;
            addDryRunFile(task, context, file, MeasurementFileStatus.SKIPPED, watermarkSkipReason, dryRun, stats);
            return;
        }

        String unstableReason = unstableReason(task, lastModifiedTime);
        if (unstableReason != null) {
            stats.skippedCount++;
            if (dryRun) {
                addDryRunFile(task, context, file, MeasurementFileStatus.SKIPPED, unstableReason, true, stats);
            } else {
                upsertSkippedFile(task, context, file, runId, batchId, unstableReason);
            }
            return;
        }

        String checksum = checksumIfNeeded(task, context.sourceType, file.getAbsolutePath());
        MeasurementFileEntity existing = findExisting(task, file, checksum, lastModifiedTime);
        if (existing != null) {
            if (existing.getFileStatus() == MeasurementFileStatus.SKIPPED) {
                if (dryRun) {
                    addDryRunFile(task, context, file, MeasurementFileStatus.PARSE_PENDING,
                            "Skipped file is now eligible", true, stats);
                } else {
                    updateExistingToParsePending(existing, task, context, file, runId, batchId, checksum);
                    stats.files.add(toFileVO(existing));
                }
                stats.discoveredCount++;
                updatePendingWatermark(task, file, lastModifiedTime, stats);
                return;
            }
            stats.skippedCount++;
            addDryRunFile(task, context, file, MeasurementFileStatus.SKIPPED,
                    "File identity already exists", dryRun, stats);
            updatePendingWatermark(task, file, lastModifiedTime, stats);
            return;
        }

        MeasurementFileEntity entity = buildFileEntity(
                task,
                context,
                file,
                runId,
                batchId,
                MeasurementFileStatus.PARSE_PENDING,
                null,
                checksum
        );
        if (dryRun) {
            stats.files.add(toFileVO(entity));
        } else {
            try {
                fileDao.insert(entity);
            } catch (DuplicateKeyException e) {
                stats.skippedCount++;
                return;
            }
            stats.files.add(toFileVO(entity));
        }
        stats.discoveredCount++;
        updatePendingWatermark(task, file, lastModifiedTime, stats);
    }

    private void updateExistingToParsePending(
            MeasurementFileEntity existing,
            MeasurementFileSyncTaskEntity task,
            ScanContext context,
            DiscoveredFile file,
            String runId,
            String batchId,
            String checksum
    ) {
        existing.setBatchId(batchId);
        existing.setRunId(runId);
        existing.setSourceDatasourceId(task.getSourceDatasourceId());
        existing.setSourceType(context.sourceType);
        existing.setParserType(task.getParserType());
        existing.setRootPath(context.rootPath);
        existing.setRelativePath(file.getRelativePath());
        existing.setFileName(file.getFileName());
        existing.setFullPath(file.getAbsolutePath());
        existing.setFileSize(file.getSize());
        existing.setLastModifiedTime(toDate(file.getLastModifiedTime()));
        existing.setChecksum(checksum);
        existing.setChecksumType(StringUtils.isBlank(checksum) ? null : CHECKSUM_TYPE_SHA256);
        existing.setFileStatus(MeasurementFileStatus.PARSE_PENDING);
        existing.setDiscoverTime(new Date());
        existing.setErrorMessage(null);
        existing.setUpdateTime(new Date());
        fileDao.updateById(existing);
    }

    private void upsertSkippedFile(
            MeasurementFileSyncTaskEntity task,
            ScanContext context,
            DiscoveredFile file,
            String runId,
            String batchId,
            String reason
    ) {
        Date lastModifiedTime = toDate(file.getLastModifiedTime());
        MeasurementFileEntity existing = fileDao.findByPathSizeMtime(
                task.getId(),
                file.getAbsolutePath(),
                file.getSize(),
                lastModifiedTime
        );
        if (existing != null) {
            existing.setBatchId(batchId);
            existing.setRunId(runId);
            existing.setFileStatus(MeasurementFileStatus.SKIPPED);
            existing.setErrorMessage(reason);
            existing.setDiscoverTime(new Date());
            existing.setUpdateTime(new Date());
            fileDao.updateById(existing);
            return;
        }
        MeasurementFileEntity entity = buildFileEntity(
                task,
                context,
                file,
                runId,
                batchId,
                MeasurementFileStatus.SKIPPED,
                reason,
                null
        );
        try {
            fileDao.insert(entity);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private void upsertFailedFile(
            MeasurementFileSyncTaskEntity task,
            ScanContext context,
            DiscoveredFile file,
            String runId,
            String batchId,
            Exception error
    ) {
        String message = error.getMessage() == null ? error.toString() : error.getMessage();
        MeasurementFileEntity entity = buildFileEntity(
                task,
                context,
                file,
                runId,
                batchId,
                MeasurementFileStatus.FAILED,
                message,
                null
        );
        try {
            fileDao.insert(entity);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private MeasurementFileEntity buildFileEntity(
            MeasurementFileSyncTaskEntity task,
            ScanContext context,
            DiscoveredFile file,
            String runId,
            String batchId,
            MeasurementFileStatus status,
            String errorMessage,
            String checksum
    ) {
        Date now = new Date();
        return MeasurementFileEntity.builder()
                .taskId(task.getId())
                .batchId(batchId)
                .runId(runId)
                .sourceDatasourceId(task.getSourceDatasourceId())
                .sourceType(context.sourceType)
                .parserType(task.getParserType())
                .rootPath(context.rootPath)
                .relativePath(file.getRelativePath())
                .fileName(file.getFileName())
                .fullPath(file.getAbsolutePath())
                .fileSize(file.getSize())
                .lastModifiedTime(toDate(file.getLastModifiedTime()))
                .checksum(checksum)
                .checksumType(StringUtils.isBlank(checksum) ? null : CHECKSUM_TYPE_SHA256)
                .fileStatus(status)
                .discoverTime(now)
                .errorMessage(errorMessage)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private MeasurementFileEntity findExisting(
            MeasurementFileSyncTaskEntity task,
            DiscoveredFile file,
            String checksum,
            Date lastModifiedTime
    ) {
        MeasurementDedupStrategy strategy = task.getDedupStrategy() == null
                ? MeasurementDedupStrategy.PATH_SIZE_MTIME
                : task.getDedupStrategy();
        if (strategy == MeasurementDedupStrategy.PATH_ONLY) {
            return fileDao.findByPath(task.getId(), file.getAbsolutePath());
        }
        if (strategy == MeasurementDedupStrategy.PATH_CHECKSUM) {
            if (StringUtils.isBlank(checksum)) {
                throw new ServiceException("PATH_CHECKSUM requires checksum_enabled=true");
            }
            return fileDao.findByPathChecksum(task.getId(), file.getAbsolutePath(), checksum);
        }
        return fileDao.findByPathSizeMtime(task.getId(), file.getAbsolutePath(), file.getSize(), lastModifiedTime);
    }

    private String checksumIfNeeded(MeasurementFileSyncTaskEntity task, DbType sourceType, String fullPath) {
        if (!Boolean.TRUE.equals(task.getChecksumEnabled())
                && task.getDedupStrategy() != MeasurementDedupStrategy.PATH_CHECKSUM) {
            return null;
        }
        return fileSourceClient.sha256(sourceType, fullPath);
    }

    private String unstableReason(MeasurementFileSyncTaskEntity task, Date lastModifiedTime) {
        int stableSeconds = task.getFileStableSeconds() == null ? 0 : task.getFileStableSeconds();
        if (stableSeconds <= 0 || lastModifiedTime == null) {
            return null;
        }
        long ageMillis = System.currentTimeMillis() - lastModifiedTime.getTime();
        if (ageMillis < stableSeconds * 1000L) {
            return "File is not stable yet, required stable seconds=" + stableSeconds;
        }
        return null;
    }

    private String watermarkSkipReason(
            MeasurementFileSyncTaskEntity task,
            DiscoveredFile file,
            Date lastModifiedTime
    ) {
        if (StringUtils.isBlank(task.getCurrentWatermark())) {
            return null;
        }
        MeasurementDiscoveryMode mode = task.getDiscoveryMode() == null
                ? MeasurementDiscoveryMode.FULL_SCAN
                : task.getDiscoveryMode();
        if (mode == MeasurementDiscoveryMode.BY_LAST_MODIFIED) {
            Date watermark = parseWatermarkDate(task.getCurrentWatermark());
            if (watermark != null && lastModifiedTime != null && !lastModifiedTime.after(watermark)) {
                return "File lastModifiedTime is not greater than current watermark";
            }
        }
        if (mode == MeasurementDiscoveryMode.BY_FILE_NAME
                && file.getRelativePath() != null
                && file.getRelativePath().compareTo(task.getCurrentWatermark()) <= 0) {
            return "File name is not greater than current watermark";
        }
        return null;
    }

    private void updatePendingWatermark(
            MeasurementFileSyncTaskEntity task,
            DiscoveredFile file,
            Date lastModifiedTime,
            ScanStats stats
    ) {
        MeasurementDiscoveryMode mode = task.getDiscoveryMode() == null
                ? MeasurementDiscoveryMode.FULL_SCAN
                : task.getDiscoveryMode();
        if (mode == MeasurementDiscoveryMode.BY_LAST_MODIFIED && lastModifiedTime != null) {
            if (stats.pendingWatermarkDate == null || lastModifiedTime.after(stats.pendingWatermarkDate)) {
                stats.pendingWatermarkDate = lastModifiedTime;
            }
        }
        if (mode == MeasurementDiscoveryMode.BY_FILE_NAME && file.getRelativePath() != null) {
            if (stats.pendingWatermarkText == null || file.getRelativePath().compareTo(stats.pendingWatermarkText) > 0) {
                stats.pendingWatermarkText = file.getRelativePath();
            }
        }
    }

    private void updateWatermarkIfNeeded(MeasurementFileSyncTaskEntity task, ScanStats stats) {
        MeasurementDiscoveryMode mode = task.getDiscoveryMode() == null
                ? MeasurementDiscoveryMode.FULL_SCAN
                : task.getDiscoveryMode();
        if (mode == MeasurementDiscoveryMode.BY_LAST_MODIFIED && stats.pendingWatermarkDate != null) {
            taskDao.updateWatermark(task.getId(), formatWatermarkDate(stats.pendingWatermarkDate));
        }
        if (mode == MeasurementDiscoveryMode.BY_FILE_NAME && StringUtils.isNotBlank(stats.pendingWatermarkText)) {
            taskDao.updateWatermark(task.getId(), stats.pendingWatermarkText);
        }
    }

    private ScanContext buildScanContext(MeasurementFileSyncTaskEntity task) {
        DataSource dataSource = dataSourceDao.queryById(task.getSourceDatasourceId());
        if (dataSource == null) {
            throw new ServiceException(Status.DATASOURCE_NOT_EXIST);
        }
        DbType sourceType = dataSource.getDbType();
        if (!fileSourceClient.supports(sourceType)) {
            throw new ServiceException("Unsupported measurement file datasource type: " + sourceType);
        }
        FileDataSourceConfig config = fileSourceClient.parseConfig(sourceType, dataSource.getConnectionParams());
        if (Boolean.FALSE.equals(config.getEnabled())) {
            throw new ServiceException("Datasource is disabled: " + dataSource.getName());
        }
        String rootPath = StringUtils.defaultIfBlank(task.getSourceRootPath(), config.getRootPath());
        if (StringUtils.isBlank(rootPath)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "rootPath");
        }
        ScanContext context = new ScanContext();
        context.sourceType = sourceType;
        context.config = config;
        context.rootPath = rootPath.trim();
        return context;
    }

    private MeasurementFileSyncTaskEntity loadTask(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskId");
        }
        MeasurementFileSyncTaskEntity task = taskDao.queryById(taskId);
        if (task == null) {
            throw new ServiceException("Measurement file sync task not found, taskId=" + taskId);
        }
        return task;
    }

    private void addDryRunFile(
            MeasurementFileSyncTaskEntity task,
            ScanContext context,
            DiscoveredFile file,
            MeasurementFileStatus status,
            String errorMessage,
            boolean dryRun,
            ScanStats stats
    ) {
        if (!dryRun) {
            return;
        }
        stats.files.add(toFileVO(buildFileEntity(task, context, file, null, null, status, errorMessage, null)));
    }

    private MeasurementFileScanResultVO toScanResult(
            Long taskId,
            String runId,
            String batchId,
            MeasurementRunStatus status,
            ScanStats stats
    ) {
        MeasurementFileScanResultVO result = new MeasurementFileScanResultVO();
        result.setTaskId(taskId);
        result.setRunId(runId);
        result.setBatchId(batchId);
        result.setStatus(status);
        result.setScannedCount(stats.scannedCount);
        result.setDiscoveredCount(stats.discoveredCount);
        result.setSkippedCount(stats.skippedCount);
        result.setFailedCount(stats.failedCount);
        result.setErrorMessage(stats.errorMessage);
        result.setFiles(stats.files);
        return result;
    }

    private MeasurementFileRunVO toRunVO(MeasurementFileRunEntity entity) {
        MeasurementFileRunVO vo = new MeasurementFileRunVO();
        vo.setId(entity.getId());
        vo.setRunId(entity.getRunId());
        vo.setBatchId(entity.getBatchId());
        vo.setTaskId(entity.getTaskId());
        vo.setTriggerType(entity.getTriggerType());
        vo.setStatus(entity.getStatus());
        vo.setSourceDatasourceId(entity.getSourceDatasourceId());
        vo.setScannedCount(entity.getScannedCount());
        vo.setDiscoveredCount(entity.getDiscoveredCount());
        vo.setSkippedCount(entity.getSkippedCount());
        vo.setFailedCount(entity.getFailedCount());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
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
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private ScanStats emptyStats(String message) {
        ScanStats stats = new ScanStats();
        stats.errorMessage = message;
        return stats;
    }

    private Date toDate(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private Date parseWatermarkDate(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return Date.from(LocalDateTime.parse(text.trim(), WATERMARK_FORMATTER)
                    .atZone(ZoneId.systemDefault())
                    .toInstant());
        } catch (DateTimeParseException ignored) {
            try {
                return Date.from(Instant.parse(text.trim()));
            } catch (Exception ignoredAgain) {
                throw new ServiceException("Invalid BY_LAST_MODIFIED currentWatermark: " + text);
            }
        }
    }

    private String formatWatermarkDate(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(WATERMARK_FORMATTER);
    }

    private String generateRuntimeId(String prefix, String taskCode) {
        String safeTaskCode = taskCode == null ? "task" : taskCode.replaceAll("[^A-Za-z0-9_]", "_");
        return prefix
                + "_"
                + safeTaskCode
                + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_"
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }

    private String appendMessage(String current, String next) {
        if (StringUtils.isBlank(current)) {
            return next;
        }
        return current + "\n" + next;
    }

    private static class ScanContext {
        private DbType sourceType;
        private FileDataSourceConfig config;
        private String rootPath;
    }

    private static class ScanStats {
        private int scannedCount;
        private int discoveredCount;
        private int skippedCount;
        private int failedCount;
        private String errorMessage;
        private Date pendingWatermarkDate;
        private String pendingWatermarkText;
        private List<MeasurementFileVO> files = new ArrayList<>();
    }
}
