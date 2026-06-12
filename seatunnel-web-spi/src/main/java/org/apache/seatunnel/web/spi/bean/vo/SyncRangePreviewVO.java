package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class SyncRangePreviewVO {

    private String taskCode;

    private String strategy;

    private String watermarkKey;

    private String currentWatermark;

    private String startValue;

    private String endValue;

    private String startTime;

    private String endTime;

    private Boolean lookbackApplied;

    private Boolean maxBatchSecondsApplied;

    private Boolean willAdvanceWatermark;

    private List<String> warnings;
}
