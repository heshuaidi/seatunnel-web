package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.NonNull;
import org.apache.seatunnel.web.dao.entity.SyncTaskLockEntity;
import org.apache.seatunnel.web.dao.mapper.SyncTaskLockMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskLockDao;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public class SyncTaskLockDaoImpl extends BaseDao<SyncTaskLockEntity, SyncTaskLockMapper>
        implements SyncTaskLockDao {

    private final SyncTaskLockMapper syncTaskLockMapper;

    public SyncTaskLockDaoImpl(@NonNull SyncTaskLockMapper syncTaskLockMapper) {
        super(syncTaskLockMapper);
        this.syncTaskLockMapper = syncTaskLockMapper;
    }

    @Override
    public SyncTaskLockEntity queryByTaskIdAndWatermarkKey(Long taskId, String watermarkKey) {
        if (taskId == null || isBlank(watermarkKey)) {
            return null;
        }
        return syncTaskLockMapper.selectOne(
                new LambdaQueryWrapper<SyncTaskLockEntity>()
                        .eq(SyncTaskLockEntity::getTaskId, taskId)
                        .eq(SyncTaskLockEntity::getWatermarkKey, watermarkKey)
                        .last("limit 1")
        );
    }

    @Override
    public boolean takeoverExpiredLock(
            Long taskId,
            String watermarkKey,
            String lockToken,
            String lockOwner,
            String runId,
            String batchId,
            Date now,
            Date expiresAt
    ) {
        if (taskId == null || isBlank(watermarkKey) || isBlank(lockToken) || now == null || expiresAt == null) {
            return false;
        }
        SyncTaskLockEntity update = new SyncTaskLockEntity();
        update.setLockToken(lockToken);
        update.setLockOwner(lockOwner);
        update.setRunId(runId);
        update.setBatchId(batchId);
        update.setLockedAt(now);
        update.setExpiresAt(expiresAt);
        update.setStatus("LOCKED");
        update.setUpdateTime(now);
        return syncTaskLockMapper.update(
                update,
                new LambdaUpdateWrapper<SyncTaskLockEntity>()
                        .eq(SyncTaskLockEntity::getTaskId, taskId)
                        .eq(SyncTaskLockEntity::getWatermarkKey, watermarkKey)
                        .lt(SyncTaskLockEntity::getExpiresAt, now)
        ) > 0;
    }

    @Override
    public boolean release(Long taskId, String watermarkKey, String lockToken) {
        if (taskId == null || isBlank(watermarkKey) || isBlank(lockToken)) {
            return false;
        }
        return syncTaskLockMapper.delete(
                new LambdaQueryWrapper<SyncTaskLockEntity>()
                        .eq(SyncTaskLockEntity::getTaskId, taskId)
                        .eq(SyncTaskLockEntity::getWatermarkKey, watermarkKey)
                        .eq(SyncTaskLockEntity::getLockToken, lockToken)
        ) > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
