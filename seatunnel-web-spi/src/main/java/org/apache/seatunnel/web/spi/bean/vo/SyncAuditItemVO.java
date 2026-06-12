package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class SyncAuditItemVO {

    private Long id;

    private String runId;

    private String batchId;

    private Long taskId;

    private String taskCode;

    private String eventType;

    private String eventLevel;

    private String eventMessage;

    private String detailJson;

    private String createTime;
}
