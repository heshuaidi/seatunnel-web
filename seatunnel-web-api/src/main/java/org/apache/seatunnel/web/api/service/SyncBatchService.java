package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;

import java.util.List;

public interface SyncBatchService {

    Long create(SyncBatchEntity entity);

    Boolean update(SyncBatchEntity entity);

    SyncBatchEntity getById(Long id);

    SyncBatchEntity getByBatchId(String batchId);

    List<SyncBatchEntity> listByTaskId(Long taskId);

    Boolean updateStatus(String batchId, SyncBatchStatus status, String errorMessage);

    SyncBatchEntity createBatchForRun(
            SyncTaskEntity task,
            WatermarkRange range,
            SyncTriggerType triggerType,
            SyncRunMode runMode
    );
}
