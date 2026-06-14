package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class BatchLinkUpIncrementalConfigVO {

    private Long id;

    private Long taskId;

    private Long batchLinkUpTaskId;

    private Boolean enabled;

    private String rangeType;

    private String boundaryMode;

    private String startValueSource;

    private String endValueSource;

    private String startTimeSource;

    private String endTimeSource;

    private Long boundaryDatasourceId;

    private String batchPrepareSql;

    private String batchStartValueSql;

    private String batchEndValueSql;

    private String batchStartTimeSql;

    private String batchEndTimeSql;

    private String fixedStartValue;

    private String fixedEndValue;

    private String fixedStartTime;

    private String fixedEndTime;

    private String defaultParamsJson;

    private String customContextJson;

    private Boolean successUpdateWatermark;

    private Boolean checkEnabled;

    private Long checkDatasourceId;

    private String checkSql;

    private String cleanupSql;

    private Long cleanupDatasourceId;

    private Boolean cleanupOnRerun;

    private Boolean cleanupBeforeRetryOnly;

    private String createTime;

    private String updateTime;
}
