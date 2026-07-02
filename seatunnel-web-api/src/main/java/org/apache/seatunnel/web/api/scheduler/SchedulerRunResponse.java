package org.apache.seatunnel.web.api.scheduler;

import lombok.Data;

@Data
public class SchedulerRunResponse {

    private Long jobDefineId;

    private Long jobInstanceId;

    private String status;

    private String triggeredBy;

    private String externalBatchId;

    private String operator;

    private String remark;

    private String message;
}
