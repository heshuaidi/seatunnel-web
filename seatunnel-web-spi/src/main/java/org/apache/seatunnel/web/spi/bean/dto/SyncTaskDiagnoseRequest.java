package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SyncTaskDiagnoseRequest {

    private Map<String, Object> params;

    private Boolean includeHoconPreview;

    private Boolean includeCheckPreview;

    private Boolean includeDatasourceCheck;
}
