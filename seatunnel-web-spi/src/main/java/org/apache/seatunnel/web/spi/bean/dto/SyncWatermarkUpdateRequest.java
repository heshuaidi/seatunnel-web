package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

@Data
public class SyncWatermarkUpdateRequest {

    private String watermarkKey;

    private String currentValue;

    private String reason;
}
