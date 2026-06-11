package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class SyncFileRetryResultVO {

    private String batchId;

    private Integer retryCount;

    private List<SyncFileItemVO> files;
}
