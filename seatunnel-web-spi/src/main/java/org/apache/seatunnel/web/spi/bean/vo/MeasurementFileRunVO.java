package org.apache.seatunnel.web.spi.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.apache.seatunnel.web.common.enums.MeasurementRunPhase;
import org.apache.seatunnel.web.common.enums.MeasurementRunStatus;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;

import java.util.Date;

@Data
public class MeasurementFileRunVO {

    private Long id;

    private String runId;

    private String batchId;

    private Long taskId;

    private SyncTriggerType triggerType;

    private MeasurementRunStatus status;

    private MeasurementRunPhase runPhase;

    private Long sourceDatasourceId;

    private Integer scannedCount;

    private Integer discoveredCount;

    private Integer skippedCount;

    private Integer failedCount;

    private Integer selectedFileCount;

    private Integer parsedFileCount;

    private Integer loadedFileCount;

    private Integer parseFailedCount;

    private Integer loadFailedCount;

    private Long parsedRowCount;

    private Long loadedRowCount;

    private String stagingDir;

    private Long targetDatasourceId;

    private String targetDatabase;

    private String targetTable;

    private String generatedHocon;

    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
