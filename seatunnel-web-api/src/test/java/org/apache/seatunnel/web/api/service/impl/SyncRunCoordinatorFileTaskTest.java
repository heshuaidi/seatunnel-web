package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.SyncBatchService;
import org.apache.seatunnel.web.api.service.SyncFileDiscoveryService;
import org.apache.seatunnel.web.api.service.SyncRunService;
import org.apache.seatunnel.web.api.service.SyncVerifyService;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.api.service.SyncZetaClient;
import org.apache.seatunnel.web.api.service.model.SyncJobStatusResult;
import org.apache.seatunnel.web.api.service.model.SyncSubmitJobResult;
import org.apache.seatunnel.web.api.service.model.VerifyResult;
import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.common.enums.SyncFileCursorMode;
import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncSinkType;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.common.enums.SyncTaskType;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;
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

import java.util.List;
import java.util.Map;

class SyncRunCoordinatorFileTaskTest {

    private SyncRunCoordinatorServiceImpl service;
    private SyncWatermarkService watermarkService;
    private SyncFileDiscoveryService fileDiscoveryService;
    private SyncZetaClient zetaClient;

    @BeforeEach
    void setUp() {
        service = new SyncRunCoordinatorServiceImpl();
        SyncTaskDao taskDao = Mockito.mock(SyncTaskDao.class);
        SyncTaskVersionDao versionDao = Mockito.mock(SyncTaskVersionDao.class);
        SyncIncrementalConfigDao configDao = Mockito.mock(SyncIncrementalConfigDao.class);
        SyncBatchService batchService = Mockito.mock(SyncBatchService.class);
        SyncRunService runService = Mockito.mock(SyncRunService.class);
        SyncAuditService auditService = Mockito.mock(SyncAuditService.class);
        watermarkService = Mockito.mock(SyncWatermarkService.class);
        fileDiscoveryService = Mockito.mock(SyncFileDiscoveryService.class);
        zetaClient = Mockito.mock(SyncZetaClient.class);
        SyncVerifyService verifyService = Mockito.mock(SyncVerifyService.class);
        HoconRenderService hoconRenderService = new HoconRenderServiceImpl();

        Mockito.when(taskDao.queryByTaskCode("file_task")).thenReturn(task());
        Mockito.when(versionDao.queryById(2L)).thenReturn(version());
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(config());
        Mockito.when(batchService.createFileBatchForRun(
                Mockito.any(),
                Mockito.eq(SyncTriggerType.MANUAL),
                Mockito.eq(SyncRunMode.NORMAL),
                Mockito.any(),
                Mockito.any()
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
        Mockito.when(fileDiscoveryService.claimFilesForBatch(Mockito.eq(1L), Mockito.eq("batch_1"), Mockito.eq(10)))
                .thenReturn(List.of(fileItem()));
        Mockito.when(zetaClient.submitJob(Mockito.eq(7L), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(submitResult());
        VerifyResult verifyResult = new VerifyResult();
        verifyResult.setPassed(true);
        Mockito.when(verifyService.verifyRun(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyMap()))
                .thenReturn(verifyResult);

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
        ReflectionTestUtils.setField(service, "syncFileDiscoveryService", fileDiscoveryService);
        ReflectionTestUtils.setField(service, "syncRunProperties", properties);
    }

    @Test
    void fileTaskSuccessShouldMarkFilesSuccessAndNotAdvanceWatermark() {
        Mockito.when(zetaClient.getJobStatus(7L, "job-1")).thenReturn(successStatus());

        RunResultVO result = service.runTask("file_task", request());

        Assertions.assertFalse(result.getWatermarkAdvanced());
        Assertions.assertEquals(SyncBatchStatus.SUCCESS.getCode(), result.getBatchStatus());
        Mockito.verify(fileDiscoveryService).discoverFiles(1L);
        Mockito.verify(fileDiscoveryService).markFilesProcessing(Mockito.eq("batch_1"), Mockito.anyString());
        Mockito.verify(fileDiscoveryService).markFilesSuccess(Mockito.eq("batch_1"), Mockito.anyString());
        Mockito.verify(fileDiscoveryService, Mockito.never())
                .markFilesFailed(Mockito.anyString(), Mockito.any(), Mockito.anyString());
        Mockito.verify(watermarkService, Mockito.never())
                .advanceWatermark(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void fileTaskFailedShouldMarkFilesFailedAndNotAdvanceWatermark() {
        Mockito.when(zetaClient.getJobStatus(7L, "job-1")).thenReturn(failedStatus());

        Assertions.assertThrows(ServiceException.class, () -> service.runTask("file_task", request()));

        Mockito.verify(fileDiscoveryService).markFilesProcessing(Mockito.eq("batch_1"), Mockito.anyString());
        Mockito.verify(fileDiscoveryService).markFilesFailed(Mockito.eq("batch_1"), Mockito.anyString(), Mockito.contains("SeaTunnel job failed"));
        Mockito.verify(fileDiscoveryService, Mockito.never())
                .markFilesSuccess(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(watermarkService, Mockito.never())
                .advanceWatermark(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private RunTaskRequest request() {
        RunTaskRequest request = new RunTaskRequest();
        request.setWaitForFinish(true);
        request.setParams(Map.of("maxFiles", 10));
        return request;
    }

    private SyncTaskEntity task() {
        return SyncTaskEntity.builder()
                .id(1L)
                .taskCode("file_task")
                .taskName("File Task")
                .taskType(SyncTaskType.BATCH)
                .sourceType(SyncSourceType.LOCAL_FILE)
                .sinkType(SyncSinkType.JDBC)
                .clientId(7L)
                .status(SyncTaskStatus.PUBLISHED)
                .currentVersionId(2L)
                .build();
    }

    private SyncTaskVersionEntity version() {
        return SyncTaskVersionEntity.builder()
                .id(2L)
                .taskId(1L)
                .versionNo(1)
                .hoconTemplate("source { LocalFile { path = \"${file_path}\" file_filter_pattern = \"${file_filter_pattern}\" } } "
                        + "batch = \"${batch_id}\" run = \"${run_id}\" count = \"${batch_file_count}\"")
                .build();
    }

    private SyncIncrementalConfigEntity config() {
        return SyncIncrementalConfigEntity.builder()
                .taskId(1L)
                .sourceType(SyncSourceType.LOCAL_FILE)
                .strategy(SyncIncrementalStrategy.FILE_MANIFEST)
                .filePath("/data")
                .filePattern(".*\\.json")
                .fileRecursive(true)
                .fileCursorMode(SyncFileCursorMode.PATH_MTIME_SIZE)
                .maxBatchRows(10L)
                .build();
    }

    private SyncBatchEntity batch() {
        return SyncBatchEntity.builder()
                .id(21L)
                .batchId("batch_1")
                .taskId(1L)
                .taskCode("file_task")
                .triggerType(SyncTriggerType.MANUAL)
                .runMode(SyncRunMode.NORMAL)
                .status(SyncBatchStatus.CREATED)
                .build();
    }

    private SyncFileItemEntity fileItem() {
        SyncFileItemEntity entity = new SyncFileItemEntity();
        entity.setId(100L);
        entity.setTaskId(1L);
        entity.setBatchId("batch_1");
        entity.setStatus(SyncFileItemStatus.CLAIMED);
        entity.setFilePath("/data/a.json");
        entity.setFileName("a.json");
        entity.setRelativePath("a.json");
        return entity;
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

    private SyncJobStatusResult failedStatus() {
        SyncJobStatusResult result = new SyncJobStatusResult();
        result.setJobId("job-1");
        result.setStatus("FAILED");
        result.setEndState(true);
        result.setSuccess(false);
        result.setErrorMessage("failed");
        result.setRawResponse(Map.of("status", "FAILED"));
        return result;
    }
}
