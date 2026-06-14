package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;

import java.util.List;

public interface SyncIncrementalConfigDao extends IDao<SyncIncrementalConfigEntity> {

    SyncIncrementalConfigEntity queryByTaskId(Long taskId);

    SyncIncrementalConfigEntity queryByBatchLinkUpTaskId(Long batchLinkUpTaskId);

    SyncIncrementalConfigEntity queryByTaskIdAndWatermarkKey(Long taskId, String watermarkKey);

    List<SyncIncrementalConfigEntity> listByTaskId(Long taskId);
}
