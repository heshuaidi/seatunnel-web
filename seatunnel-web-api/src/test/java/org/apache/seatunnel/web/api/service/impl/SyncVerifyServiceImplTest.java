package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.api.service.SyncCheckResultService;
import org.apache.seatunnel.web.api.service.SyncCheckSqlExecutor;
import org.apache.seatunnel.web.api.service.model.VerifyResult;
import org.apache.seatunnel.web.common.enums.SyncCheckDatasourceType;
import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.common.enums.SyncCheckType;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

class SyncVerifyServiceImplTest {

    private SyncVerifyServiceImpl service;
    private SyncCheckConfigService configService;
    private SyncCheckResultService resultService;
    private SyncCheckSqlExecutor sqlExecutor;

    @BeforeEach
    void setUp() {
        service = new SyncVerifyServiceImpl();
        configService = Mockito.mock(SyncCheckConfigService.class);
        resultService = Mockito.mock(SyncCheckResultService.class);
        sqlExecutor = Mockito.mock(SyncCheckSqlExecutor.class);
        ReflectionTestUtils.setField(service, "syncCheckConfigService", configService);
        ReflectionTestUtils.setField(service, "syncCheckResultService", resultService);
        ReflectionTestUtils.setField(service, "syncCheckSqlExecutor", sqlExecutor);
        ReflectionTestUtils.setField(service, "hoconRenderService", new HoconRenderServiceImpl());
        ReflectionTestUtils.setField(service, "syncAuditService", Mockito.mock(SyncAuditService.class));
    }

    @Test
    void shouldPassWhenNoCheckConfigExists() {
        Mockito.when(configService.listEnabledByTaskId(1L)).thenReturn(List.of());

        VerifyResult result = service.verifyRun(task(), batch(), run(), Map.of());

        Assertions.assertTrue(result.isPassed());
        Assertions.assertFalse(result.isHasBlockingFailure());
        Mockito.verifyNoInteractions(sqlExecutor);
    }

    @Test
    void sourceAndSinkCountShouldPassWhenEqual() {
        Mockito.when(configService.listEnabledByTaskId(1L)).thenReturn(List.of(
                sourceCountCheck(),
                sinkCountCheck(),
                errorCountCheck()
        ));
        mockScalarByCheckCode("100", "100", "0");

        VerifyResult result = service.verifyRun(task(), batch(), run(), variables());

        Assertions.assertTrue(result.isPassed());
        Assertions.assertFalse(result.isHasBlockingFailure());
        Assertions.assertEquals(100L, result.getSourceCount());
        Assertions.assertEquals(100L, result.getSinkCount());
        Assertions.assertEquals(0L, result.getErrorCount());
    }

    @Test
    void sourceAndSinkCountShouldFailWhenMismatch() {
        Mockito.when(configService.listEnabledByTaskId(1L)).thenReturn(List.of(
                sourceCountCheck(),
                sinkCountCheck()
        ));
        mockScalarByCheckCode("100", "90", "0");

        VerifyResult result = service.verifyRun(task(), batch(), run(), variables());

        Assertions.assertFalse(result.isPassed());
        Assertions.assertTrue(result.isHasBlockingFailure());
        Assertions.assertTrue(result.getErrorMessage().contains("sink_count"));
    }

    @Test
    void errorCountShouldPassWhenZero() {
        Mockito.when(configService.listEnabledByTaskId(1L)).thenReturn(List.of(errorCountCheck()));
        mockScalarByCheckCode("100", "100", "0");

        VerifyResult result = service.verifyRun(task(), batch(), run(), variables());

        Assertions.assertTrue(result.isPassed());
        Assertions.assertEquals(0L, result.getErrorCount());
    }

    @Test
    void errorCountShouldFailWhenGreaterThanZero() {
        Mockito.when(configService.listEnabledByTaskId(1L)).thenReturn(List.of(errorCountCheck()));
        mockScalarByCheckCode("100", "100", "5");

        VerifyResult result = service.verifyRun(task(), batch(), run(), variables());

        Assertions.assertFalse(result.isPassed());
        Assertions.assertTrue(result.isHasBlockingFailure());
    }

    @Test
    void sqlExceptionShouldCreateFailedResult() {
        Mockito.when(configService.listEnabledByTaskId(1L)).thenReturn(List.of(sourceCountCheck()));
        Mockito.when(sqlExecutor.executeScalar(Mockito.any(), Mockito.anyString()))
                .thenThrow(new RuntimeException("database unavailable"));

        VerifyResult result = service.verifyRun(task(), batch(), run(), variables());

        Assertions.assertFalse(result.isPassed());
        Assertions.assertTrue(result.isHasBlockingFailure());
        ArgumentCaptor<org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity> captor =
                ArgumentCaptor.forClass(org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity.class);
        Mockito.verify(resultService).insertResult(captor.capture());
        Assertions.assertFalse(captor.getValue().getPassed());
        Assertions.assertTrue(captor.getValue().getErrorMessage().contains("database unavailable"));
    }

    private void mockScalarByCheckCode(String sourceCount, String sinkCount, String errorCount) {
        Mockito.when(sqlExecutor.executeScalar(Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> {
                    SyncCheckConfigEntity config = invocation.getArgument(0);
                    if ("source_count".equals(config.getCheckCode())) {
                        return sourceCount;
                    }
                    if ("sink_count".equals(config.getCheckCode())) {
                        return sinkCount;
                    }
                    if ("error_count".equals(config.getCheckCode())) {
                        return errorCount;
                    }
                    return null;
                });
    }

    private SyncCheckConfigEntity sourceCountCheck() {
        return check("source_count", SyncCheckType.SOURCE_COUNT, SyncCheckExpectedOperator.GE, "0", null, 10);
    }

    private SyncCheckConfigEntity sinkCountCheck() {
        return check("sink_count", SyncCheckType.SINK_COUNT, SyncCheckExpectedOperator.EQ, null, "source_count", 20);
    }

    private SyncCheckConfigEntity errorCountCheck() {
        return check("error_count", SyncCheckType.ERROR_COUNT, SyncCheckExpectedOperator.EQ, "0", null, 30);
    }

    private SyncCheckConfigEntity check(
            String code,
            SyncCheckType type,
            SyncCheckExpectedOperator operator,
            String expectedValue,
            String compareToCheckCode,
            int sortOrder
    ) {
        return SyncCheckConfigEntity.builder()
                .taskId(1L)
                .checkCode(code)
                .checkName(code)
                .checkType(type)
                .datasourceType(SyncCheckDatasourceType.CUSTOM)
                .datasourceId(1L)
                .sqlText("select count(*) from t where batch_id = '${batch_id}'")
                .expectedOperator(operator)
                .expectedValue(expectedValue)
                .compareToCheckCode(compareToCheckCode)
                .failOnMismatch(true)
                .enabled(true)
                .sortOrder(sortOrder)
                .build();
    }

    private SyncTaskEntity task() {
        return SyncTaskEntity.builder()
                .id(1L)
                .taskCode("task_1")
                .build();
    }

    private SyncBatchEntity batch() {
        return SyncBatchEntity.builder()
                .batchId("batch_1")
                .taskId(1L)
                .taskCode("task_1")
                .build();
    }

    private SyncRunEntity run() {
        return SyncRunEntity.builder()
                .id(11L)
                .runId("run_1")
                .taskId(1L)
                .batchId("batch_1")
                .build();
    }

    private Map<String, Object> variables() {
        return Map.of("batch_id", "batch_1");
    }
}
