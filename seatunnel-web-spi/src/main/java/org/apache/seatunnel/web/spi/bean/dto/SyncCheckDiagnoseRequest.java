package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SyncCheckDiagnoseRequest {

    private Map<String, Object> params;

    private Boolean executeSql;
}
