package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;
import org.apache.seatunnel.web.common.enums.MeasurementRunStatus;

import java.util.ArrayList;
import java.util.List;

@Data
public class MeasurementFileScanResultVO {

    private Long taskId;

    private String runId;

    private String batchId;

    private MeasurementRunStatus status;

    private Integer scannedCount;

    private Integer discoveredCount;

    private Integer skippedCount;

    private Integer failedCount;

    private String errorMessage;

    private List<MeasurementFileVO> files = new ArrayList<>();
}
