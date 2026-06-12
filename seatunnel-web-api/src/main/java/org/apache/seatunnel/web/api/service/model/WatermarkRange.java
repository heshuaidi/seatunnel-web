package org.apache.seatunnel.web.api.service.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WatermarkRange {

    private String watermarkKey;

    private String startValue;

    private String endValue;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String valueType;

    private String currentWatermark;

    private Boolean lookbackApplied;

    private Boolean maxBatchSecondsApplied;

    private List<String> warnings;

    private boolean backfill;

    private boolean advanceWatermark;
}
