package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;

public interface SyncTaskService {

    Long create(SyncTaskEntity entity);

    Boolean update(SyncTaskEntity entity);

    SyncTaskEntity getById(Long id);

    SyncTaskEntity getByTaskCode(String taskCode);

    Boolean updateStatus(Long id, SyncTaskStatus status);
}
