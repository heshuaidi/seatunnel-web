package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileQueryDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileRunQueryDTO;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileRunVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileScanResultVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileVO;

public interface MeasurementFileDiscoveryService {

    MeasurementFileScanResultVO testScan(Long taskId);

    MeasurementFileScanResultVO discover(Long taskId, SyncTriggerType triggerType);

    PaginationResult<MeasurementFileRunVO> runPage(MeasurementFileRunQueryDTO dto);

    PaginationResult<MeasurementFileVO> filePage(MeasurementFileQueryDTO dto);
}
