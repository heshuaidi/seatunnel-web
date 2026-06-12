package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.common.enums.SyncPublishStatus;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;

import java.util.List;

public interface SyncTaskVersionDao extends IDao<SyncTaskVersionEntity> {

    List<SyncTaskVersionEntity> listByTaskId(Long taskId);

    SyncTaskVersionEntity queryLatestByTaskId(Long taskId);

    boolean updateStatus(Long id, SyncPublishStatus publishStatus);
}
