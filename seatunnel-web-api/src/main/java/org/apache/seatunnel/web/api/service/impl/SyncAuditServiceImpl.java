package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncAuditService;
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
}
