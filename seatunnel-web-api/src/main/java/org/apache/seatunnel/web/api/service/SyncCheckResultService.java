package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity;

import java.util.List;

public interface SyncCheckResultService {

    Long insertResult(SyncCheckResultEntity entity);

    List<SyncCheckResultEntity> listByRunId(String runId);

    List<SyncCheckResultEntity> listByBatchId(String batchId);

    Boolean hasFailedBlockingCheck(String runId);
}
