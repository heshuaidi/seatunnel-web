package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncBatchService;
import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.repository.SyncBatchDao;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class SyncBatchServiceImpl extends SyncServiceSupport implements SyncBatchService {

    @Resource
    private SyncBatchDao syncBatchDao;

    @Override
    public Long create(SyncBatchEntity entity) {
        requireEntity(entity, "syncBatch");
        Date now = now();
        if (entity.getStatus() == null) {
            entity.setStatus(SyncBatchStatus.CREATED);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(now);
        }
        syncBatchDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncBatchEntity entity) {
        requireEntity(entity, "syncBatch");
        requireId(entity.getId());
        entity.setUpdateTime(now());
        return syncBatchDao.updateById(entity);
    }

    @Override
    public SyncBatchEntity getById(Long id) {
        requireId(id);
        return syncBatchDao.queryById(id);
    }

    @Override
    public SyncBatchEntity getByBatchId(String batchId) {
        if (isBlank(batchId)) {
            return null;
        }
        return syncBatchDao.queryByBatchId(batchId);
    }

    @Override
    public List<SyncBatchEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncBatchDao.listByTaskId(taskId);
    }

    @Override
    public Boolean updateStatus(String batchId, SyncBatchStatus status, String errorMessage) {
        return syncBatchDao.updateStatus(batchId, status, errorMessage);
    }
}
