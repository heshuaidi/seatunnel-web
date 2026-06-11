package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.api.service.model.FileDiscoveryResult;
import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;

import java.util.List;

public interface SyncFileDiscoveryService {

    FileDiscoveryResult discoverFiles(Long taskId);

    FileDiscoveryResult discoverFiles(String taskCode);

    List<SyncFileItemEntity> claimFilesForBatch(Long taskId, String batchId, int maxFiles);

    void markFilesProcessing(String batchId, String runId);

    void markFilesSuccess(String batchId, String runId);

    void markFilesFailed(String batchId, String runId, String errorMessage);

    List<SyncFileItemEntity> listFiles(
            Long taskId,
            SyncFileItemStatus status,
            String batchId,
            String runId,
            String fileName,
            String filePath
    );

    List<SyncFileItemEntity> listFilesByBatchId(String batchId);

    List<SyncFileItemEntity> retryFailedFiles(String batchId);
}
