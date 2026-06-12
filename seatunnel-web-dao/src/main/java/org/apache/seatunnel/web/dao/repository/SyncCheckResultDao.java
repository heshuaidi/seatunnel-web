package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity;

import java.util.List;

public interface SyncCheckResultDao extends IDao<SyncCheckResultEntity> {

    List<SyncCheckResultEntity> listByRunId(String runId);

    List<SyncCheckResultEntity> listByBatchId(String batchId);

    boolean hasFailedBlockingCheck(String runId);
}
