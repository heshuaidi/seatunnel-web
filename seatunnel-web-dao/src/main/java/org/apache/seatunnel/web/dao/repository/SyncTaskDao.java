package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;

public interface SyncTaskDao extends IDao<SyncTaskEntity> {

    SyncTaskEntity queryByTaskCode(String taskCode);

    boolean updateStatus(Long id, SyncTaskStatus status);
}
