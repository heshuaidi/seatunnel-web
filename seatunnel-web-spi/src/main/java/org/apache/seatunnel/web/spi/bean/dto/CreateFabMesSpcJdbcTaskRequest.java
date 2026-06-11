package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

@Data
public class CreateFabMesSpcJdbcTaskRequest {

    private String taskCode;

    private String taskName;

    private Long clientId;

    private String description;

    private String sourceType;

    private String sinkType;

    private String engineType;

    private String incrementalStrategy;

    private String watermarkField;

    private String watermarkFieldType;

    private String startValue;

    private Integer lookbackSeconds;

    private Integer maxBatchSeconds;

    private String sourceSystem;

    private String sourceJdbcUrl;

    private String sourceJdbcDriver;

    private String sourceUsername;

    private String sourcePassword;

    private String headerSourceSql;

    private String siteSourceSql;

    private String starrocksNodeUrls;

    private String starrocksBaseUrl;

    private String starrocksUsername;

    private String starrocksPassword;

    private String starrocksDatabase;

    private Long sourceDatasourceId;

    private Long sinkDatasourceId;

    private Boolean enableDefaultChecks;
}
