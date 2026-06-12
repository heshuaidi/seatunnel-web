package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class SyncCheckResultVO {

    private Long id;

    private String runId;

    private String batchId;

    private Long taskId;

    private String taskCode;

    private String checkCode;

    private String checkName;

    private String checkType;

    private String renderedSql;

    private String actualValue;

    private String expectedOperator;

    private String expectedValue;

    private String compareToCheckCode;

    private String compareToActualValue;

    private Boolean passed;

    private Boolean failOnMismatch;

    private String errorMessage;

    private String startTime;

    private String endTime;

    private String createTime;
}
