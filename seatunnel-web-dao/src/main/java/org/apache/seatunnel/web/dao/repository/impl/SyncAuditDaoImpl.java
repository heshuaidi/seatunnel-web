package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.dao.entity.SyncAuditEntity;
import org.apache.seatunnel.web.dao.mapper.SyncAuditMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncAuditDao;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class SyncAuditDaoImpl extends BaseDao<SyncAuditEntity, SyncAuditMapper> implements SyncAuditDao {

    @Resource
    private SyncAuditMapper syncAuditMapper;

    public SyncAuditDaoImpl(@NonNull SyncAuditMapper syncAuditMapper) {
        super(syncAuditMapper);
    }

    @Override
    public List<SyncAuditEntity> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return syncAuditMapper.selectList(
                new LambdaQueryWrapper<SyncAuditEntity>()
                        .eq(SyncAuditEntity::getTaskId, taskId)
                        .orderByDesc(SyncAuditEntity::getCreateTime)
        );
    }

    @Override
    public List<SyncAuditEntity> listByRunId(String runId) {
        if (isBlank(runId)) {
            return Collections.emptyList();
        }
        return syncAuditMapper.selectList(
                new LambdaQueryWrapper<SyncAuditEntity>()
                        .eq(SyncAuditEntity::getRunId, runId)
                        .orderByDesc(SyncAuditEntity::getCreateTime)
        );
    }

    @Override
    public List<SyncAuditEntity> listByBatchId(String batchId) {
        if (isBlank(batchId)) {
            return Collections.emptyList();
        }
        return syncAuditMapper.selectList(
                new LambdaQueryWrapper<SyncAuditEntity>()
                        .eq(SyncAuditEntity::getBatchId, batchId)
                        .orderByDesc(SyncAuditEntity::getCreateTime)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
