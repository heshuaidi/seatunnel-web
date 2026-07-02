package org.apache.seatunnel.web.api.dolphinscheduler;

import lombok.Data;

import java.util.Map;

@Data
public class DolphinSchedulerSyncRunRequest {

    private String triggerType;

    private String runMode;

    private String version;

    private String bizDate;

    private String idempotencyKey;

    private Map<String, Object> params;
}
