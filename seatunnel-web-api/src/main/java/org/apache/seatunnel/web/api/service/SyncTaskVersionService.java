package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.common.enums.SyncPublishStatus;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;

import java.util.List;

public interface SyncTaskVersionService {

    Long create(SyncTaskVersionEntity entity);

    Boolean update(SyncTaskVersionEntity entity);

    SyncTaskVersionEntity getById(Long id);

    SyncTaskVersionEntity getByTaskId(Long taskId);

    List<SyncTaskVersionEntity> listByTaskId(Long taskId);

    Boolean updateStatus(Long id, SyncPublishStatus publishStatus);
}
