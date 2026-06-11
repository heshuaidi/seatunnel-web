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

    private String runStatus;

    private String batchStatus;

    private Boolean watermarkAdvanced;

    private String errorMessage;
}
