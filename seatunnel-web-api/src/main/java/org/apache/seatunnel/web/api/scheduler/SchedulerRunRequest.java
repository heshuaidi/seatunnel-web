package org.apache.seatunnel.web.api.scheduler;

import lombok.Data;

@Data
public class SchedulerRunRequest {

    private String triggeredBy;

    private String externalBatchId;

    private String remark;

    private Integer timeoutSeconds;

    private Integer pollIntervalSeconds;
}
