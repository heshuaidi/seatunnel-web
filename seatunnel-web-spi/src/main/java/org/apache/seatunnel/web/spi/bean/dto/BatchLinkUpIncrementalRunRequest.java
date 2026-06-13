package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class BatchLinkUpIncrementalRunRequest {

    private Map<String, Object> params;

    private Boolean waitForFinish;

    private String triggerType;

    private String runMode;
}
