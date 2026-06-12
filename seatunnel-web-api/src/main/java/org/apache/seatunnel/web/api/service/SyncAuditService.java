package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.dao.entity.SyncAuditEntity;

import java.util.List;

public interface SyncAuditService {

    Long appendAudit(SyncAuditEntity entity);

    SyncAuditEntity getById(Long id);

    List<SyncAuditEntity> listByTaskId(Long taskId);

    List<SyncAuditEntity> listByRunId(String runId);

    List<SyncAuditEntity> listByBatchId(String batchId);

    Long appendInfo(
            String runId,
            String batchId,
            Long taskId,
            String taskCode,
            SyncAuditEventType eventType,
            String message,
            Object detail
    );

    Long appendWarn(
            String runId,
            String batchId,
            Long taskId,
            String taskCode,
            SyncAuditEventType eventType,
            String message,
            Object detail
    );

    Long appendError(
            String runId,
            String batchId,
            Long taskId,
            String taskCode,
            SyncAuditEventType eventType,
            String message,
            Object detail
    );
}
