package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class WatermarkVO {

    private Long id;

    private Long taskId;

    private String taskCode;

    private String watermarkKey;

    private String currentValue;

    private String previousValue;

    private String currentValueType;

    private Long lastSuccessRunId;

    private String lastSuccessBatchId;

    private String updateTime;
}
