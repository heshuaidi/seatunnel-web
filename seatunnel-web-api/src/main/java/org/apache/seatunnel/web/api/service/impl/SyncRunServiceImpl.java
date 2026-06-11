package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncRunService;
import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;
import org.apache.seatunnel.web.dao.repository.SyncRunDao;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class SyncRunServiceImpl extends SyncServiceSupport implements SyncRunService {

    @Resource
    private SyncRunDao syncRunDao;

    @Override
    public Long create(SyncRunEntity entity) {
        requireEntity(entity, "syncRun");
        Date now = now();
        if (entity.getStatus() == null) {
            entity.setStatus(SyncRunStatus.CREATED);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(now);
        }
        syncRunDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncRunEntity entity) {
        requireEntity(entity, "syncRun");
        requireId(entity.getId());
        entity.setUpdateTime(now());
        return syncRunDao.updateById(entity);
    }

    @Override
    public SyncRunEntity getById(Long id) {
        requireId(id);
        return syncRunDao.queryById(id);
    }

    @Override
    public SyncRunEntity getByRunId(String runId) {
        if (isBlank(runId)) {
            return null;
        }
        return syncRunDao.queryByRunId(runId);
    }

    @Override
    public List<SyncRunEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncRunDao.listByTaskId(taskId);
    }

    @Override
    public Boolean updateStatus(String runId, SyncRunStatus status, String errorMessage) {
        return syncRunDao.updateStatus(runId, status, errorMessage);
    }
}
