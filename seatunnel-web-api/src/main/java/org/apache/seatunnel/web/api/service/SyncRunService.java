package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;

import java.util.List;

public interface SyncRunService {

    Long create(SyncRunEntity entity);

    Boolean update(SyncRunEntity entity);

    SyncRunEntity getById(Long id);

    SyncRunEntity getByRunId(String runId);

    List<SyncRunEntity> listByTaskId(Long taskId);

    Boolean updateStatus(String runId, SyncRunStatus status, String errorMessage);
}
