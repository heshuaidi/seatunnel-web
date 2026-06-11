package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

@Data
public class CreateFabLoaderPublishTaskRequest {

    private String taskCode;

    private String taskName;

    private Long clientId;

    private String description;

    private String sourceTaskCode;

    private String sourceSystem;

    private String batchIdMode;

    private String starrocksJdbcUrl;

    private String starrocksJdbcDriver;

    private String starrocksNodeUrls;

    private String starrocksBaseUrl;

    private String starrocksUsername;

    private String starrocksPassword;

    private String starrocksDatabase;

    private String xchgHeaderTable;

    private String xchgSiteTable;

    private String xchgErrorTable;

    private String stgHeaderTable;

    private String stgSiteTable;

    private String stgErrorTable;

    private Long starrocksDatasourceId;

    private Boolean enableDefaultChecks;
}
