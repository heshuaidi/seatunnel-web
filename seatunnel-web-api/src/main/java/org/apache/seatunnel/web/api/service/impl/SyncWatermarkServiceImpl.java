package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.common.constants.SyncConstants;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.dao.repository.SyncWatermarkDao;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncWatermarkServiceImpl extends SyncServiceSupport implements SyncWatermarkService {

    @Resource
    private SyncWatermarkDao syncWatermarkDao;

    @Override
    public Long create(SyncWatermarkEntity entity) {
        requireEntity(entity, "syncWatermark");
        if (isBlank(entity.getWatermarkKey())) {
            entity.setWatermarkKey(SyncConstants.DEFAULT_WATERMARK_KEY);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(now());
        }
        syncWatermarkDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncWatermarkEntity entity) {
        requireEntity(entity, "syncWatermark");
        requireId(entity.getId());
        entity.setUpdateTime(now());
        return syncWatermarkDao.updateById(entity);
    }

    @Override
    public SyncWatermarkEntity getById(Long id) {
        requireId(id);
        return syncWatermarkDao.queryById(id);
    }

    @Override
    public SyncWatermarkEntity getByTaskIdAndWatermarkKey(Long taskId, String watermarkKey) {
        requireId(taskId);
        return syncWatermarkDao.queryByTaskIdAndWatermarkKey(taskId, watermarkKey);
    }

    @Override
    public List<SyncWatermarkEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncWatermarkDao.listByTaskId(taskId);
    }
}
