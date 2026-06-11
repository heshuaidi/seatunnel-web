package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;

import java.util.List;

public interface SyncBatchDao extends IDao<SyncBatchEntity> {

    SyncBatchEntity queryByBatchId(String batchId);

    List<SyncBatchEntity> listByTaskId(Long taskId);

    boolean updateStatus(String batchId, SyncBatchStatus status, String errorMessage);
}
