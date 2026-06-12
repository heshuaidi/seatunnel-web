package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SyncRangePreviewRequest {

    private String runMode;

    private Map<String, Object> params;
}
