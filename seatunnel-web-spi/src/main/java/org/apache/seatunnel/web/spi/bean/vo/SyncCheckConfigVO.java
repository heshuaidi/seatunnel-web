package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class SyncCheckConfigVO {

    private Long id;

    private Long taskId;

    private String taskCode;

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

    private String createTime;

    private String updateTime;
}
