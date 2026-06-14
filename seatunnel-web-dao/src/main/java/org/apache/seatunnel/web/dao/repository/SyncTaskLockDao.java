package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.dao.entity.SyncTaskLockEntity;

import java.util.Date;

public interface SyncTaskLockDao extends IDao<SyncTaskLockEntity> {

    SyncTaskLockEntity queryByTaskIdAndWatermarkKey(Long taskId, String watermarkKey);

    boolean takeoverExpiredLock(
            Long taskId,
            String watermarkKey,
            String lockToken,
            String lockOwner,
            String runId,
            String batchId,
            Date now,
            Date expiresAt
    );

    boolean release(Long taskId, String watermarkKey, String lockToken);
}
