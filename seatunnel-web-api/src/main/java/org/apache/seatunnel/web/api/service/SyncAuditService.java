package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.dao.entity.SyncAuditEntity;

import java.util.List;

public interface SyncAuditService {

    Long appendAudit(SyncAuditEntity entity);

    SyncAuditEntity getById(Long id);

    List<SyncAuditEntity> listByTaskId(Long taskId);

    List<SyncAuditEntity> listByRunId(String runId);

    List<SyncAuditEntity> listByBatchId(String batchId);
}
