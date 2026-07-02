package org.apache.seatunnel.web.api.dolphinscheduler;

import lombok.Data;

@Data
public class DolphinSchedulerSyncRunVO {

    private String extractRunId;

    private String batchId;

    private String taskCode;

    private String status;

    private String seatunnelJobId;

    private String errorMessage;

    private String startTime;

    private String endTime;
}
