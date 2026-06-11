package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;

import java.util.List;

public interface SyncWatermarkService {

    Long create(SyncWatermarkEntity entity);

    Boolean update(SyncWatermarkEntity entity);

    SyncWatermarkEntity getById(Long id);

    SyncWatermarkEntity getByTaskIdAndWatermarkKey(Long taskId, String watermarkKey);

    List<SyncWatermarkEntity> listByTaskId(Long taskId);
}
