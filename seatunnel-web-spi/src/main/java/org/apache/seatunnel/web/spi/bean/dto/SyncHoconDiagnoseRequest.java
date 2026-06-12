package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SyncHoconDiagnoseRequest {

    private Map<String, Object> params;

    private Boolean includeRenderedHocon;
}
