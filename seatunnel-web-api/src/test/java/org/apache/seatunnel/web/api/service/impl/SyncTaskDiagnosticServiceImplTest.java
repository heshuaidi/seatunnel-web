package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.api.service.DataSourceService;
import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.SyncBatchService;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.api.service.SyncCheckSqlExecutor;
import org.apache.seatunnel.web.api.service.SyncIncrementalConfigService;
import org.apache.seatunnel.web.api.service.SyncRunService;
import org.apache.seatunnel.web.api.service.SyncTaskService;
import org.apache.seatunnel.web.api.service.SyncTaskVersionService;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.common.enums.SyncCheckType;
import org.apache.seatunnel.web.common.enums.SyncEngineType;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncPublishStatus;
import org.apache.seatunnel.web.common.enums.SyncSinkType;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.common.enums.SyncTaskType;
import org.apache.seatunnel.web.common.enums.SyncWatermarkValueType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.DataSource;
import org.apache.seatunnel.web.dao.entity.SeaTunnelClient;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.dao.repository.SeaTunnelClientDao;
import org.apache.seatunnel.web.spi.bean.dto.SyncCheckDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncHoconDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncTaskDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncWatermarkUpdateRequest;
import org.apache.seatunnel.web.spi.bean.vo.SyncCheckDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncHoconDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncTaskDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

class SyncTaskDiagnosticServiceImplTest {

    private SyncTaskDiagnosticServiceImpl service;
    private SyncTaskService taskService;
    private SyncTaskVersionService versionService;
    private SyncIncrementalConfigService configService;
    private SyncWatermarkService watermarkService;
    private SyncCheckConfigService checkConfigService;
    private SyncAuditService auditService;
    private DataSourceService dataSourceService;
    private SeaTunnelClientDao seaTunnelClientDao;

    @BeforeEach
    void setUp() {
        service = new SyncTaskDiagnosticServiceImpl();
        taskService = Mockito.mock(SyncTaskService.class);
        versionService = Mockito.mock(SyncTaskVersionService.class);
        configService = Mockito.mock(SyncIncrementalConfigService.class);
        watermarkService = Mockito.mock(SyncWatermarkService.class);
        checkConfigService = Mockito.mock(SyncCheckConfigService.class);
        SyncCheckSqlExecutor checkSqlExecutor = Mockito.mock(SyncCheckSqlExecutor.class);
        SyncRunService runService = Mockito.mock(SyncRunService.class);
        SyncBatchService batchService = Mockito.mock(SyncBatchService.class);
        auditService = Mockito.mock(SyncAuditService.class);
        HoconRenderService hoconRenderService = new HoconRenderServiceImpl();
        dataSourceService = Mockito.mock(DataSourceService.class);
        seaTunnelClientDao = Mockito.mock(SeaTunnelClientDao.class);

        Mockito.when(taskService.getByTaskCode("task_1")).thenReturn(task(SyncIncrementalStrategy.UPDATE_TIME_RANGE));
        Mockito.when(versionService.getById(2L)).thenReturn(version(
                "source { username = \"${source_username}\" password = \"${source_password}\" end = \"${batch_end_value}\" }"
        ));
        Mockito.when(configService.getByTaskId(1L)).thenReturn(updateTimeConfig());
        Mockito.when(configService.getByTaskIdAndWatermarkKey(1L, "default")).thenReturn(updateTimeConfig());
        Mockito.when(watermarkService.getByTaskIdAndWatermarkKey(1L, "default")).thenReturn(watermark("old"));
        Mockito.when(watermarkService.previewRange(Mockito.eq(1L), Mockito.any(), Mockito.anyMap())).thenReturn(range());
        Mockito.when(checkConfigService.listByTaskId(1L)).thenReturn(List.of(check(10L)));
        Mockito.when(dataSourceService.selectById(10L)).thenReturn(new DataSource());
        Mockito.when(seaTunnelClientDao.selectById(7L)).thenReturn(new SeaTunnelClient());
        Mockito.when(watermarkService.update(Mockito.any())).thenReturn(true);
        Mockito.when(auditService.appendWarn(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()
        )).thenReturn(1L);

        ReflectionTestUtils.setField(service, "syncTaskService", taskService);
        ReflectionTestUtils.setField(service, "syncTaskVersionService", versionService);
        ReflectionTestUtils.setField(service, "syncIncrementalConfigService", configService);
        ReflectionTestUtils.setField(service, "syncWatermarkService", watermarkService);
        ReflectionTestUtils.setField(service, "syncCheckConfigService", checkConfigService);
        ReflectionTestUtils.setField(service, "syncCheckSqlExecutor", checkSqlExecutor);
        ReflectionTestUtils.setField(service, "syncRunService", runService);
        ReflectionTestUtils.setField(service, "syncBatchService", batchService);
        ReflectionTestUtils.setField(service, "syncAuditService", auditService);
        ReflectionTestUtils.setField(service, "hoconRenderService", hoconRenderService);
        ReflectionTestUtils.setField(service, "dataSourceService", dataSourceService);
        ReflectionTestUtils.setField(service, "seaTunnelClientDao", seaTunnelClientDao);
    }

    @Test
    void diagnoseShouldReturnNormalTaskAndMaskRenderedHocon() {
        SyncTaskDiagnoseRequest request = new SyncTaskDiagnoseRequest();
        request.setIncludeHoconPreview(true);
        request.setIncludeCheckPreview(true);
        request.setParams(Map.of("source_username", "st_lab", "source_password", "plain-password"));

        SyncTaskDiagnosticVO diagnostic = service.diagnoseTask("task_1", request);

        Assertions.assertEquals("OK", diagnostic.getDiagnostics().getLevel());
        Assertions.assertTrue(diagnostic.getVersion().getExists());
        Assertions.assertTrue(diagnostic.getIncrementalConfig().getExists());
        Assertions.assertTrue(diagnostic.getWatermark().getExists());
        Assertions.assertTrue(diagnostic.getHocon().getRenderable());
        Assertions.assertFalse(diagnostic.getHocon().getRenderedHoconPreview().contains("plain-password"));
        Assertions.assertTrue(diagnostic.getHocon().getRenderedHoconPreview().contains("******"));
        Assertions.assertEquals(1, diagnostic.getChecks().getCheckCount());
    }

    @Test
    void diagnoseShouldRejectMissingTask() {
        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.diagnoseTask("missing_task", new SyncTaskDiagnoseRequest())
        );

        Assertions.assertTrue(exception.getMessage().contains("Sync task not found"));
    }

    @Test
    void diagnoseHoconShouldReturnAllMissingVariables() {
        Mockito.when(versionService.getById(2L)).thenReturn(version(
                "source { username = \"${source_username}\" password = \"${source_password}\" token = \"${source_token}\" }"
        ));
        SyncHoconDiagnoseRequest request = new SyncHoconDiagnoseRequest();
        request.setParams(Map.of("source_username", "st_lab"));

        SyncHoconDiagnosticVO diagnostic = service.diagnoseHocon("task_1", request);

        Assertions.assertFalse(diagnostic.getRenderable());
        Assertions.assertEquals(List.of("source_password", "source_token"), diagnostic.getMissingVariables());
    }

    @Test
    void diagnoseShouldReportIdRangeMissingBatchEndValue() {
        Mockito.when(taskService.getByTaskCode("task_1")).thenReturn(task(SyncIncrementalStrategy.ID_RANGE));
        Mockito.when(configService.getByTaskId(1L)).thenReturn(idRangeConfig());
        Mockito.when(watermarkService.previewRange(Mockito.eq(1L), Mockito.any(), Mockito.anyMap()))
                .thenThrow(new ServiceException("ID_RANGE requires batchEndValue in run params"));
        Mockito.when(checkConfigService.listByTaskId(1L)).thenReturn(List.of());

        SyncTaskDiagnosticVO diagnostic = service.diagnoseTask("task_1", new SyncTaskDiagnoseRequest());

        Assertions.assertEquals("ERROR", diagnostic.getDiagnostics().getLevel());
        Assertions.assertTrue(diagnostic.getDiagnostics().getMessages()
                .contains("ERROR: ID_RANGE requires batchEndValue in run params"));
    }

    @Test
    void diagnoseChecksShouldWarnWhenDatasourceIdIsEmpty() {
        Mockito.when(checkConfigService.listByTaskId(1L)).thenReturn(List.of(check(null)));
        SyncCheckDiagnoseRequest request = new SyncCheckDiagnoseRequest();
        request.setParams(Map.of("source_username", "st_lab", "source_password", "plain-password"));
        request.setExecuteSql(false);

        SyncCheckDiagnosticVO diagnostic = service.diagnoseChecks("task_1", request);

        Assertions.assertEquals(1, diagnostic.getMissingDatasourceIds().size());
        Assertions.assertTrue(diagnostic.getMissingDatasourceIds().get(0).contains("datasourceId is empty"));
    }

    @Test
    void updateWatermarkShouldMoveOldValueToPreviousValueAndWriteAudit() {
        SyncWatermarkUpdateRequest request = new SyncWatermarkUpdateRequest();
        request.setWatermarkKey("default");
        request.setCurrentValue("2026-06-01 00:00:00");
        request.setReason("reset for lab test");

        WatermarkVO watermark = service.updateWatermark("task_1", request);

        Assertions.assertEquals("old", watermark.getPreviousValue());
        Assertions.assertEquals("2026-06-01 00:00:00", watermark.getCurrentValue());

        ArgumentCaptor<SyncWatermarkEntity> captor = ArgumentCaptor.forClass(SyncWatermarkEntity.class);
        Mockito.verify(watermarkService).update(captor.capture());
        Assertions.assertEquals("old", captor.getValue().getPreviousValue());
        Assertions.assertEquals("2026-06-01 00:00:00", captor.getValue().getCurrentValue());
        Mockito.verify(auditService).appendWarn(
                Mockito.isNull(),
                Mockito.isNull(),
                Mockito.eq(1L),
                Mockito.eq("task_1"),
                Mockito.eq(SyncAuditEventType.MANUAL_UPDATE_WATERMARK),
                Mockito.anyString(),
                Mockito.any()
        );
    }

    @Test
    void updateWatermarkShouldRejectEmptyReason() {
        SyncWatermarkUpdateRequest request = new SyncWatermarkUpdateRequest();
        request.setCurrentValue("2026-06-01 00:00:00");
        request.setReason("");

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.updateWatermark("task_1", request)
        );

        Assertions.assertTrue(exception.getMessage().contains("reason is required"));
        Mockito.verify(watermarkService, Mockito.never()).update(Mockito.any());
        Mockito.verify(auditService, Mockito.never()).appendWarn(
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any(),
                Mockito.eq(SyncAuditEventType.MANUAL_UPDATE_WATERMARK),
                Mockito.any(),
                Mockito.any()
        );
    }

    private SyncTaskEntity task(SyncIncrementalStrategy strategy) {
        return SyncTaskEntity.builder()
                .id(1L)
                .taskCode("task_1")
                .taskName("Task 1")
                .taskType(SyncTaskType.BATCH)
                .sourceType(SyncSourceType.JDBC)
                .sinkType(SyncSinkType.STARROCKS)
                .engineType(SyncEngineType.ZETA)
                .clientId(7L)
                .incrementalEnabled(true)
                .incrementalStrategy(strategy)
                .status(SyncTaskStatus.PUBLISHED)
                .currentVersionId(2L)
                .build();
    }

    private SyncTaskVersionEntity version(String hoconTemplate) {
        return SyncTaskVersionEntity.builder()
                .id(2L)
                .taskId(1L)
                .versionNo(3)
                .publishStatus(SyncPublishStatus.PUBLISHED)
                .hoconHash("hash")
                .hoconTemplate(hoconTemplate)
                .build();
    }

    private SyncIncrementalConfigEntity updateTimeConfig() {
        return SyncIncrementalConfigEntity.builder()
                .taskId(1L)
                .sourceType(SyncSourceType.JDBC)
                .strategy(SyncIncrementalStrategy.UPDATE_TIME_RANGE)
                .watermarkKey("default")
                .watermarkField("update_time")
                .watermarkFieldType(SyncWatermarkValueType.DATETIME)
                .startValue("2026-06-01 00:00:00")
                .lookbackSeconds(60)
                .maxBatchSeconds(3600)
                .build();
    }

    private SyncIncrementalConfigEntity idRangeConfig() {
        return SyncIncrementalConfigEntity.builder()
                .taskId(1L)
                .sourceType(SyncSourceType.SQL)
                .strategy(SyncIncrementalStrategy.ID_RANGE)
                .watermarkKey("default")
                .watermarkField("id")
                .watermarkFieldType(SyncWatermarkValueType.LONG)
                .startValue("0")
                .build();
    }

    private SyncWatermarkEntity watermark(String currentValue) {
        return SyncWatermarkEntity.builder()
                .id(5L)
                .taskId(1L)
                .watermarkKey("default")
                .currentValue(currentValue)
                .previousValue("older")
                .currentValueType(SyncWatermarkValueType.DATETIME)
                .lastSuccessRunId(11L)
                .lastSuccessBatchId("batch_old")
                .build();
    }

    private WatermarkRange range() {
        WatermarkRange range = new WatermarkRange();
        range.setWatermarkKey("default");
        range.setCurrentWatermark("2026-06-01 00:00:00");
        range.setStartValue("2026-06-01 00:00:00");
        range.setEndValue("2026-06-01 01:00:00");
        range.setStartTime(LocalDateTime.of(2026, 6, 1, 0, 0, 0));
        range.setEndTime(LocalDateTime.of(2026, 6, 1, 1, 0, 0));
        range.setLookbackApplied(false);
        range.setMaxBatchSecondsApplied(false);
        range.setWarnings(List.of());
        range.setAdvanceWatermark(true);
        return range;
    }

    private SyncCheckConfigEntity check(Long datasourceId) {
        return SyncCheckConfigEntity.builder()
                .id(9L)
                .taskId(1L)
                .checkCode("source_count")
                .checkName("Source count")
                .checkType(SyncCheckType.SOURCE_COUNT)
                .datasourceId(datasourceId)
                .sqlText("select count(*) from source_table where update_time <= '${batch_end_value}'")
                .expectedOperator(SyncCheckExpectedOperator.GE)
                .expectedValue("0")
                .enabled(true)
                .failOnMismatch(true)
                .sortOrder(0)
                .build();
    }
}
