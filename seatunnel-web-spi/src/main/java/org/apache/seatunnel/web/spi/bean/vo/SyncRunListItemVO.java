package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class SyncRunListItemVO {

    private Long id;

    private String runId;

    private String batchId;

    private Long taskId;

    private String taskCode;

    private String runType;

    private String triggerType;

    private String schedulerRunId;

    private String status;

    private String batchStatus;

    private String seatunnelJobId;

    private String seatunnelJobName;

    private String batchStartValue;

    private String batchEndValue;

    private String batchStartTime;

    private String batchEndTime;

    private String submitTime;

    private String startTime;

    private String endTime;

    private String createTime;

    private String updateTime;

    private Long sourceCount;

    private Long sinkCount;

    private Long errorCount;

    private String errorMessage;

    private String generatedHocon;

    private Boolean targetMayHaveWritten;

    private Boolean retryRequiresCleanup;

    private String cleanupHint;
}
