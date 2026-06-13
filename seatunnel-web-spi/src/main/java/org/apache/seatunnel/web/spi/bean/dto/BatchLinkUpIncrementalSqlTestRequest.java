package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class BatchLinkUpIncrementalSqlTestRequest {

    private Long datasourceId;

    private String sql;

    private Map<String, Object> params;

    private Boolean scalar;
}
