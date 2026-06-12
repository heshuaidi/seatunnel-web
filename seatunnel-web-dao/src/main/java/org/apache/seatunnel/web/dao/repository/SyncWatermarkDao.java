package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;

import java.util.List;

public interface SyncWatermarkDao extends IDao<SyncWatermarkEntity> {

    SyncWatermarkEntity queryByTaskIdAndWatermarkKey(Long taskId, String watermarkKey);

    List<SyncWatermarkEntity> listByTaskId(Long taskId);
}
