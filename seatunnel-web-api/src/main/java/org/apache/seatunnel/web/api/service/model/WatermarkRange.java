package org.apache.seatunnel.web.api.service.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WatermarkRange {

    private String watermarkKey;

    private String startValue;

    private String endValue;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String valueType;

    private boolean backfill;

    private boolean advanceWatermark;
}
