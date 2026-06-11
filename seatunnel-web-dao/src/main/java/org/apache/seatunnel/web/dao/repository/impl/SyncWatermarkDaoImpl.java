package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.dao.mapper.SyncWatermarkMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncWatermarkDao;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class SyncWatermarkDaoImpl
        extends BaseDao<SyncWatermarkEntity, SyncWatermarkMapper>
        implements SyncWatermarkDao {

    @Resource
    private SyncWatermarkMapper syncWatermarkMapper;

    public SyncWatermarkDaoImpl(@NonNull SyncWatermarkMapper syncWatermarkMapper) {
        super(syncWatermarkMapper);
    }

    @Override
    public SyncWatermarkEntity queryByTaskIdAndWatermarkKey(Long taskId, String watermarkKey) {
        if (taskId == null || isBlank(watermarkKey)) {
            return null;
        }
        return syncWatermarkMapper.selectOne(
                new LambdaQueryWrapper<SyncWatermarkEntity>()
                        .eq(SyncWatermarkEntity::getTaskId, taskId)
                        .eq(SyncWatermarkEntity::getWatermarkKey, watermarkKey)
                        .last("limit 1")
        );
    }

    @Override
    public List<SyncWatermarkEntity> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return syncWatermarkMapper.selectList(
                new LambdaQueryWrapper<SyncWatermarkEntity>()
                        .eq(SyncWatermarkEntity::getTaskId, taskId)
                        .orderByAsc(SyncWatermarkEntity::getWatermarkKey)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
