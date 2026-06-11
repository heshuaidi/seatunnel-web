package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;

import java.util.List;

public interface SyncFileItemService {

    Long create(SyncFileItemEntity entity);

    Boolean update(SyncFileItemEntity entity);

    SyncFileItemEntity getById(Long id);

    List<SyncFileItemEntity> listByTaskId(Long taskId);

    List<SyncFileItemEntity> listByBatchId(String batchId);

    Boolean updateStatus(Long id, SyncFileItemStatus status, String errorMessage);
}
