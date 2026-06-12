package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    public List<SyncFileItemEntity> listByTaskId(
            Long taskId,
            SyncFileItemStatus status,
            String batchId,
            String runId,
            String fileName,
            String filePath
    ) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SyncFileItemEntity> wrapper = new LambdaQueryWrapper<SyncFileItemEntity>()
                .eq(SyncFileItemEntity::getTaskId, taskId);
        if (status != null) {
            wrapper.eq(SyncFileItemEntity::getStatus, status);
        }
        if (!isBlank(batchId)) {
            wrapper.eq(SyncFileItemEntity::getBatchId, batchId);
        }
        if (!isBlank(runId)) {
            wrapper.eq(SyncFileItemEntity::getRunId, runId);
        }
        if (!isBlank(fileName)) {
            wrapper.like(SyncFileItemEntity::getFileName, fileName.trim());
        }
        if (!isBlank(filePath)) {
            wrapper.like(SyncFileItemEntity::getFilePath, filePath.trim());
        }
        return syncFileItemMapper.selectList(wrapper.orderByDesc(SyncFileItemEntity::getDiscoveredTime));
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
    public List<SyncFileItemEntity> listClaimableByTaskId(Long taskId, int limit) {
        if (taskId == null || limit <= 0) {
            return Collections.emptyList();
        }
        return syncFileItemMapper.selectList(
                new LambdaQueryWrapper<SyncFileItemEntity>()
                        .eq(SyncFileItemEntity::getTaskId, taskId)
                        .in(SyncFileItemEntity::getStatus,
                                SyncFileItemStatus.DISCOVERED,
                                SyncFileItemStatus.FAILED)
                        .orderByAsc(SyncFileItemEntity::getDiscoveredTime)
                        .last("limit " + limit)
        );
    }

    @Override
    public SyncFileItemEntity findByMtimeIdentity(Long taskId, String filePath, Date lastModifiedTime) {
        if (taskId == null || isBlank(filePath) || lastModifiedTime == null) {
            return null;
        }
        return syncFileItemMapper.selectOne(
                new LambdaQueryWrapper<SyncFileItemEntity>()
                        .eq(SyncFileItemEntity::getTaskId, taskId)
                        .eq(SyncFileItemEntity::getFilePath, filePath)
                        .eq(SyncFileItemEntity::getLastModifiedTime, lastModifiedTime)
                        .last("limit 1")
        );
    }

    @Override
    public SyncFileItemEntity findByPathMtimeSizeIdentity(
            Long taskId,
            String filePath,
            Long fileSize,
            Date lastModifiedTime
    ) {
        if (taskId == null || isBlank(filePath) || fileSize == null || lastModifiedTime == null) {
            return null;
        }
        return syncFileItemMapper.selectOne(
                new LambdaQueryWrapper<SyncFileItemEntity>()
                        .eq(SyncFileItemEntity::getTaskId, taskId)
                        .eq(SyncFileItemEntity::getFilePath, filePath)
                        .eq(SyncFileItemEntity::getFileSize, fileSize)
                        .eq(SyncFileItemEntity::getLastModifiedTime, lastModifiedTime)
                        .last("limit 1")
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

    @Override
    public boolean claimFile(Long id, String batchId) {
        if (id == null || isBlank(batchId)) {
            return false;
        }
        return syncFileItemMapper.update(
                null,
                new LambdaUpdateWrapper<SyncFileItemEntity>()
                        .set(SyncFileItemEntity::getBatchId, batchId)
                        .set(SyncFileItemEntity::getRunId, null)
                        .set(SyncFileItemEntity::getStatus, SyncFileItemStatus.CLAIMED)
                        .set(SyncFileItemEntity::getErrorMessage, null)
                        .set(SyncFileItemEntity::getUpdateTime, new Date())
                        .eq(SyncFileItemEntity::getId, id)
                        .in(SyncFileItemEntity::getStatus,
                                SyncFileItemStatus.DISCOVERED,
                                SyncFileItemStatus.FAILED)
        ) > 0;
    }

    @Override
    public int markBatchProcessing(String batchId, String runId) {
        if (isBlank(batchId) || isBlank(runId)) {
            return 0;
        }
        return syncFileItemMapper.update(
                null,
                new LambdaUpdateWrapper<SyncFileItemEntity>()
                        .set(SyncFileItemEntity::getRunId, runId)
                        .set(SyncFileItemEntity::getStatus, SyncFileItemStatus.PROCESSING)
                        .set(SyncFileItemEntity::getErrorMessage, null)
                        .set(SyncFileItemEntity::getUpdateTime, new Date())
                        .eq(SyncFileItemEntity::getBatchId, batchId)
                        .eq(SyncFileItemEntity::getStatus, SyncFileItemStatus.CLAIMED)
        );
    }

    @Override
    public int markBatchSuccess(String batchId, String runId) {
        if (isBlank(batchId)) {
            return 0;
        }
        LambdaUpdateWrapper<SyncFileItemEntity> wrapper = new LambdaUpdateWrapper<SyncFileItemEntity>()
                .set(SyncFileItemEntity::getStatus, SyncFileItemStatus.SUCCESS)
                .set(SyncFileItemEntity::getErrorMessage, null)
                .set(SyncFileItemEntity::getUpdateTime, new Date())
                .eq(SyncFileItemEntity::getBatchId, batchId)
                .in(SyncFileItemEntity::getStatus,
                        SyncFileItemStatus.CLAIMED,
                        SyncFileItemStatus.PROCESSING);
        if (!isBlank(runId)) {
            wrapper.set(SyncFileItemEntity::getRunId, runId);
        }
        return syncFileItemMapper.update(null, wrapper);
    }

    @Override
    public int markBatchFailed(String batchId, String runId, String errorMessage) {
        if (isBlank(batchId)) {
            return 0;
        }
        LambdaUpdateWrapper<SyncFileItemEntity> wrapper = new LambdaUpdateWrapper<SyncFileItemEntity>()
                .set(SyncFileItemEntity::getStatus, SyncFileItemStatus.FAILED)
                .set(SyncFileItemEntity::getErrorMessage, errorMessage)
                .set(SyncFileItemEntity::getUpdateTime, new Date())
                .eq(SyncFileItemEntity::getBatchId, batchId)
                .in(SyncFileItemEntity::getStatus,
                        SyncFileItemStatus.CLAIMED,
                        SyncFileItemStatus.PROCESSING);
        if (!isBlank(runId)) {
            wrapper.set(SyncFileItemEntity::getRunId, runId);
        }
        return syncFileItemMapper.update(null, wrapper);
    }

    @Override
    public int retryFailedByBatchId(String batchId) {
        if (isBlank(batchId)) {
            return 0;
        }
        return syncFileItemMapper.update(
                null,
                new LambdaUpdateWrapper<SyncFileItemEntity>()
                        .set(SyncFileItemEntity::getStatus, SyncFileItemStatus.CLAIMED)
                        .set(SyncFileItemEntity::getRunId, null)
                        .set(SyncFileItemEntity::getErrorMessage, null)
                        .set(SyncFileItemEntity::getUpdateTime, new Date())
                        .eq(SyncFileItemEntity::getBatchId, batchId)
                        .eq(SyncFileItemEntity::getStatus, SyncFileItemStatus.FAILED)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
