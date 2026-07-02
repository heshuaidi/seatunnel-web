package org.apache.seatunnel.web.api.scheduler;

public interface SchedulerExecutorService {

    SchedulerRunResponse run(Long jobDefineId, SchedulerRunRequest request);

    SchedulerRunResponse runSync(Long jobDefineId, SchedulerRunRequest request);
}
