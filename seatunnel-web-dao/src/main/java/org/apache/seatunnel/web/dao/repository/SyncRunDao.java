package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;

import java.util.List;

public interface SyncRunDao extends IDao<SyncRunEntity> {

    SyncRunEntity queryByRunId(String runId);

    List<SyncRunEntity> listByTaskId(Long taskId);

    boolean updateStatus(String runId, SyncRunStatus status, String errorMessage);
}
