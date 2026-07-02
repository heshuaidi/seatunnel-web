package org.apache.seatunnel.web.api.scheduler;

import org.apache.seatunnel.web.api.service.BatchJobExecutorService;
import org.apache.seatunnel.web.api.service.BatchJobInstanceService;
import org.apache.seatunnel.web.common.enums.RunMode;
import org.apache.seatunnel.web.spi.bean.vo.JobInstanceVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class SchedulerExecutorServiceImplTest {

    private BatchJobExecutorService batchJobExecutorService;
    private BatchJobInstanceService batchJobInstanceService;
    private SchedulerExecutorServiceImpl service;

    @BeforeEach
    void setUp() {
        batchJobExecutorService = Mockito.mock(BatchJobExecutorService.class);
        batchJobInstanceService = Mockito.mock(BatchJobInstanceService.class);

        SchedulerProperties properties = new SchedulerProperties();
        properties.setOperator("scheduler");
        properties.setRunSyncDefaultTimeoutSeconds(1);
        properties.setRunSyncDefaultPollIntervalSeconds(1);

        service = new SchedulerExecutorServiceImpl(
                batchJobExecutorService,
                batchJobInstanceService,
                properties);
    }

    @Test
    void runShouldCallExistingExecuteLogic() {
        SchedulerRunRequest request = new SchedulerRunRequest();
        request.setTriggeredBy("DOLPHINSCHEDULER");
        request.setExternalBatchId("ds-batch-1");

        Mockito.when(batchJobExecutorService.jobExecute(220L, RunMode.SCHEDULED, "ds-batch-1"))
                .thenReturn(330L);

        SchedulerRunResponse response = service.run(220L, request);

        Assertions.assertEquals(220L, response.getJobDefineId());
        Assertions.assertEquals(330L, response.getJobInstanceId());
        Assertions.assertEquals("SUBMITTED", response.getStatus());
        Assertions.assertEquals("DOLPHINSCHEDULER", response.getTriggeredBy());
        Mockito.verify(batchJobExecutorService)
                .jobExecute(220L, RunMode.SCHEDULED, "ds-batch-1");
    }

    @Test
    void runSyncShouldReturnFinishedWhenInstanceFinishes() {
        SchedulerRunRequest request = new SchedulerRunRequest();
        request.setTriggeredBy("DOLPHINSCHEDULER");
        request.setTimeoutSeconds(1);
        request.setPollIntervalSeconds(1);

        Mockito.when(batchJobExecutorService.jobExecute(220L, RunMode.SCHEDULED, null))
                .thenReturn(330L);
        Mockito.when(batchJobInstanceService.selectById(330L))
                .thenReturn(instance("FINISHED"));

        SchedulerRunResponse response = service.runSync(220L, request);

        Assertions.assertEquals("FINISHED", response.getStatus());
        Assertions.assertEquals(330L, response.getJobInstanceId());
    }

    @Test
    void runSyncShouldFailWhenInstanceFails() {
        SchedulerRunRequest request = new SchedulerRunRequest();
        request.setTimeoutSeconds(1);
        request.setPollIntervalSeconds(1);

        Mockito.when(batchJobExecutorService.jobExecute(220L, RunMode.SCHEDULED, null))
                .thenReturn(330L);
        Mockito.when(batchJobInstanceService.selectById(330L))
                .thenReturn(instance("FAILED"));

        SchedulerRunFailureException error = Assertions.assertThrows(
                SchedulerRunFailureException.class,
                () -> service.runSync(220L, request));

        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getHttpStatus());
        Assertions.assertEquals("FAILED", error.getResponse().getStatus());
    }

    @Test
    void runSyncShouldTimeoutWhenInstanceKeepsRunning() {
        SchedulerRunRequest request = new SchedulerRunRequest();
        request.setTimeoutSeconds(1);
        request.setPollIntervalSeconds(1);

        Mockito.when(batchJobExecutorService.jobExecute(220L, RunMode.SCHEDULED, null))
                .thenReturn(330L);
        Mockito.when(batchJobInstanceService.selectById(330L))
                .thenReturn(instance("RUNNING"));

        SchedulerRunFailureException error = Assertions.assertThrows(
                SchedulerRunFailureException.class,
                () -> service.runSync(220L, request));

        Assertions.assertEquals(HttpStatus.GATEWAY_TIMEOUT, error.getHttpStatus());
        Assertions.assertEquals("TIMEOUT", error.getResponse().getStatus());
    }

    @Test
    void runSyncShouldReturnNotImplementedWhenExecuteLogicReturnsNoInstanceId() {
        Mockito.when(batchJobExecutorService.jobExecute(220L, RunMode.SCHEDULED, null))
                .thenReturn(null);

        SchedulerRunFailureException error = Assertions.assertThrows(
                SchedulerRunFailureException.class,
                () -> service.runSync(220L, new SchedulerRunRequest()));

        Assertions.assertEquals(HttpStatus.NOT_IMPLEMENTED, error.getHttpStatus());
        Assertions.assertEquals("NOT_IMPLEMENTED", error.getResponse().getStatus());
    }

    private JobInstanceVO instance(String status) {
        JobInstanceVO instance = new JobInstanceVO();
        instance.setId(330L);
        instance.setJobStatus(status);
        return instance;
    }
}
