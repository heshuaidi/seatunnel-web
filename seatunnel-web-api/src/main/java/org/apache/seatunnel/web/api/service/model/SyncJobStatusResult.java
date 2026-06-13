package org.apache.seatunnel.web.api.service.model;

import lombok.Data;

import java.util.Map;

@Data
public class SyncJobStatusResult {

    private String jobId;

    private String status;

    private boolean endState;

    private boolean success;

    private String errorMessage;

    private Long sourceCount;

    private Long sinkCount;

    private Long errorCount;

    private Map<String, Object> rawResponse;
}
