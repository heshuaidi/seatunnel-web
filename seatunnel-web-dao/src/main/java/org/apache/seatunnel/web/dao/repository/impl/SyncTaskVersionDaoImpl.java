package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.common.enums.SyncPublishStatus;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.mapper.SyncTaskVersionMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskVersionDao;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class SyncTaskVersionDaoImpl
        extends BaseDao<SyncTaskVersionEntity, SyncTaskVersionMapper>
        implements SyncTaskVersionDao {

    @Resource
    private SyncTaskVersionMapper syncTaskVersionMapper;

    public SyncTaskVersionDaoImpl(@NonNull SyncTaskVersionMapper syncTaskVersionMapper) {
        super(syncTaskVersionMapper);
    }

    @Override
    public List<SyncTaskVersionEntity> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return syncTaskVersionMapper.selectList(
                new LambdaQueryWrapper<SyncTaskVersionEntity>()
                        .eq(SyncTaskVersionEntity::getTaskId, taskId)
                        .orderByDesc(SyncTaskVersionEntity::getVersionNo)
        );
    }

    @Override
    public SyncTaskVersionEntity queryLatestByTaskId(Long taskId) {
        if (taskId == null) {
            return null;
        }
        return syncTaskVersionMapper.selectOne(
                new LambdaQueryWrapper<SyncTaskVersionEntity>()
                        .eq(SyncTaskVersionEntity::getTaskId, taskId)
                        .orderByDesc(SyncTaskVersionEntity::getVersionNo)
                        .last("limit 1")
        );
    }

    @Override
    public boolean updateStatus(Long id, SyncPublishStatus publishStatus) {
        if (id == null || publishStatus == null) {
            return false;
        }
        SyncTaskVersionEntity entity = new SyncTaskVersionEntity();
        entity.setId(id);
        entity.setPublishStatus(publishStatus);
        return updateById(entity);
    }
}
