package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.mapper.SyncIncrementalConfigMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncIncrementalConfigDao;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class SyncIncrementalConfigDaoImpl
        extends BaseDao<SyncIncrementalConfigEntity, SyncIncrementalConfigMapper>
        implements SyncIncrementalConfigDao {

    @Resource
    private SyncIncrementalConfigMapper syncIncrementalConfigMapper;

    public SyncIncrementalConfigDaoImpl(@NonNull SyncIncrementalConfigMapper syncIncrementalConfigMapper) {
        super(syncIncrementalConfigMapper);
    }

    @Override
    public SyncIncrementalConfigEntity queryByTaskId(Long taskId) {
        if (taskId == null) {
            return null;
        }
        return syncIncrementalConfigMapper.selectOne(
                new LambdaQueryWrapper<SyncIncrementalConfigEntity>()
                        .eq(SyncIncrementalConfigEntity::getTaskId, taskId)
                        .orderByDesc(SyncIncrementalConfigEntity::getCreateTime)
                        .last("limit 1")
        );
    }

    @Override
    public SyncIncrementalConfigEntity queryByBatchLinkUpTaskId(Long batchLinkUpTaskId) {
        if (batchLinkUpTaskId == null) {
            return null;
        }
        return syncIncrementalConfigMapper.selectOne(
                new LambdaQueryWrapper<SyncIncrementalConfigEntity>()
                        .eq(SyncIncrementalConfigEntity::getBatchLinkUpTaskId, batchLinkUpTaskId)
                        .orderByDesc(SyncIncrementalConfigEntity::getCreateTime)
                        .last("limit 1")
        );
    }

    @Override
    public SyncIncrementalConfigEntity queryByTaskIdAndWatermarkKey(Long taskId, String watermarkKey) {
        if (taskId == null || isBlank(watermarkKey)) {
            return null;
        }
        return syncIncrementalConfigMapper.selectOne(
                new LambdaQueryWrapper<SyncIncrementalConfigEntity>()
                        .eq(SyncIncrementalConfigEntity::getTaskId, taskId)
                        .eq(SyncIncrementalConfigEntity::getWatermarkKey, watermarkKey)
                        .last("limit 1")
        );
    }

    @Override
    public List<SyncIncrementalConfigEntity> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return syncIncrementalConfigMapper.selectList(
                new LambdaQueryWrapper<SyncIncrementalConfigEntity>()
                        .eq(SyncIncrementalConfigEntity::getTaskId, taskId)
                        .orderByDesc(SyncIncrementalConfigEntity::getCreateTime)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
