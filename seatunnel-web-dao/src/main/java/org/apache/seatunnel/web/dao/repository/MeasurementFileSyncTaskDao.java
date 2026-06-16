package org.apache.seatunnel.web.dao.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.seatunnel.web.dao.entity.MeasurementFileSyncTaskEntity;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileSyncTaskDTO;

public interface MeasurementFileSyncTaskDao extends IDao<MeasurementFileSyncTaskEntity> {

    IPage<MeasurementFileSyncTaskEntity> queryPage(MeasurementFileSyncTaskDTO dto);

    MeasurementFileSyncTaskEntity queryByTaskCode(String taskCode);

    boolean existsByTaskCode(String taskCode, Long excludeId);

    boolean updateWatermark(Long taskId, String currentWatermark);
}
