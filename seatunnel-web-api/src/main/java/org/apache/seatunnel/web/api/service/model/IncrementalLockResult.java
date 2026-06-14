package org.apache.seatunnel.web.api.service.model;

import lombok.Data;
import org.apache.seatunnel.web.dao.entity.SyncTaskLockEntity;

@Data
public class IncrementalLockResult {

    private boolean acquired;

    private String lockToken;

    private SyncTaskLockEntity existingLock;

    public static IncrementalLockResult acquired(String lockToken) {
        IncrementalLockResult result = new IncrementalLockResult();
        result.acquired = true;
        result.lockToken = lockToken;
        return result;
    }

    public static IncrementalLockResult rejected(SyncTaskLockEntity existingLock) {
        IncrementalLockResult result = new IncrementalLockResult();
        result.acquired = false;
        result.existingLock = existingLock;
        return result;
    }
}
