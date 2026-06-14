package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.api.service.model.IncrementalLockResult;

public interface IncrementalTaskLockService {

    IncrementalLockResult acquireLock(Long taskId, String watermarkKey, String runId, String batchId);

    boolean releaseLock(Long taskId, String watermarkKey, String lockToken);
}
