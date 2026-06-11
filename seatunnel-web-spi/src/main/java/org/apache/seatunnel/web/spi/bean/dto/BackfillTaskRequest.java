package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class BackfillTaskRequest {

    private String startTime;

    private String endTime;

    private String startValue;

    private String endValue;

    private Boolean advanceWatermark;

    private Boolean waitForFinish;

    private Map<String, Object> params;
}
