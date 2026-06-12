package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class SyncFileItemVO {

    private Long id;

    private Long taskId;

    private String taskCode;

    private String batchId;

    private String runId;

    private String sourceType;

    private String fileSystem;

    private String filePath;

    private String fileName;

    private String relativePath;

    private Long fileSize;

    private String lastModifiedTime;

    private String checksum;

    private String discoveredTime;

    private String status;

    private String errorMessage;

    private String createTime;

    private String updateTime;
}
