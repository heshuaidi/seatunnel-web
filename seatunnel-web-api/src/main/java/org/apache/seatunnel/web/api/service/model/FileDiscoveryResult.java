package org.apache.seatunnel.web.api.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDiscoveryResult {

    private Long taskId;

    private String taskCode;

    private SyncSourceType sourceType;

    private SyncIncrementalStrategy strategy;

    private String filePath;

    private String filePattern;

    private Integer discoveredCount;

    private Integer skippedCount;

    private Integer failedCount;

    @Builder.Default
    private List<SyncFileItemEntity> discoveredFiles = new ArrayList<>();

    private String message;
}
