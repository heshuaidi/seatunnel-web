package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.dao.entity.SyncAuditEntity;

import java.util.List;

public interface SyncAuditDao extends IDao<SyncAuditEntity> {

    List<SyncAuditEntity> listByTaskId(Long taskId);

    List<SyncAuditEntity> listByRunId(String runId);

    List<SyncAuditEntity> listByBatchId(String batchId);
}
