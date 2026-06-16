package org.apache.seatunnel.web.dao.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.seatunnel.web.dao.entity.MeasurementFileRunEntity;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileRunQueryDTO;

public interface MeasurementFileRunDao extends IDao<MeasurementFileRunEntity> {

    IPage<MeasurementFileRunEntity> queryPage(MeasurementFileRunQueryDTO dto);

    MeasurementFileRunEntity queryByRunId(String runId);
}
