package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.mapper.SyncTaskMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskDao;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public class SyncTaskDaoImpl extends BaseDao<SyncTaskEntity, SyncTaskMapper> implements SyncTaskDao {

    @Resource
    private SyncTaskMapper syncTaskMapper;

    public SyncTaskDaoImpl(@NonNull SyncTaskMapper syncTaskMapper) {
        super(syncTaskMapper);
    }

    @Override
    public SyncTaskEntity queryByTaskCode(String taskCode) {
        if (isBlank(taskCode)) {
            return null;
        }
        return syncTaskMapper.selectOne(
                new LambdaQueryWrapper<SyncTaskEntity>()
                        .eq(SyncTaskEntity::getTaskCode, taskCode)
                        .last("limit 1")
        );
    }

    @Override
    public boolean updateStatus(Long id, SyncTaskStatus status) {
        if (id == null || status == null) {
            return false;
        }
        SyncTaskEntity entity = new SyncTaskEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setUpdateTime(new Date());
        return updateById(entity);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
