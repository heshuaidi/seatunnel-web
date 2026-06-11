package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

@Data
public class SyncCheckConfigRequest {

    private String checkCode;

    private String checkName;

    private String checkType;

    private String datasourceType;

    private Long datasourceId;

    private String sqlText;

    private String expectedOperator;

    private String expectedValue;

    private String compareToCheckCode;

    private Boolean failOnMismatch;

    private Boolean enabled;

    private Integer sortOrder;

    private String description;
}
