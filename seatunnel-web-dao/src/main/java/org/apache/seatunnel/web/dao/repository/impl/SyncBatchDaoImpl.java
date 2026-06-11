package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.mapper.SyncBatchMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncBatchDao;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Repository
public class SyncBatchDaoImpl extends BaseDao<SyncBatchEntity, SyncBatchMapper> implements SyncBatchDao {

    @Resource
    private SyncBatchMapper syncBatchMapper;

    public SyncBatchDaoImpl(@NonNull SyncBatchMapper syncBatchMapper) {
        super(syncBatchMapper);
    }

    @Override
    public SyncBatchEntity queryByBatchId(String batchId) {
        if (isBlank(batchId)) {
            return null;
        }
        return syncBatchMapper.selectOne(
                new LambdaQueryWrapper<SyncBatchEntity>()
                        .eq(SyncBatchEntity::getBatchId, batchId)
                        .last("limit 1")
        );
    }

    @Override
    public List<SyncBatchEntity> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return syncBatchMapper.selectList(
                new LambdaQueryWrapper<SyncBatchEntity>()
                        .eq(SyncBatchEntity::getTaskId, taskId)
                        .orderByDesc(SyncBatchEntity::getCreateTime)
        );
    }

    @Override
    public boolean updateStatus(String batchId, SyncBatchStatus status, String errorMessage) {
        if (isBlank(batchId) || status == null) {
            return false;
        }
        SyncBatchEntity entity = new SyncBatchEntity();
        entity.setStatus(status);
        entity.setErrorMessage(errorMessage);
        entity.setUpdateTime(new Date());
        return syncBatchMapper.update(
                entity,
                new LambdaUpdateWrapper<SyncBatchEntity>()
                        .eq(SyncBatchEntity::getBatchId, batchId)
        ) > 0;
    }

    @Override
    public boolean updateMetrics(String batchId, Long sourceCount, Long sinkCount, Long errorCount) {
        if (isBlank(batchId)) {
            return false;
        }
        SyncBatchEntity entity = new SyncBatchEntity();
        entity.setSourceCount(sourceCount);
        entity.setSinkCount(sinkCount);
        entity.setErrorCount(errorCount);
        entity.setUpdateTime(new Date());
        return syncBatchMapper.update(
                entity,
                new LambdaUpdateWrapper<SyncBatchEntity>()
                        .eq(SyncBatchEntity::getBatchId, batchId)
        ) > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
