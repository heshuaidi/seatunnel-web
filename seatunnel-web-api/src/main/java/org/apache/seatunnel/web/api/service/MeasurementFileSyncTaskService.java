package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileSyncTaskDTO;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileSyncTaskVO;

public interface MeasurementFileSyncTaskService {

    MeasurementFileSyncTaskVO create(MeasurementFileSyncTaskDTO dto);

    MeasurementFileSyncTaskVO update(Long id, MeasurementFileSyncTaskDTO dto);

    MeasurementFileSyncTaskVO get(Long id);

    PaginationResult<MeasurementFileSyncTaskVO> page(MeasurementFileSyncTaskDTO dto);

    boolean delete(Long id);
}
