package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncTaskVersionService;
import org.apache.seatunnel.web.common.enums.SyncPublishStatus;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.repository.SyncTaskVersionDao;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class SyncTaskVersionServiceImpl extends SyncServiceSupport implements SyncTaskVersionService {

    @Resource
    private SyncTaskVersionDao syncTaskVersionDao;

    @Override
    public Long create(SyncTaskVersionEntity entity) {
        requireEntity(entity, "syncTaskVersion");
        Date now = now();
        if (entity.getPublishStatus() == null) {
            entity.setPublishStatus(SyncPublishStatus.DRAFT);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        syncTaskVersionDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncTaskVersionEntity entity) {
        requireEntity(entity, "syncTaskVersion");
        requireId(entity.getId());
        return syncTaskVersionDao.updateById(entity);
    }

    @Override
    public SyncTaskVersionEntity getById(Long id) {
        requireId(id);
        return syncTaskVersionDao.queryById(id);
    }

    @Override
    public SyncTaskVersionEntity getByTaskId(Long taskId) {
        requireId(taskId);
        return syncTaskVersionDao.queryLatestByTaskId(taskId);
    }

    @Override
    public List<SyncTaskVersionEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncTaskVersionDao.listByTaskId(taskId);
    }

    @Override
    public Boolean updateStatus(Long id, SyncPublishStatus publishStatus) {
        requireId(id);
        return syncTaskVersionDao.updateStatus(id, publishStatus);
    }
}
