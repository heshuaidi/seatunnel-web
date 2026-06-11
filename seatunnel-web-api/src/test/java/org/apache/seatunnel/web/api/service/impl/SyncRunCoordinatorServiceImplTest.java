package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.SyncBatchService;
import org.apache.seatunnel.web.api.service.SyncRunService;
import org.apache.seatunnel.web.api.service.SyncVerifyService;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.api.service.SyncZetaClient;
import org.apache.seatunnel.web.api.service.model.SyncJobStatusResult;
import org.apache.seatunnel.web.api.service.model.SyncSubmitJobResult;
import org.apache.seatunnel.web.api.service.model.VerifyResult;
import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.common.enums.SyncSinkType;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.common.enums.SyncTaskType;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.repository.SyncIncrementalConfigDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskVersionDao;
import org.apache.seatunnel.web.spi.bean.dto.RunTaskRequest;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

class SyncRunCoordinatorServiceImplTest {

    private SyncRunCoordinatorServiceImpl service;
    private SyncWatermarkService watermarkService;
    private SyncVerifyService verifyService;
    private SyncIncrementalConfigDao configDao;
    private SyncBatchService batchService;
    private SyncZetaClient zetaClient;

    @BeforeEach
    void setUp() {
        service = new SyncRunCoordinatorServiceImpl();
        watermarkService = Mockito.mock(SyncWatermarkService.class);
        verifyService = Mockito.mock(SyncVerifyService.class);

        SyncTaskDao taskDao = Mockito.mock(SyncTaskDao.class);
        SyncTaskVersionDao versionDao = Mockito.mock(SyncTaskVersionDao.class);
        configDao = Mockito.mock(SyncIncrementalConfigDao.class);
        batchService = Mockito.mock(SyncBatchService.class);
        SyncRunService runService = Mockito.mock(SyncRunService.class);
        SyncAuditService auditService = Mockito.mock(SyncAuditService.class);
        zetaClient = Mockito.mock(SyncZetaClient.class);
        HoconRenderService hoconRenderService = new HoconRenderServiceImpl();

        Mockito.when(taskDao.queryByTaskCode("task_1")).thenReturn(task());
        Mockito.when(taskDao.queryByTaskCode("loader_task")).thenReturn(nonIncrementalTask());
        Mockito.when(versionDao.queryById(2L)).thenReturn(version());
        Mockito.when(versionDao.queryById(3L)).thenReturn(nonIncrementalVersion());
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(config());
        Mockito.when(watermarkService.calculateNextRange(Mockito.eq(1L), Mockito.anyMap())).thenReturn(range());
        Mockito.when(watermarkService.getByTaskIdAndWatermarkKey(1L, "default")).thenReturn(null);
        Mockito.when(batchService.createBatchForRun(
                Mockito.any(),
                Mockito.any(),
                Mockito.eq(SyncTriggerType.MANUAL),
                Mockito.eq(SyncRunMode.NORMAL)
        )).thenReturn(batch());
        Mockito.when(batchService.createFileBatchForRun(
                Mockito.any(),
                Mockito.eq(SyncTriggerType.MANUAL),
                Mockito.eq(SyncRunMode.NORMAL),
                Mockito.isNull(),
                Mockito.isNull()
        )).thenReturn(batch());
        Mockito.when(batchService.updateStatus(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(batchService.updateMetrics(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(true);
        Mockito.when(runService.create(Mockito.any())).thenAnswer(invocation -> {
            SyncRunEntity run = invocation.getArgument(0);
            run.setId(11L);
            return 11L;
        });
        Mockito.when(runService.updateGeneratedHocon(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
        Mockito.when(runService.updateSeatunnelJob(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(true);
        Mockito.when(runService.updateStatus(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(runService.updateMetrics(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(true);
        Mockito.when(zetaClient.submitJob(Mockito.eq(7L), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(submitResult());
        Mockito.when(zetaClient.getJobStatus(7L, "job-1")).thenReturn(successStatus());

        SyncRunProperties properties = new SyncRunProperties();
        properties.setPollIntervalMs(1L);
        properties.setPollTimeoutMs(1000L);

        ReflectionTestUtils.setField(service, "syncTaskDao", taskDao);
        ReflectionTestUtils.setField(service, "syncTaskVersionDao", versionDao);
        ReflectionTestUtils.setField(service, "syncIncrementalConfigDao", configDao);
        ReflectionTestUtils.setField(service, "syncWatermarkService", watermarkService);
        ReflectionTestUtils.setField(service, "syncBatchService", batchService);
        ReflectionTestUtils.setField(service, "syncRunService", runService);
        ReflectionTestUtils.setField(service, "syncAuditService", auditService);
        ReflectionTestUtils.setField(service, "hoconRenderService", hoconRenderService);
        ReflectionTestUtils.setField(service, "syncZetaClient", zetaClient);
        ReflectionTestUtils.setField(service, "syncVerifyService", verifyService);
        ReflectionTestUtils.setField(service, "syncRunProperties", properties);
    }

    @Test
    void verificationFailedShouldNotAdvanceWatermark() {
        VerifyResult verifyResult = new VerifyResult();
        verifyResult.setPassed(false);
        verifyResult.setHasBlockingFailure(true);
        verifyResult.setErrorMessage("sink_count mismatch");
        Mockito.when(verifyService.verifyRun(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyMap()))
                .thenReturn(verifyResult);

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.runTask("task_1", request())
        );

        Assertions.assertTrue(exception.getMessage().contains("Sync verification failed"));
        Mockito.verify(watermarkService, Mockito.never())
                .advanceWatermark(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void verificationPassedShouldAdvanceWatermark() {
        VerifyResult verifyResult = new VerifyResult();
        verifyResult.setPassed(true);
        verifyResult.setSourceCount(100L);
        verifyResult.setSinkCount(100L);
        verifyResult.setErrorCount(0L);
        Mockito.when(verifyService.verifyRun(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyMap()))
                .thenReturn(verifyResult);

        RunResultVO result = service.runTask("task_1", request());

        Assertions.assertTrue(result.getWatermarkAdvanced());
        Mockito.verify(watermarkService).advanceWatermark(
                Mockito.eq(1L),
                Mockito.eq("default"),
                Mockito.eq("2026-06-02 00:00:00"),
                Mockito.eq(11L),
                Mockito.eq("batch_1")
        );
    }

    @Test
    void nonIncrementalTaskShouldSkipWatermarkAndRenderRunParams() {
        VerifyResult verifyResult = new VerifyResult();
        verifyResult.setPassed(true);
        verifyResult.setSourceCount(10L);
        verifyResult.setSinkCount(10L);
        Mockito.when(verifyService.verifyRun(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyMap()))
                .thenReturn(verifyResult);

        RunTaskRequest request = request();
        request.setParams(Map.of("xchg_batch_id", "translator_batch_1"));
        RunResultVO result = service.runTask("loader_task", request);

        Assertions.assertFalse(result.getWatermarkAdvanced());
        Assertions.assertEquals(SyncRunStatus.SUCCESS.getCode(), result.getRunStatus());
        Mockito.verify(configDao, Mockito.never()).queryByTaskId(3L);
        Mockito.verify(watermarkService, Mockito.never()).calculateNextRange(Mockito.eq(3L), Mockito.anyMap());
        Mockito.verify(watermarkService, Mockito.never())
                .advanceWatermark(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        org.mockito.ArgumentCaptor<String> hoconCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        Mockito.verify(zetaClient).submitJob(Mockito.eq(7L), Mockito.anyString(), hoconCaptor.capture());
        Assertions.assertTrue(hoconCaptor.getValue().contains("translator_batch_1"));
        Assertions.assertTrue(hoconCaptor.getValue().contains("system_batch=batch_1"));
    }

    @Test
    void nonIncrementalTaskVerificationFailedShouldNotAdvanceWatermark() {
        VerifyResult verifyResult = new VerifyResult();
        verifyResult.setPassed(false);
        verifyResult.setHasBlockingFailure(true);
        verifyResult.setErrorMessage("stg_header_count mismatch");
        Mockito.when(verifyService.verifyRun(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyMap()))
                .thenReturn(verifyResult);

        RunTaskRequest request = request();
        request.setParams(Map.of("xchg_batch_id", "translator_batch_1"));

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.runTask("loader_task", request)
        );

        Assertions.assertTrue(exception.getMessage().contains("Sync verification failed"));
        Mockito.verify(watermarkService, Mockito.never())
                .advanceWatermark(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private RunTaskRequest request() {
        RunTaskRequest request = new RunTaskRequest();
        request.setWaitForFinish(true);
        return request;
    }

    private SyncTaskEntity task() {
        return SyncTaskEntity.builder()
                .id(1L)
                .taskCode("task_1")
                .taskName("Task 1")
                .taskType(SyncTaskType.BATCH)
                .sourceType(SyncSourceType.JDBC)
                .sinkType(SyncSinkType.JDBC)
                .clientId(7L)
                .status(SyncTaskStatus.PUBLISHED)
                .currentVersionId(2L)
                .build();
    }

    private SyncTaskEntity nonIncrementalTask() {
        return SyncTaskEntity.builder()
                .id(3L)
                .taskCode("loader_task")
                .taskName("Loader Task")
                .taskType(SyncTaskType.BATCH)
                .sourceType(SyncSourceType.SQL)
                .sinkType(SyncSinkType.STARROCKS)
                .clientId(7L)
                .incrementalEnabled(false)
                .status(SyncTaskStatus.PUBLISHED)
                .currentVersionId(3L)
                .build();
    }

    private SyncTaskVersionEntity version() {
        return SyncTaskVersionEntity.builder()
                .id(2L)
                .taskId(1L)
                .versionNo(1)
                .hoconTemplate("job { name = \"${run_id}\" batch = \"${batch_id}\" }")
                .build();
    }

    private SyncTaskVersionEntity nonIncrementalVersion() {
        return SyncTaskVersionEntity.builder()
                .id(3L)
                .taskId(3L)
                .versionNo(1)
                .hoconTemplate("job { system_batch=${batch_id} run=${run_id} xchg_batch=${xchg_batch_id} }")
                .build();
    }

    private SyncIncrementalConfigEntity config() {
        return SyncIncrementalConfigEntity.builder()
                .taskId(1L)
                .sourceType(SyncSourceType.JDBC)
                .strategy(SyncIncrementalStrategy.UPDATE_TIME_RANGE)
                .watermarkKey("default")
                .lookbackSeconds(0)
                .build();
    }

    private WatermarkRange range() {
        WatermarkRange range = new WatermarkRange();
        range.setWatermarkKey("default");
        range.setStartValue("2026-06-01 00:00:00");
        range.setEndValue("2026-06-02 00:00:00");
        range.setStartTime(LocalDateTime.of(2026, 6, 1, 0, 0, 0));
        range.setEndTime(LocalDateTime.of(2026, 6, 2, 0, 0, 0));
        range.setAdvanceWatermark(true);
        return range;
    }

    private SyncBatchEntity batch() {
        return SyncBatchEntity.builder()
                .id(21L)
                .batchId("batch_1")
                .taskId(1L)
                .taskCode("task_1")
                .triggerType(SyncTriggerType.MANUAL)
                .runMode(SyncRunMode.NORMAL)
                .status(SyncBatchStatus.CREATED)
                .build();
    }

    private SyncSubmitJobResult submitResult() {
        SyncSubmitJobResult result = new SyncSubmitJobResult();
        result.setJobId("job-1");
        result.setJobName("job-name");
        result.setRawResponse(Map.of("jobId", "job-1"));
        return result;
    }

    private SyncJobStatusResult successStatus() {
        SyncJobStatusResult result = new SyncJobStatusResult();
        result.setJobId("job-1");
        result.setStatus("SUCCESS");
        result.setEndState(true);
        result.setSuccess(true);
        result.setRawResponse(Map.of("status", "SUCCESS"));
        return result;
    }
}
