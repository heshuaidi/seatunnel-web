package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;
import org.apache.seatunnel.web.common.enums.MeasurementRunStatus;

import java.util.ArrayList;
import java.util.List;

@Data
public class MeasurementParseLoadResultVO {

    private Long taskId;

    private String runId;

    private String batchId;

    private String runPhase;

    private MeasurementRunStatus status;

    private Integer selectedFileCount = 0;

    private Integer parsedFileCount = 0;

    private Integer loadedFileCount = 0;

    private Integer parseFailedCount = 0;

    private Integer loadFailedCount = 0;

    private Integer skippedCount = 0;

    private Long parsedRowCount = 0L;

    private Long loadedRowCount = 0L;

    private String errorMessage;

    private String generatedHocon;

    private List<MeasurementFileVO> files = new ArrayList<>();
}
