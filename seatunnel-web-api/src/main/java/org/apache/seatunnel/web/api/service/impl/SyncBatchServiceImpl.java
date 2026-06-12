package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncBatchService;
import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.repository.SyncBatchDao;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@Service
public class SyncBatchServiceImpl extends SyncServiceSupport implements SyncBatchService {

    private static final DateTimeFormatter BATCH_ID_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private SyncBatchDao syncBatchDao;

    @Override
    public Long create(SyncBatchEntity entity) {
        requireEntity(entity, "syncBatch");
        Date now = now();
        if (entity.getStatus() == null) {
            entity.setStatus(SyncBatchStatus.CREATED);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(now);
        }
        syncBatchDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncBatchEntity entity) {
        requireEntity(entity, "syncBatch");
        requireId(entity.getId());
        entity.setUpdateTime(now());
        return syncBatchDao.updateById(entity);
    }

    @Override
    public SyncBatchEntity getById(Long id) {
        requireId(id);
        return syncBatchDao.queryById(id);
    }

    @Override
    public SyncBatchEntity getByBatchId(String batchId) {
        if (isBlank(batchId)) {
            return null;
        }
        return syncBatchDao.queryByBatchId(batchId);
    }

    @Override
    public List<SyncBatchEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncBatchDao.listByTaskId(taskId);
    }

    @Override
    public Boolean updateStatus(String batchId, SyncBatchStatus status, String errorMessage) {
        return syncBatchDao.updateStatus(batchId, status, errorMessage);
    }

    @Override
    public SyncBatchEntity createBatchForRun(
            SyncTaskEntity task,
            WatermarkRange range,
            SyncTriggerType triggerType,
            SyncRunMode runMode
    ) {
        requireEntity(task, "syncTask");
        requireEntity(range, "watermarkRange");
        if (isBlank(task.getTaskCode())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskCode");
        }
        if (triggerType == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "triggerType");
        }
        if (runMode == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "runMode");
        }

        Date now = now();
        SyncBatchEntity entity = SyncBatchEntity.builder()
                .batchId(generateBatchId(task.getTaskCode()))
                .taskId(task.getId())
                .taskCode(task.getTaskCode())
                .triggerType(triggerType)
                .runMode(runMode)
                .batchStartValue(range.getStartValue())
                .batchEndValue(range.getEndValue())
                .batchStartTime(toDate(range.getStartTime()))
                .batchEndTime(toDate(range.getEndTime()))
                .status(SyncBatchStatus.CREATED)
                .createTime(now)
                .updateTime(now)
                .build();

        syncBatchDao.insert(entity);
        return entity;
    }

    @Override
    public SyncBatchEntity createFileBatchForRun(
            SyncTaskEntity task,
            SyncTriggerType triggerType,
            SyncRunMode runMode,
            Date batchStartTime,
            Date batchEndTime
    ) {
        requireRunnableBatchRequest(task, triggerType, runMode);

        Date now = now();
        SyncBatchEntity entity = SyncBatchEntity.builder()
                .batchId(generateBatchId(task.getTaskCode()))
                .taskId(task.getId())
                .taskCode(task.getTaskCode())
                .triggerType(triggerType)
                .runMode(runMode)
                .batchStartTime(batchStartTime)
                .batchEndTime(batchEndTime)
                .status(SyncBatchStatus.CREATED)
                .createTime(now)
                .updateTime(now)
                .build();

        syncBatchDao.insert(entity);
        return entity;
    }

    @Override
    public Boolean updateMetrics(String batchId, Long sourceCount, Long sinkCount, Long errorCount) {
        return syncBatchDao.updateMetrics(batchId, sourceCount, sinkCount, errorCount);
    }

    private void requireRunnableBatchRequest(
            SyncTaskEntity task,
            SyncTriggerType triggerType,
            SyncRunMode runMode
    ) {
        requireEntity(task, "syncTask");
        if (isBlank(task.getTaskCode())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskCode");
        }
        if (triggerType == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "triggerType");
        }
        if (runMode == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "runMode");
        }
    }

    private String generateBatchId(String taskCode) {
        int random = RANDOM.nextInt(1_000_000);
        return taskCode
                + "_"
                + BATCH_ID_TIME_FORMATTER.format(LocalDateTime.now())
                + "_"
                + String.format("%06d", random);
    }

    private Date toDate(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
