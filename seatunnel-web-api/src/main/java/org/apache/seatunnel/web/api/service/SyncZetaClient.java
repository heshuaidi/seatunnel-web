package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.api.service.model.SyncJobStatusResult;
import org.apache.seatunnel.web.api.service.model.SyncSubmitJobResult;

public interface SyncZetaClient {

    SyncSubmitJobResult submitJob(Long clientId, String jobName, String hoconText);

    SyncJobStatusResult getJobStatus(Long clientId, String jobId);

    void stopJob(Long clientId, String jobId);
}
