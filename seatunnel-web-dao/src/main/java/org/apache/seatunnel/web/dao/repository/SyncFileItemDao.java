package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;

import java.util.List;

public interface SyncFileItemDao extends IDao<SyncFileItemEntity> {

    List<SyncFileItemEntity> listByTaskId(Long taskId);

    List<SyncFileItemEntity> listByBatchId(String batchId);

    boolean updateStatus(Long id, SyncFileItemStatus status, String errorMessage);
}
