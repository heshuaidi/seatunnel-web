package org.apache.seatunnel.web.spi.bean.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.Map;

@Data
public class BatchLinkUpIncrementalSqlTestRequest {

    @JsonAlias("datasource_id")
    private Long datasourceId;

    private String sql;

    @JsonAlias("sql_type")
    private String sqlType;

    @JsonAlias("field_name")
    private String fieldName;

    private Map<String, Object> params;

    private Boolean scalar;
}
