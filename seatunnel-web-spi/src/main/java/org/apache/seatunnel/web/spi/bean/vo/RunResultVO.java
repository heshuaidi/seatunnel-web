package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class RunResultVO {

    private String runId;

    private String batchId;

    private Long taskId;

    private String taskCode;

    private Long taskVersionId;

    private String seatunnelJobId;

    private String seatunnelJobName;

    private String jobStatus;

    private String status;

    private String runStatus;

    private String batchStatus;

    private Long sourceCount;

    private Long sinkCount;

    private Long errorCount;

    private Boolean watermarkUpdated;

    private Boolean watermarkAdvanced;

    private String watermarkValue;

    private String errorMessage;

    private Boolean targetMayHaveWritten;

    private Boolean retryRequiresCleanup;

    private String cleanupHint;
}
