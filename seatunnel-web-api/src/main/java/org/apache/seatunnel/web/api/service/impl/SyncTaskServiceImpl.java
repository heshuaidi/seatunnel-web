package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncTaskService;
import org.apache.seatunnel.web.common.enums.SyncEngineType;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.repository.SyncTaskDao;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class SyncTaskServiceImpl extends SyncServiceSupport implements SyncTaskService {

    @Resource
    private SyncTaskDao syncTaskDao;

    @Override
    public Long create(SyncTaskEntity entity) {
        requireEntity(entity, "syncTask");
        Date now = now();
        if (entity.getEngineType() == null) {
            entity.setEngineType(SyncEngineType.ZETA);
        }
        if (entity.getIncrementalEnabled() == null) {
            entity.setIncrementalEnabled(false);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(SyncTaskStatus.DRAFT);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(now);
        }
        syncTaskDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncTaskEntity entity) {
        requireEntity(entity, "syncTask");
        requireId(entity.getId());
        entity.setUpdateTime(now());
        return syncTaskDao.updateById(entity);
    }

    @Override
    public SyncTaskEntity getById(Long id) {
        requireId(id);
        return syncTaskDao.queryById(id);
    }

    @Override
    public SyncTaskEntity getByTaskCode(String taskCode) {
        if (isBlank(taskCode)) {
            return null;
        }
        return syncTaskDao.queryByTaskCode(taskCode);
    }

    @Override
    public Boolean updateStatus(Long id, SyncTaskStatus status) {
        requireId(id);
        return syncTaskDao.updateStatus(id, status);
    }
}
