package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;

import java.util.List;

public interface SyncIncrementalConfigService {

    Long create(SyncIncrementalConfigEntity entity);

    Boolean update(SyncIncrementalConfigEntity entity);

    SyncIncrementalConfigEntity getById(Long id);

    SyncIncrementalConfigEntity getByTaskId(Long taskId);

    SyncIncrementalConfigEntity getByTaskIdAndWatermarkKey(Long taskId, String watermarkKey);

    List<SyncIncrementalConfigEntity> listByTaskId(Long taskId);
}
