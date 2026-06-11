package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncIncrementalConfigService;
import org.apache.seatunnel.web.common.constants.SyncConstants;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.repository.SyncIncrementalConfigDao;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class SyncIncrementalConfigServiceImpl
        extends SyncServiceSupport
        implements SyncIncrementalConfigService {

    @Resource
    private SyncIncrementalConfigDao syncIncrementalConfigDao;

    @Override
    public Long create(SyncIncrementalConfigEntity entity) {
        requireEntity(entity, "syncIncrementalConfig");
        Date now = now();
        if (isBlank(entity.getWatermarkKey())) {
            entity.setWatermarkKey(SyncConstants.DEFAULT_WATERMARK_KEY);
        }
        if (entity.getLookbackSeconds() == null) {
            entity.setLookbackSeconds(0);
        }
        if (entity.getFileRecursive() == null) {
            entity.setFileRecursive(false);
        }
        if (entity.getBackfillAdvanceWatermark() == null) {
            entity.setBackfillAdvanceWatermark(false);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(now);
        }
        syncIncrementalConfigDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncIncrementalConfigEntity entity) {
        requireEntity(entity, "syncIncrementalConfig");
        requireId(entity.getId());
        entity.setUpdateTime(now());
        return syncIncrementalConfigDao.updateById(entity);
    }

    @Override
    public SyncIncrementalConfigEntity getById(Long id) {
        requireId(id);
        return syncIncrementalConfigDao.queryById(id);
    }

    @Override
    public SyncIncrementalConfigEntity getByTaskId(Long taskId) {
        requireId(taskId);
        return syncIncrementalConfigDao.queryByTaskId(taskId);
    }

    @Override
    public SyncIncrementalConfigEntity getByTaskIdAndWatermarkKey(Long taskId, String watermarkKey) {
        requireId(taskId);
        return syncIncrementalConfigDao.queryByTaskIdAndWatermarkKey(taskId, watermarkKey);
    }

    @Override
    public List<SyncIncrementalConfigEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncIncrementalConfigDao.listByTaskId(taskId);
    }
}
