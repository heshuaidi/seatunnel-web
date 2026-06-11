package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;

import java.util.List;
import java.util.Map;

public interface SyncWatermarkService {

    Long create(SyncWatermarkEntity entity);

    Boolean update(SyncWatermarkEntity entity);

    SyncWatermarkEntity getById(Long id);

    SyncWatermarkEntity getByTaskIdAndWatermarkKey(Long taskId, String watermarkKey);

    List<SyncWatermarkEntity> listByTaskId(Long taskId);

    WatermarkRange calculateNextRange(Long taskId, Map<String, Object> runParams);

    WatermarkRange calculateBackfillRange(Long taskId, Map<String, Object> runParams, Boolean advanceWatermark);

    void advanceWatermark(Long taskId, String watermarkKey, String newValue, Long runId, String batchId);

    void rollbackOrKeepWatermarkOnFailure(Long taskId, String watermarkKey, Long runId, String batchId);
}
