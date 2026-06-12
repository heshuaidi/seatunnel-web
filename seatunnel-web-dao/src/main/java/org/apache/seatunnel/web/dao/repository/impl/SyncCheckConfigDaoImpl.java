package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.mapper.SyncCheckConfigMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncCheckConfigDao;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class SyncCheckConfigDaoImpl
        extends BaseDao<SyncCheckConfigEntity, SyncCheckConfigMapper>
        implements SyncCheckConfigDao {

    @Resource
    private SyncCheckConfigMapper syncCheckConfigMapper;

    public SyncCheckConfigDaoImpl(@NonNull SyncCheckConfigMapper syncCheckConfigMapper) {
        super(syncCheckConfigMapper);
    }

    @Override
    public List<SyncCheckConfigEntity> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return syncCheckConfigMapper.selectList(
                baseTaskWrapper(taskId)
        );
    }

    @Override
    public List<SyncCheckConfigEntity> listEnabledByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return syncCheckConfigMapper.selectList(
                baseTaskWrapper(taskId)
                        .eq(SyncCheckConfigEntity::getEnabled, true)
        );
    }

    @Override
    public SyncCheckConfigEntity queryByTaskIdAndCheckCode(Long taskId, String checkCode) {
        if (taskId == null || isBlank(checkCode)) {
            return null;
        }
        return syncCheckConfigMapper.selectOne(
                new LambdaQueryWrapper<SyncCheckConfigEntity>()
                        .eq(SyncCheckConfigEntity::getTaskId, taskId)
                        .eq(SyncCheckConfigEntity::getCheckCode, checkCode.trim())
                        .last("limit 1")
        );
    }

    private LambdaQueryWrapper<SyncCheckConfigEntity> baseTaskWrapper(Long taskId) {
        return new LambdaQueryWrapper<SyncCheckConfigEntity>()
                .eq(SyncCheckConfigEntity::getTaskId, taskId)
                .orderByAsc(SyncCheckConfigEntity::getSortOrder)
                .orderByAsc(SyncCheckConfigEntity::getId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
