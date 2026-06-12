package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class SyncFileDiscoveryResultVO {

    private Long taskId;

    private String taskCode;

    private String sourceType;

    private String strategy;

    private String filePath;

    private String filePattern;

    private Integer discoveredCount;

    private Integer skippedCount;

    private Integer failedCount;

    private List<SyncFileItemVO> discoveredFiles;

    private String message;
}
