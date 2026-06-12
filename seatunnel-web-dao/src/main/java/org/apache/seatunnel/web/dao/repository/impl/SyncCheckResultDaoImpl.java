package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity;
import org.apache.seatunnel.web.dao.mapper.SyncCheckResultMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncCheckResultDao;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class SyncCheckResultDaoImpl
        extends BaseDao<SyncCheckResultEntity, SyncCheckResultMapper>
        implements SyncCheckResultDao {

    @Resource
    private SyncCheckResultMapper syncCheckResultMapper;

    public SyncCheckResultDaoImpl(@NonNull SyncCheckResultMapper syncCheckResultMapper) {
        super(syncCheckResultMapper);
    }

    @Override
    public List<SyncCheckResultEntity> listByRunId(String runId) {
        if (isBlank(runId)) {
            return Collections.emptyList();
        }
        return syncCheckResultMapper.selectList(
                new LambdaQueryWrapper<SyncCheckResultEntity>()
                        .eq(SyncCheckResultEntity::getRunId, runId)
                        .orderByAsc(SyncCheckResultEntity::getId)
        );
    }

    @Override
    public List<SyncCheckResultEntity> listByBatchId(String batchId) {
        if (isBlank(batchId)) {
            return Collections.emptyList();
        }
        return syncCheckResultMapper.selectList(
                new LambdaQueryWrapper<SyncCheckResultEntity>()
                        .eq(SyncCheckResultEntity::getBatchId, batchId)
                        .orderByAsc(SyncCheckResultEntity::getId)
        );
    }

    @Override
    public boolean hasFailedBlockingCheck(String runId) {
        if (isBlank(runId)) {
            return false;
        }
        Long count = syncCheckResultMapper.selectCount(
                new LambdaQueryWrapper<SyncCheckResultEntity>()
                        .eq(SyncCheckResultEntity::getRunId, runId)
                        .eq(SyncCheckResultEntity::getPassed, false)
                        .eq(SyncCheckResultEntity::getFailOnMismatch, true)
        );
        return count != null && count > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
