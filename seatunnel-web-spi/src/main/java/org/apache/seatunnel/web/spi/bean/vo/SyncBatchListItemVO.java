package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class SyncBatchListItemVO {

    private String batchId;

    private String taskCode;

    private String status;

    private String batchStartValue;

    private String batchEndValue;

    private String batchStartTime;

    private String batchEndTime;

    private Long sourceCount;

    private Long sinkCount;

    private Long errorCount;

    private String errorMessage;

    private String createTime;

    private String updateTime;
}
