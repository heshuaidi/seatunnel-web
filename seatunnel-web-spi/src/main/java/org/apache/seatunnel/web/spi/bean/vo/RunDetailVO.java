package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class RunDetailVO {

    private Long taskId;

    private String taskCode;

    private String runId;

    private String batchId;

    private Long taskVersionId;

    private String triggerType;

    private String runStatus;

    private String batchStatus;

    private String seatunnelJobId;

    private String seatunnelJobName;

    private String errorMessage;

    private String generatedHocon;

    private Long sourceCount;

    private Long sinkCount;

    private Long errorCount;

    private String createTime;

    private String updateTime;

    private String submitTime;

    private String startTime;

    private String endTime;

    private List<SyncAuditItemVO> audits;
}
