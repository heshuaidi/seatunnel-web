package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.NonNull;
import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;
import org.apache.seatunnel.web.dao.mapper.SyncRunMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.SyncRunDao;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Repository
public class SyncRunDaoImpl extends BaseDao<SyncRunEntity, SyncRunMapper> implements SyncRunDao {

    @Resource
    private SyncRunMapper syncRunMapper;

    public SyncRunDaoImpl(@NonNull SyncRunMapper syncRunMapper) {
        super(syncRunMapper);
    }

    @Override
    public SyncRunEntity queryByRunId(String runId) {
        if (isBlank(runId)) {
            return null;
        }
        return syncRunMapper.selectOne(
                new LambdaQueryWrapper<SyncRunEntity>()
                        .eq(SyncRunEntity::getRunId, runId)
                        .last("limit 1")
        );
    }

    @Override
    public List<SyncRunEntity> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return syncRunMapper.selectList(
                new LambdaQueryWrapper<SyncRunEntity>()
                        .eq(SyncRunEntity::getTaskId, taskId)
                        .orderByDesc(SyncRunEntity::getCreateTime)
        );
    }

    @Override
    public boolean updateStatus(String runId, SyncRunStatus status, String errorMessage) {
        if (isBlank(runId) || status == null) {
            return false;
        }
        SyncRunEntity entity = new SyncRunEntity();
        entity.setStatus(status);
        entity.setErrorMessage(errorMessage);
        entity.setUpdateTime(new Date());
        if (status == SyncRunStatus.SUBMITTED) {
            entity.setSubmitTime(new Date());
        }
        if (status == SyncRunStatus.RUNNING) {
            entity.setStartTime(new Date());
        }
        if (status == SyncRunStatus.SUCCESS || status == SyncRunStatus.FAILED || status == SyncRunStatus.CANCELED) {
            entity.setEndTime(new Date());
        }
        return syncRunMapper.update(
                entity,
                new LambdaUpdateWrapper<SyncRunEntity>()
                        .eq(SyncRunEntity::getRunId, runId)
        ) > 0;
    }

    @Override
    public boolean updateGeneratedHocon(String runId, String generatedHocon) {
        if (isBlank(runId)) {
            return false;
        }
        SyncRunEntity entity = new SyncRunEntity();
        entity.setGeneratedHocon(generatedHocon);
        entity.setUpdateTime(new Date());
        return syncRunMapper.update(
                entity,
                new LambdaUpdateWrapper<SyncRunEntity>()
                        .eq(SyncRunEntity::getRunId, runId)
        ) > 0;
    }

    @Override
    public boolean updateSeatunnelJob(String runId, String seatunnelJobId, String seatunnelJobName) {
        if (isBlank(runId)) {
            return false;
        }
        SyncRunEntity entity = new SyncRunEntity();
        entity.setSeatunnelJobId(seatunnelJobId);
        entity.setSeatunnelJobName(seatunnelJobName);
        entity.setUpdateTime(new Date());
        return syncRunMapper.update(
                entity,
                new LambdaUpdateWrapper<SyncRunEntity>()
                        .eq(SyncRunEntity::getRunId, runId)
        ) > 0;
    }

    @Override
    public boolean updateMetrics(String runId, Long sourceCount, Long sinkCount, Long errorCount) {
        if (isBlank(runId)) {
            return false;
        }
        SyncRunEntity entity = new SyncRunEntity();
        entity.setSourceCount(sourceCount);
        entity.setSinkCount(sinkCount);
        entity.setErrorCount(errorCount);
        entity.setUpdateTime(new Date());
        return syncRunMapper.update(
                entity,
                new LambdaUpdateWrapper<SyncRunEntity>()
                        .eq(SyncRunEntity::getRunId, runId)
        ) > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
