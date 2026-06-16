package org.apache.seatunnel.web.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.seatunnel.web.api.service.IncrementalTaskLockService;
import org.apache.seatunnel.web.api.service.model.IncrementalLockResult;
import org.apache.seatunnel.web.dao.entity.SyncTaskLockEntity;
import org.apache.seatunnel.web.dao.repository.SyncTaskLockDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncrementalTaskLockServiceImpl implements IncrementalTaskLockService {

    private final SyncTaskLockDao syncTaskLockDao;
    private final SyncRunProperties syncRunProperties;

    @Value("${batch-link-up.incremental.lock-ttl-minutes:${seatunnel.sync.incremental-lock-ttl-minutes:60}}")
    private long configuredLockTtlMinutes;

    @Override
    public IncrementalLockResult acquireLock(Long taskId, String watermarkKey, String runId, String batchId) {
        return acquireLock(taskId, watermarkKey, runId, batchId, null);
    }

    @Override
    public IncrementalLockResult acquireLock(
            Long taskId,
            String watermarkKey,
            String runId,
            String batchId,
            Long ttlMinutesOverride
    ) {
        if (taskId == null) {
            return IncrementalLockResult.rejected(null);
        }
        String safeWatermarkKey = isBlank(watermarkKey) ? "default" : watermarkKey.trim();
        String token = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        long ttlMinutes = ttlMinutesOverride != null && ttlMinutesOverride > 0
                ? ttlMinutesOverride
                : configuredLockTtlMinutes > 0
                ? configuredLockTtlMinutes
                : syncRunProperties.getIncrementalLockTtlMinutes();
        Date expiresAt = Date.from(Instant.ofEpochMilli(now.getTime())
                .plusSeconds(Math.max(1L, ttlMinutes) * 60L));
        SyncTaskLockEntity lock = SyncTaskLockEntity.builder()
                .taskId(taskId)
                .watermarkKey(safeWatermarkKey)
                .lockToken(token)
                .lockOwner(lockOwner())
                .runId(runId)
                .batchId(batchId)
                .lockedAt(now)
                .expiresAt(expiresAt)
                .status("LOCKED")
                .createTime(now)
                .updateTime(now)
                .build();
        try {
            syncTaskLockDao.insert(lock);
            return IncrementalLockResult.acquired(token);
        } catch (DuplicateKeyException e) {
            if (syncTaskLockDao.takeoverExpiredLock(
                    taskId,
                    safeWatermarkKey,
                    token,
                    lock.getLockOwner(),
                    runId,
                    batchId,
                    now,
                    expiresAt
            )) {
                return IncrementalLockResult.acquired(token);
            }
            return IncrementalLockResult.rejected(
                    syncTaskLockDao.queryByTaskIdAndWatermarkKey(taskId, safeWatermarkKey)
            );
        }
    }

    @Override
    public boolean releaseLock(Long taskId, String watermarkKey, String lockToken) {
        if (taskId == null || isBlank(lockToken)) {
            return false;
        }
        return syncTaskLockDao.release(taskId, isBlank(watermarkKey) ? "default" : watermarkKey.trim(), lockToken);
    }

    private String lockOwner() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
