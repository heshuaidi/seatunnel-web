package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.utils.SyncSensitiveMaskUtils;
import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.common.enums.SyncAuditLevel;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.dao.entity.SyncAuditEntity;
import org.apache.seatunnel.web.dao.repository.SyncAuditDao;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncAuditServiceImpl extends SyncServiceSupport implements SyncAuditService {

    @Resource
    private SyncAuditDao syncAuditDao;

    @Override
    public Long appendAudit(SyncAuditEntity entity) {
        requireEntity(entity, "syncAudit");
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now());
        }
        syncAuditDao.insert(entity);
        return entity.getId();
    }

    @Override
    public SyncAuditEntity getById(Long id) {
        requireId(id);
        return syncAuditDao.queryById(id);
    }

    @Override
    public List<SyncAuditEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncAuditDao.listByTaskId(taskId);
    }

    @Override
    public List<SyncAuditEntity> listByRunId(String runId) {
        return syncAuditDao.listByRunId(runId);
    }

    @Override
    public List<SyncAuditEntity> listByBatchId(String batchId) {
        return syncAuditDao.listByBatchId(batchId);
    }

    @Override
    public Long appendInfo(
            String runId,
            String batchId,
            Long taskId,
            String taskCode,
            SyncAuditEventType eventType,
            String message,
            Object detail
    ) {
        return append(runId, batchId, taskId, taskCode, eventType, SyncAuditLevel.INFO, message, detail);
    }

    @Override
    public Long appendWarn(
            String runId,
            String batchId,
            Long taskId,
            String taskCode,
            SyncAuditEventType eventType,
            String message,
            Object detail
    ) {
        return append(runId, batchId, taskId, taskCode, eventType, SyncAuditLevel.WARN, message, detail);
    }

    @Override
    public Long appendError(
            String runId,
            String batchId,
            Long taskId,
            String taskCode,
            SyncAuditEventType eventType,
            String message,
            Object detail
    ) {
        return append(runId, batchId, taskId, taskCode, eventType, SyncAuditLevel.ERROR, message, detail);
    }

    private Long append(
            String runId,
            String batchId,
            Long taskId,
            String taskCode,
            SyncAuditEventType eventType,
            SyncAuditLevel eventLevel,
            String message,
            Object detail
    ) {
        SyncAuditEntity entity = SyncAuditEntity.builder()
                .runId(runId)
                .batchId(batchId)
                .taskId(taskId)
                .taskCode(taskCode)
                .eventType(eventType)
                .eventLevel(eventLevel)
                .eventMessage(message)
                .detailJson(toDetailJson(detail))
                .createTime(now())
                .build();
        return appendAudit(entity);
    }

    private String toDetailJson(Object detail) {
        if (detail == null) {
            return null;
        }
        if (detail instanceof String) {
            return (String) detail;
        }
        return JSONUtils.toJsonString(SyncSensitiveMaskUtils.mask(detail));
    }
}
