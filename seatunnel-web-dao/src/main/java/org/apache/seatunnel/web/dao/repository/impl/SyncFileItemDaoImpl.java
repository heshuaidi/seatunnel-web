package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;
import org.apache.seatunnel.web.dao.mapper.SyncFileItemMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncFileItemDao;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Repository
public class SyncFileItemDaoImpl
        extends BaseDao<SyncFileItemEntity, SyncFileItemMapper>
        implements SyncFileItemDao {

    @Resource
    private SyncFileItemMapper syncFileItemMapper;

    public SyncFileItemDaoImpl(@NonNull SyncFileItemMapper syncFileItemMapper) {
        super(syncFileItemMapper);
    }

    @Override
    public List<SyncFileItemEntity> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return syncFileItemMapper.selectList(
                new LambdaQueryWrapper<SyncFileItemEntity>()
                        .eq(SyncFileItemEntity::getTaskId, taskId)
                        .orderByDesc(SyncFileItemEntity::getDiscoveredTime)
        );
    }

    @Override
    public List<SyncFileItemEntity> listByBatchId(String batchId) {
        if (isBlank(batchId)) {
            return Collections.emptyList();
        }
        return syncFileItemMapper.selectList(
                new LambdaQueryWrapper<SyncFileItemEntity>()
                        .eq(SyncFileItemEntity::getBatchId, batchId)
                        .orderByDesc(SyncFileItemEntity::getDiscoveredTime)
        );
    }

    @Override
    public boolean updateStatus(Long id, SyncFileItemStatus status, String errorMessage) {
        if (id == null || status == null) {
            return false;
        }
        SyncFileItemEntity entity = new SyncFileItemEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setErrorMessage(errorMessage);
        entity.setUpdateTime(new Date());
        return updateById(entity);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
