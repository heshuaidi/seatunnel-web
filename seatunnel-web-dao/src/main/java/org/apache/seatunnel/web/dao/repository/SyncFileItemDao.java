package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;

import java.util.Date;
import java.util.List;

public interface SyncFileItemDao extends IDao<SyncFileItemEntity> {

    List<SyncFileItemEntity> listByTaskId(Long taskId);

    List<SyncFileItemEntity> listByTaskId(
            Long taskId,
            SyncFileItemStatus status,
            String batchId,
            String runId,
            String fileName,
            String filePath
    );

    List<SyncFileItemEntity> listByBatchId(String batchId);

    List<SyncFileItemEntity> listClaimableByTaskId(Long taskId, int limit);

    SyncFileItemEntity findByMtimeIdentity(Long taskId, String filePath, Date lastModifiedTime);

    SyncFileItemEntity findByPathMtimeSizeIdentity(
            Long taskId,
            String filePath,
            Long fileSize,
            Date lastModifiedTime
    );

    boolean updateStatus(Long id, SyncFileItemStatus status, String errorMessage);

    boolean claimFile(Long id, String batchId);

    int markBatchProcessing(String batchId, String runId);

    int markBatchSuccess(String batchId, String runId);

    int markBatchFailed(String batchId, String runId, String errorMessage);

    int retryFailedByBatchId(String batchId);
}
