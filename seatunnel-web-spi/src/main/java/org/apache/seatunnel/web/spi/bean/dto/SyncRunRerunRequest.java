package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SyncRunRerunRequest {

    private String mode;

    private Boolean waitForFinish;

    private Map<String, Object> params;
}
