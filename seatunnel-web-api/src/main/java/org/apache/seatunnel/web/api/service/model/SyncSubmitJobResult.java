package org.apache.seatunnel.web.api.service.model;

import lombok.Data;

import java.util.Map;

@Data
public class SyncSubmitJobResult {

    private String jobId;

    private String jobName;

    private Map<String, Object> rawResponse;
}
