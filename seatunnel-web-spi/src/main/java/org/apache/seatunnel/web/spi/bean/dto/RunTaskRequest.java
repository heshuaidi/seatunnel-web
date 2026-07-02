package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RunTaskRequest {

    private String triggerType;

    private String runMode;

    private String version;

    private String bizDate;

    private String schedulerRunId;

    private String idempotencyKey;

    private Map<String, Object> params;

    private Boolean waitForFinish;
}
