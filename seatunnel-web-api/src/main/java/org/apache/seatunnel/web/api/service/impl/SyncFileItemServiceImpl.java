package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncFileItemService;
import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;
import org.apache.seatunnel.web.dao.repository.SyncFileItemDao;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class SyncFileItemServiceImpl extends SyncServiceSupport implements SyncFileItemService {

    @Resource
    private SyncFileItemDao syncFileItemDao;

    @Override
    public Long create(SyncFileItemEntity entity) {
        requireEntity(entity, "syncFileItem");
        Date now = now();
        if (entity.getStatus() == null) {
            entity.setStatus(SyncFileItemStatus.DISCOVERED);
        }
        if (entity.getDiscoveredTime() == null) {
            entity.setDiscoveredTime(now);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(now);
        }
        syncFileItemDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncFileItemEntity entity) {
        requireEntity(entity, "syncFileItem");
        requireId(entity.getId());
        entity.setUpdateTime(now());
        return syncFileItemDao.updateById(entity);
    }

    @Override
    public SyncFileItemEntity getById(Long id) {
        requireId(id);
        return syncFileItemDao.queryById(id);
    }

    @Override
    public List<SyncFileItemEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncFileItemDao.listByTaskId(taskId);
    }

    @Override
    public List<SyncFileItemEntity> listByBatchId(String batchId) {
        return syncFileItemDao.listByBatchId(batchId);
    }

    @Override
    public Boolean updateStatus(Long id, SyncFileItemStatus status, String errorMessage) {
        requireId(id);
        return syncFileItemDao.updateStatus(id, status, errorMessage);
    }
}
