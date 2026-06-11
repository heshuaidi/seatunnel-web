package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.SyncFileDiscoveryService;
import org.apache.seatunnel.web.api.service.file.LocalSyncFileScanner;
import org.apache.seatunnel.web.api.service.file.SyncRemoteFileScanner;
import org.apache.seatunnel.web.api.service.model.DiscoveredFile;
import org.apache.seatunnel.web.api.service.model.FileDiscoveryResult;
import org.apache.seatunnel.web.api.service.model.LocalFileScanRequest;
import org.apache.seatunnel.web.api.service.model.RemoteFileScanRequest;
import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.common.enums.SyncFileCursorMode;
import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.common.enums.SyncFileSystem;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.repository.SyncFileItemDao;
import org.apache.seatunnel.web.dao.repository.SyncIncrementalConfigDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskDao;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncFileDiscoveryServiceImpl extends SyncServiceSupport implements SyncFileDiscoveryService {

    private static final int DEFAULT_MAX_FILES = 1000;

    @Resource
    private SyncTaskDao syncTaskDao;

    @Resource
    private SyncIncrementalConfigDao syncIncrementalConfigDao;

    @Resource
    private SyncFileItemDao syncFileItemDao;

    @Resource
    private SyncAuditService syncAuditService;

    @Resource
    private LocalSyncFileScanner localSyncFileScanner;

    @Resource
    private SyncRemoteFileScanner syncRemoteFileScanner;

    @Override
    public FileDiscoveryResult discoverFiles(Long taskId) {
        requireId(taskId);
        SyncTaskEntity task = syncTaskDao.queryById(taskId);
        if (task == null) {
            throw new ServiceException("Sync task not found, taskId=" + taskId);
        }
        return discoverFiles(task);
    }

    @Override
    public FileDiscoveryResult discoverFiles(String taskCode) {
        if (isBlank(taskCode)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskCode");
        }
        SyncTaskEntity task = syncTaskDao.queryByTaskCode(taskCode);
        if (task == null) {
            throw new ServiceException("Sync task not found, taskCode=" + taskCode);
        }
        return discoverFiles(task);
    }

    @Override
    public List<SyncFileItemEntity> claimFilesForBatch(Long taskId, String batchId, int maxFiles) {
        requireId(taskId);
        if (isBlank(batchId)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "batchId");
        }
        int safeMaxFiles = maxFiles <= 0 ? DEFAULT_MAX_FILES : maxFiles;
        SyncTaskEntity task = loadTask(taskId);
        SyncIncrementalConfigEntity config = loadConfig(taskId);
        List<SyncFileItemEntity> claimableFiles = syncFileItemDao.listClaimableByTaskId(taskId, safeMaxFiles);
        List<SyncFileItemEntity> claimedFiles = new ArrayList<>();
        for (SyncFileItemEntity file : claimableFiles) {
            if (syncFileItemDao.claimFile(file.getId(), batchId)) {
                file.setBatchId(batchId);
                file.setRunId(null);
                file.setStatus(SyncFileItemStatus.CLAIMED);
                file.setErrorMessage(null);
                claimedFiles.add(file);
            }
        }
        syncAuditService.appendInfo(
                null,
                batchId,
                task.getId(),
                task.getTaskCode(),
                SyncAuditEventType.CLAIM_FILES,
                "Sync files claimed for batch",
                fileAuditDetail(config, Map.of("claimedCount", claimedFiles.size(), "maxFiles", safeMaxFiles))
        );
        return claimedFiles;
    }

    @Override
    public void markFilesProcessing(String batchId, String runId) {
        SyncTaskEntity task = loadTaskByBatchId(batchId);
        SyncIncrementalConfigEntity config = loadConfig(task.getId());
        int count = syncFileItemDao.markBatchProcessing(batchId, runId);
        syncAuditService.appendInfo(
                runId,
                batchId,
                task.getId(),
                task.getTaskCode(),
                SyncAuditEventType.MARK_FILES_PROCESSING,
                "Sync files marked PROCESSING",
                fileAuditDetail(config, Map.of("processingCount", count))
        );
    }

    @Override
    public void markFilesSuccess(String batchId, String runId) {
        SyncTaskEntity task = loadTaskByBatchId(batchId);
        SyncIncrementalConfigEntity config = loadConfig(task.getId());
        int count = syncFileItemDao.markBatchSuccess(batchId, runId);
        syncAuditService.appendInfo(
                runId,
                batchId,
                task.getId(),
                task.getTaskCode(),
                SyncAuditEventType.MARK_FILES_SUCCESS,
                "Sync files marked SUCCESS",
                fileAuditDetail(config, Map.of("successCount", count))
        );
    }

    @Override
    public void markFilesFailed(String batchId, String runId, String errorMessage) {
        SyncTaskEntity task = loadTaskByBatchId(batchId);
        SyncIncrementalConfigEntity config = loadConfig(task.getId());
        int count = syncFileItemDao.markBatchFailed(batchId, runId, errorMessage);
        syncAuditService.appendInfo(
                runId,
                batchId,
                task.getId(),
                task.getTaskCode(),
                SyncAuditEventType.MARK_FILES_FAILED,
                "Sync files marked FAILED",
                fileAuditDetail(config, failedDetail(count, errorMessage))
        );
    }

    @Override
    public List<SyncFileItemEntity> listFiles(
            Long taskId,
            SyncFileItemStatus status,
            String batchId,
            String runId,
            String fileName,
            String filePath
    ) {
        requireId(taskId);
        return syncFileItemDao.listByTaskId(taskId, status, batchId, runId, fileName, filePath);
    }

    @Override
    public List<SyncFileItemEntity> listFilesByBatchId(String batchId) {
        if (isBlank(batchId)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "batchId");
        }
        return syncFileItemDao.listByBatchId(batchId);
    }

    @Override
    public List<SyncFileItemEntity> retryFailedFiles(String batchId) {
        SyncTaskEntity task = loadTaskByBatchId(batchId);
        SyncIncrementalConfigEntity config = loadConfig(task.getId());
        int retryCount = syncFileItemDao.retryFailedByBatchId(batchId);
        syncAuditService.appendInfo(
                null,
                batchId,
                task.getId(),
                task.getTaskCode(),
                SyncAuditEventType.CLAIM_FILES,
                "Failed sync files reset to CLAIMED",
                fileAuditDetail(config, Map.of("claimedCount", retryCount))
        );
        return syncFileItemDao.listByBatchId(batchId);
    }

    private FileDiscoveryResult discoverFiles(SyncTaskEntity task) {
        SyncIncrementalConfigEntity config = loadConfig(task.getId());
        validateFileDiscoveryConfig(task, config);
        List<DiscoveredFile> files;
        try {
            files = scanFiles(task, config);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            syncAuditService.appendError(
                    null,
                    null,
                    task.getId(),
                    task.getTaskCode(),
                    SyncAuditEventType.DISCOVER_FILES,
                    "Discover sync files failed",
                    fileAuditDetail(config, Map.of("failedCount", 1, "errorMessage", message))
            );
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            }
            throw new ServiceException(message, e);
        }

        SyncFileCursorMode cursorMode = resolveCursorMode(config);
        int skippedCount = 0;
        List<SyncFileItemEntity> discoveredItems = new ArrayList<>();
        for (DiscoveredFile file : files) {
            SyncFileItemEntity existing = findExisting(task.getId(), cursorMode, file);
            if (existing != null) {
                if (existing.getStatus() != SyncFileItemStatus.FAILED) {
                    skippedCount++;
                }
                continue;
            }
            SyncFileItemEntity item = toFileItem(task, config, file);
            syncFileItemDao.insert(item);
            discoveredItems.add(item);
        }

        FileDiscoveryResult result = FileDiscoveryResult.builder()
                .taskId(task.getId())
                .taskCode(task.getTaskCode())
                .sourceType(config.getSourceType() == null ? task.getSourceType() : config.getSourceType())
                .strategy(config.getStrategy())
                .filePath(config.getFilePath())
                .filePattern(config.getFilePattern())
                .discoveredCount(discoveredItems.size())
                .skippedCount(skippedCount)
                .failedCount(0)
                .discoveredFiles(discoveredItems)
                .message("File discovery completed")
                .build();

        syncAuditService.appendInfo(
                null,
                null,
                task.getId(),
                task.getTaskCode(),
                SyncAuditEventType.DISCOVER_FILES,
                "Sync files discovered",
                fileAuditDetail(config, Map.of(
                        "discoveredCount", discoveredItems.size(),
                        "skippedCount", skippedCount
                ))
        );
        return result;
    }

    private List<DiscoveredFile> scanFiles(SyncTaskEntity task, SyncIncrementalConfigEntity config) {
        SyncSourceType sourceType = config.getSourceType() == null ? task.getSourceType() : config.getSourceType();
        if (sourceType == SyncSourceType.LOCAL_FILE) {
            return localSyncFileScanner.scan(LocalFileScanRequest.builder()
                    .basePath(config.getFilePath())
                    .pattern(config.getFilePattern())
                    .recursive(config.getFileRecursive())
                    .timezone(config.getFileTimezone())
                    .build());
        }
        if (sourceType == SyncSourceType.FTP_FILE) {
            return syncRemoteFileScanner.scan(RemoteFileScanRequest.builder()
                    .taskId(task.getId())
                    .taskCode(task.getTaskCode())
                    .remotePath(config.getFilePath())
                    .pattern(config.getFilePattern())
                    .recursive(config.getFileRecursive())
                    .timezone(config.getFileTimezone())
                    .build());
        }
        throw new ServiceException("Unsupported file source type: " + sourceType);
    }

    private void validateFileDiscoveryConfig(SyncTaskEntity task, SyncIncrementalConfigEntity config) {
        SyncSourceType sourceType = config.getSourceType() == null ? task.getSourceType() : config.getSourceType();
        if (sourceType != SyncSourceType.LOCAL_FILE && sourceType != SyncSourceType.FTP_FILE) {
            throw new ServiceException("Unsupported file source type: " + sourceType);
        }
        if (config.getStrategy() != SyncIncrementalStrategy.FILE_MANIFEST
                && config.getStrategy() != SyncIncrementalStrategy.FILE_MTIME) {
            throw new ServiceException("Unsupported file incremental strategy: " + config.getStrategy());
        }
        if (isBlank(config.getFilePath())) {
            throw new ServiceException("Sync incremental config filePath is required, taskId=" + task.getId());
        }
        resolveCursorMode(config);
    }

    private SyncFileCursorMode resolveCursorMode(SyncIncrementalConfigEntity config) {
        SyncFileCursorMode cursorMode = config.getFileCursorMode() == null
                ? SyncFileCursorMode.PATH_MTIME_SIZE
                : config.getFileCursorMode();
        if (cursorMode == SyncFileCursorMode.CHECKSUM) {
            throw new ServiceException("Unsupported file cursor mode: CHECKSUM");
        }
        return cursorMode;
    }

    private SyncFileItemEntity findExisting(Long taskId, SyncFileCursorMode cursorMode, DiscoveredFile file) {
        Date lastModifiedTime = toDate(file.getLastModifiedTime());
        if (cursorMode == SyncFileCursorMode.MTIME) {
            return syncFileItemDao.findByMtimeIdentity(taskId, file.getAbsolutePath(), lastModifiedTime);
        }
        if (cursorMode == SyncFileCursorMode.PATH_MTIME_SIZE || cursorMode == SyncFileCursorMode.MANIFEST) {
            return syncFileItemDao.findByPathMtimeSizeIdentity(
                    taskId,
                    file.getAbsolutePath(),
                    file.getSize(),
                    lastModifiedTime
            );
        }
        throw new ServiceException("Unsupported file cursor mode: " + cursorMode.getCode());
    }

    private SyncFileItemEntity toFileItem(
            SyncTaskEntity task,
            SyncIncrementalConfigEntity config,
            DiscoveredFile file
    ) {
        Date now = now();
        SyncSourceType sourceType = config.getSourceType() == null ? task.getSourceType() : config.getSourceType();
        return SyncFileItemEntity.builder()
                .taskId(task.getId())
                .sourceType(sourceType)
                .fileSystem(sourceType == SyncSourceType.LOCAL_FILE ? SyncFileSystem.LOCAL : SyncFileSystem.FTP)
                .filePath(file.getAbsolutePath())
                .fileName(file.getFileName())
                .relativePath(file.getRelativePath())
                .fileSize(file.getSize())
                .lastModifiedTime(toDate(file.getLastModifiedTime()))
                .discoveredTime(now)
                .status(SyncFileItemStatus.DISCOVERED)
                .createTime(now)
                .updateTime(now)
                .build();
    }

    private Map<String, Object> fileAuditDetail(SyncIncrementalConfigEntity config, Map<String, Object> values) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("filePath", config.getFilePath());
        detail.put("filePattern", config.getFilePattern());
        detail.put("recursive", config.getFileRecursive());
        detail.put("cursorMode", resolveCursorMode(config).getCode());
        detail.putAll(values);
        return detail;
    }

    private Map<String, Object> failedDetail(int count, String errorMessage) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("failedCount", count);
        detail.put("errorMessage", errorMessage);
        return detail;
    }

    private SyncTaskEntity loadTask(Long taskId) {
        SyncTaskEntity task = syncTaskDao.queryById(taskId);
        if (task == null) {
            throw new ServiceException("Sync task not found, taskId=" + taskId);
        }
        return task;
    }

    private SyncTaskEntity loadTaskByBatchId(String batchId) {
        if (isBlank(batchId)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "batchId");
        }
        List<SyncFileItemEntity> files = syncFileItemDao.listByBatchId(batchId);
        if (files.isEmpty()) {
            throw new ServiceException("No sync files found, batchId=" + batchId);
        }
        return loadTask(files.get(0).getTaskId());
    }

    private SyncIncrementalConfigEntity loadConfig(Long taskId) {
        SyncIncrementalConfigEntity config = syncIncrementalConfigDao.queryByTaskId(taskId);
        if (config == null) {
            throw new ServiceException("Sync incremental config not found, taskId=" + taskId);
        }
        return config;
    }

    private Date toDate(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
