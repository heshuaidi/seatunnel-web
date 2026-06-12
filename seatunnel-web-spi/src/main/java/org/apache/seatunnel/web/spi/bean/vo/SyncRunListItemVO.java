package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class SyncRunListItemVO {

    private String runId;

    private String batchId;

    private String taskCode;

    private String status;

    private String seatunnelJobId;

    private String submitTime;

    private String startTime;

    private String endTime;

    private Long sourceCount;

    private Long sinkCount;

    private Long errorCount;

    private String errorMessage;
}
