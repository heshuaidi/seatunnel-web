package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.api.service.SyncBuiltInTemplateService;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.api.service.SyncIncrementalConfigService;
import org.apache.seatunnel.web.api.service.SyncTaskService;
import org.apache.seatunnel.web.api.service.SyncTaskVersionService;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.common.enums.SyncCheckDatasourceType;
import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.common.enums.SyncCheckType;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncPublishStatus;
import org.apache.seatunnel.web.common.enums.SyncSinkType;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.common.enums.SyncWatermarkValueType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.spi.bean.dto.CreateFabLoaderPublishTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.CreateFabMesSpcJdbcTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.CreateGenericJdbcStarRocksTaskRequest;
import org.apache.seatunnel.web.spi.bean.vo.CreateTaskFromTemplateResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBuiltInTemplateVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

class SyncBuiltInTemplateServiceImplTest {

    private SyncBuiltInTemplateServiceImpl service;
    private SyncTaskService taskService;
    private SyncTaskVersionService versionService;
    private SyncIncrementalConfigService incrementalConfigService;
    private SyncWatermarkService watermarkService;
    private SyncCheckConfigService checkConfigService;

    @BeforeEach
    void setUp() {
        service = new SyncBuiltInTemplateServiceImpl();
        taskService = Mockito.mock(SyncTaskService.class);
        versionService = Mockito.mock(SyncTaskVersionService.class);
        incrementalConfigService = Mockito.mock(SyncIncrementalConfigService.class);
        watermarkService = Mockito.mock(SyncWatermarkService.class);
        checkConfigService = Mockito.mock(SyncCheckConfigService.class);
        HoconRenderService hoconRenderService = new HoconRenderServiceImpl();

        Mockito.when(taskService.create(Mockito.any())).thenReturn(1L);
        Mockito.when(taskService.update(Mockito.any())).thenReturn(true);
        Mockito.when(versionService.create(Mockito.any())).thenReturn(2L);
        Mockito.when(incrementalConfigService.create(Mockito.any())).thenReturn(3L);
        Mockito.when(watermarkService.create(Mockito.any())).thenReturn(4L);

        ReflectionTestUtils.setField(service, "syncTaskService", taskService);
        ReflectionTestUtils.setField(service, "syncTaskVersionService", versionService);
        ReflectionTestUtils.setField(service, "syncIncrementalConfigService", incrementalConfigService);
        ReflectionTestUtils.setField(service, "syncWatermarkService", watermarkService);
        ReflectionTestUtils.setField(service, "syncCheckConfigService", checkConfigService);
        ReflectionTestUtils.setField(service, "hoconRenderService", hoconRenderService);
    }

    @Test
    void listTemplatesShouldReturnFabMesSpcTemplate() {
        List<SyncBuiltInTemplateVO> templates = service.listTemplates();

        Assertions.assertEquals(3, templates.size());
        Assertions.assertTrue(templates.stream()
                .anyMatch(template -> SyncBuiltInTemplateService.FAB_MES_SPC_JDBC_TRANSLATOR
                        .equals(template.getTemplateCode())));
        Assertions.assertTrue(templates.stream()
                .anyMatch(template -> SyncBuiltInTemplateService.FAB_LOADER_PUBLISH_XCHG_TO_STG
                        .equals(template.getTemplateCode())));
        Assertions.assertTrue(templates.stream()
                .anyMatch(template -> SyncBuiltInTemplateService.GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL
                        .equals(template.getTemplateCode())));
    }

    @Test
    void getTemplateShouldReturnHoconTemplate() {
        SyncBuiltInTemplateVO template = service.getTemplate(SyncBuiltInTemplateService.FAB_MES_SPC_JDBC_TRANSLATOR);

        Assertions.assertTrue(template.getHoconTemplate().contains("xchg_meas_header"));
        Assertions.assertTrue(template.getRequiredVariables().contains("source_jdbc_url"));
    }

    @Test
    void getLoaderPublishTemplateShouldReturnHoconTemplate() {
        SyncBuiltInTemplateVO template = service.getTemplate(SyncBuiltInTemplateService.FAB_LOADER_PUBLISH_XCHG_TO_STG);

        Assertions.assertTrue(template.getHoconTemplate().contains("${xchg_batch_id}"));
        Assertions.assertTrue(template.getRequiredVariables().contains("xchg_batch_id"));
    }

    @Test
    void getGenericJdbcStarRocksTemplateShouldReturnHoconTemplate() {
        SyncBuiltInTemplateVO template = service.getTemplate(
                SyncBuiltInTemplateService.GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL);

        Assertions.assertTrue(template.getHoconTemplate().contains("${source_query}"));
        Assertions.assertTrue(template.getRequiredVariables().contains("batch_start_time"));
        Assertions.assertTrue(template.getRequiredVariables().contains("batch_end_value"));
    }

    @Test
    void getTemplateShouldThrowForUnknownTemplate() {
        Assertions.assertThrows(ServiceException.class, () -> service.getTemplate("UNKNOWN"));
    }

    @Test
    void createTaskShouldCreateMetadataWithoutChecksWhenDatasourceMissing() {
        CreateTaskFromTemplateResultVO result = service.createTaskFromFabMesSpcJdbcTemplate(request(false));

        Assertions.assertEquals(1L, result.getTaskId());
        Assertions.assertEquals(2L, result.getVersionId());
        Assertions.assertEquals(3L, result.getIncrementalConfigId());
        Assertions.assertEquals(4L, result.getWatermarkId());
        Assertions.assertEquals(0, result.getCreatedCheckCount());
        Assertions.assertFalse(result.getWarnings().isEmpty());
        Mockito.verify(checkConfigService, Mockito.never()).create(Mockito.any());

        ArgumentCaptor<SyncTaskEntity> taskCaptor = ArgumentCaptor.forClass(SyncTaskEntity.class);
        Mockito.verify(taskService).create(taskCaptor.capture());
        Assertions.assertEquals(SyncTaskStatus.DRAFT, taskCaptor.getValue().getStatus());
        Assertions.assertEquals(SyncSourceType.JDBC, taskCaptor.getValue().getSourceType());
        Assertions.assertEquals(SyncSinkType.STARROCKS, taskCaptor.getValue().getSinkType());

        ArgumentCaptor<SyncTaskVersionEntity> versionCaptor = ArgumentCaptor.forClass(SyncTaskVersionEntity.class);
        Mockito.verify(versionService).create(versionCaptor.capture());
        Assertions.assertEquals(SyncPublishStatus.PUBLISHED, versionCaptor.getValue().getPublishStatus());
        Assertions.assertTrue(versionCaptor.getValue().getHoconTemplate().contains("ST_ORDER_HEADER"));
        Assertions.assertNotNull(versionCaptor.getValue().getHoconHash());

        ArgumentCaptor<SyncIncrementalConfigEntity> configCaptor =
                ArgumentCaptor.forClass(SyncIncrementalConfigEntity.class);
        Mockito.verify(incrementalConfigService).create(configCaptor.capture());
        Assertions.assertEquals(SyncIncrementalStrategy.UPDATE_TIME_RANGE, configCaptor.getValue().getStrategy());
        Assertions.assertEquals(SyncWatermarkValueType.DATETIME, configCaptor.getValue().getWatermarkFieldType());

        ArgumentCaptor<SyncWatermarkEntity> watermarkCaptor = ArgumentCaptor.forClass(SyncWatermarkEntity.class);
        Mockito.verify(watermarkService).create(watermarkCaptor.capture());
        Assertions.assertEquals("2026-01-01 00:00:00", watermarkCaptor.getValue().getCurrentValue());
    }

    @Test
    void createTaskShouldCreateDefaultChecksWhenDatasourceProvided() {
        CreateTaskFromTemplateResultVO result = service.createTaskFromFabMesSpcJdbcTemplate(request(true));

        Assertions.assertEquals(4, result.getCreatedCheckCount());
        ArgumentCaptor<SyncCheckConfigEntity> checkCaptor = ArgumentCaptor.forClass(SyncCheckConfigEntity.class);
        Mockito.verify(checkConfigService, Mockito.times(4)).create(checkCaptor.capture());

        List<SyncCheckConfigEntity> checks = checkCaptor.getAllValues();
        Assertions.assertEquals("source_count", checks.get(0).getCheckCode());
        Assertions.assertEquals(SyncCheckType.SOURCE_COUNT, checks.get(0).getCheckType());
        Assertions.assertEquals(SyncCheckDatasourceType.SOURCE, checks.get(0).getDatasourceType());
        Assertions.assertTrue(checks.get(0).getSqlText().contains("ST_ORDER_HEADER"));

        Assertions.assertEquals("sink_header_count", checks.get(1).getCheckCode());
        Assertions.assertEquals(SyncCheckType.SINK_COUNT, checks.get(1).getCheckType());
        Assertions.assertEquals(SyncCheckExpectedOperator.EQ, checks.get(1).getExpectedOperator());
        Assertions.assertEquals("source_count", checks.get(1).getCompareToCheckCode());

        Assertions.assertEquals("sink_site_count", checks.get(2).getCheckCode());
        Assertions.assertEquals(SyncCheckType.CUSTOM_COUNT, checks.get(2).getCheckType());

        Assertions.assertEquals("error_count", checks.get(3).getCheckCode());
        Assertions.assertEquals(SyncCheckType.ERROR_COUNT, checks.get(3).getCheckType());
        Assertions.assertEquals("0", checks.get(3).getExpectedValue());
    }

    @Test
    void createLoaderPublishTaskShouldCreateTaskVersionWithoutWatermarkWhenDatasourceMissing() {
        CreateTaskFromTemplateResultVO result = service.createTaskFromFabLoaderPublishTemplate(loaderRequest(false));

        Assertions.assertEquals(1L, result.getTaskId());
        Assertions.assertEquals(2L, result.getVersionId());
        Assertions.assertNull(result.getIncrementalConfigId());
        Assertions.assertNull(result.getWatermarkId());
        Assertions.assertEquals(0, result.getCreatedCheckCount());
        Assertions.assertFalse(result.getWarnings().isEmpty());
        Mockito.verify(incrementalConfigService, Mockito.never()).create(Mockito.any());
        Mockito.verify(watermarkService, Mockito.never()).create(Mockito.any());

        ArgumentCaptor<SyncTaskEntity> taskCaptor = ArgumentCaptor.forClass(SyncTaskEntity.class);
        Mockito.verify(taskService).create(taskCaptor.capture());
        Assertions.assertFalse(taskCaptor.getValue().getIncrementalEnabled());
        Assertions.assertNull(taskCaptor.getValue().getIncrementalStrategy());
        Assertions.assertEquals(SyncTaskStatus.PUBLISHED, taskCaptor.getValue().getStatus());

        ArgumentCaptor<SyncTaskVersionEntity> versionCaptor = ArgumentCaptor.forClass(SyncTaskVersionEntity.class);
        Mockito.verify(versionService).create(versionCaptor.capture());
        Assertions.assertTrue(versionCaptor.getValue().getHoconTemplate().contains("WHERE batch_id = '${xchg_batch_id}'"));
        Assertions.assertTrue(versionCaptor.getValue().getParamSchemaJson().contains("xchg_batch_id"));
    }

    @Test
    void createLoaderPublishTaskShouldCreateDefaultChecksWhenDatasourceProvided() {
        CreateTaskFromTemplateResultVO result = service.createTaskFromFabLoaderPublishTemplate(loaderRequest(true));

        Assertions.assertEquals(6, result.getCreatedCheckCount());
        ArgumentCaptor<SyncCheckConfigEntity> checkCaptor = ArgumentCaptor.forClass(SyncCheckConfigEntity.class);
        Mockito.verify(checkConfigService, Mockito.times(6)).create(checkCaptor.capture());

        List<SyncCheckConfigEntity> checks = checkCaptor.getAllValues();
        Assertions.assertEquals("xchg_header_count", checks.get(0).getCheckCode());
        Assertions.assertEquals(SyncCheckType.SOURCE_COUNT, checks.get(0).getCheckType());
        Assertions.assertTrue(checks.get(0).getSqlText().contains("${xchg_batch_id}"));

        Assertions.assertEquals("stg_header_count", checks.get(1).getCheckCode());
        Assertions.assertEquals(SyncCheckType.SINK_COUNT, checks.get(1).getCheckType());
        Assertions.assertEquals("xchg_header_count", checks.get(1).getCompareToCheckCode());

        Assertions.assertEquals("stg_site_count", checks.get(3).getCheckCode());
        Assertions.assertEquals("xchg_site_count", checks.get(3).getCompareToCheckCode());

        Assertions.assertEquals("stg_error_count", checks.get(5).getCheckCode());
        Assertions.assertEquals("xchg_error_count", checks.get(5).getCompareToCheckCode());
        Assertions.assertNull(checks.get(5).getExpectedValue());
    }

    @Test
    void createGenericUpdateTimeTaskShouldCreateMetadataWithoutChecksWhenDatasourceMissing() {
        CreateTaskFromTemplateResultVO result =
                service.createTaskFromGenericJdbcStarRocksTemplate(genericRequest(false, "UPDATE_TIME_RANGE"));

        Assertions.assertEquals(1L, result.getTaskId());
        Assertions.assertEquals(2L, result.getVersionId());
        Assertions.assertEquals(3L, result.getIncrementalConfigId());
        Assertions.assertEquals(4L, result.getWatermarkId());
        Assertions.assertEquals(0, result.getCreatedCheckCount());
        Assertions.assertFalse(result.getWarnings().isEmpty());
        Mockito.verify(checkConfigService, Mockito.never()).create(Mockito.any());

        ArgumentCaptor<SyncTaskEntity> taskCaptor = ArgumentCaptor.forClass(SyncTaskEntity.class);
        Mockito.verify(taskService).create(taskCaptor.capture());
        Assertions.assertTrue(taskCaptor.getValue().getIncrementalEnabled());
        Assertions.assertEquals(SyncSourceType.SQL, taskCaptor.getValue().getSourceType());
        Assertions.assertEquals(SyncSinkType.STARROCKS, taskCaptor.getValue().getSinkType());
        Assertions.assertEquals(SyncTaskStatus.PUBLISHED, taskCaptor.getValue().getStatus());

        ArgumentCaptor<SyncTaskVersionEntity> versionCaptor = ArgumentCaptor.forClass(SyncTaskVersionEntity.class);
        Mockito.verify(versionService).create(versionCaptor.capture());
        Assertions.assertTrue(versionCaptor.getValue().getHoconTemplate().contains("${batch_start_time}"));
        Assertions.assertTrue(versionCaptor.getValue().getHoconTemplate().contains("lab_sink_order"));
        Assertions.assertNotNull(versionCaptor.getValue().getHoconHash());

        ArgumentCaptor<SyncIncrementalConfigEntity> configCaptor =
                ArgumentCaptor.forClass(SyncIncrementalConfigEntity.class);
        Mockito.verify(incrementalConfigService).create(configCaptor.capture());
        Assertions.assertEquals(SyncIncrementalStrategy.UPDATE_TIME_RANGE, configCaptor.getValue().getStrategy());
        Assertions.assertEquals(SyncWatermarkValueType.DATETIME, configCaptor.getValue().getWatermarkFieldType());
        Assertions.assertEquals("update_time", configCaptor.getValue().getWatermarkField());

        ArgumentCaptor<SyncWatermarkEntity> watermarkCaptor = ArgumentCaptor.forClass(SyncWatermarkEntity.class);
        Mockito.verify(watermarkService).create(watermarkCaptor.capture());
        Assertions.assertEquals("2026-06-01 00:00:00", watermarkCaptor.getValue().getCurrentValue());
    }

    @Test
    void createGenericIdRangeTaskShouldCreateMetadataAndDefaultChecks() {
        CreateTaskFromTemplateResultVO result =
                service.createTaskFromGenericJdbcStarRocksTemplate(genericRequest(true, "ID_RANGE"));

        Assertions.assertEquals(3, result.getCreatedCheckCount());

        ArgumentCaptor<SyncIncrementalConfigEntity> configCaptor =
                ArgumentCaptor.forClass(SyncIncrementalConfigEntity.class);
        Mockito.verify(incrementalConfigService).create(configCaptor.capture());
        Assertions.assertEquals(SyncIncrementalStrategy.ID_RANGE, configCaptor.getValue().getStrategy());
        Assertions.assertEquals(SyncWatermarkValueType.LONG, configCaptor.getValue().getWatermarkFieldType());
        Assertions.assertEquals("id", configCaptor.getValue().getWatermarkField());

        ArgumentCaptor<SyncCheckConfigEntity> checkCaptor = ArgumentCaptor.forClass(SyncCheckConfigEntity.class);
        Mockito.verify(checkConfigService, Mockito.times(3)).create(checkCaptor.capture());
        List<SyncCheckConfigEntity> checks = checkCaptor.getAllValues();
        Assertions.assertEquals("source_count", checks.get(0).getCheckCode());
        Assertions.assertTrue(checks.get(0).getSqlText().contains("${batch_start_value}"));
        Assertions.assertEquals("sink_count", checks.get(1).getCheckCode());
        Assertions.assertEquals("source_count", checks.get(1).getCompareToCheckCode());
        Assertions.assertEquals(SyncCheckExpectedOperator.EQ, checks.get(1).getExpectedOperator());
        Assertions.assertEquals("error_count", checks.get(2).getCheckCode());
        Assertions.assertEquals("0", checks.get(2).getExpectedValue());
    }

    @Test
    void hoconTemplateShouldRenderWithRuntimeVariablesAndThrowWhenMissing() {
        HoconRenderService renderService = new HoconRenderServiceImpl();
        String template = service.getTemplate(SyncBuiltInTemplateService.FAB_MES_SPC_JDBC_TRANSLATOR).getHoconTemplate();

        String rendered = renderService.render(template, renderVariables());

        Assertions.assertTrue(rendered.contains("batch_1"));
        Assertions.assertTrue(rendered.contains("jdbc:oracle:thin:@//oracle.lab:1521/ORCLPDB1"));

        Map<String, Object> missingVariables = new java.util.LinkedHashMap<>(renderVariables());
        missingVariables.remove("source_password");
        Assertions.assertThrows(ServiceException.class, () -> renderService.render(template, missingVariables));
    }

    @Test
    void loaderPublishTemplateShouldRenderBatchIdsAndThrowWhenXchgBatchMissing() {
        HoconRenderService renderService = new HoconRenderServiceImpl();
        String template = service.getTemplate(SyncBuiltInTemplateService.FAB_LOADER_PUBLISH_XCHG_TO_STG).getHoconTemplate();

        String rendered = renderService.render(template, loaderRenderVariables());

        Assertions.assertTrue(rendered.contains("system_batch_1"));
        Assertions.assertTrue(rendered.contains("translator_batch_1"));
        Assertions.assertTrue(rendered.contains("WHERE batch_id = 'translator_batch_1'"));

        Map<String, Object> missingVariables = new java.util.LinkedHashMap<>(loaderRenderVariables());
        missingVariables.remove("xchg_batch_id");
        Assertions.assertThrows(ServiceException.class, () -> renderService.render(template, missingVariables));
    }

    @Test
    void genericTemplateShouldRenderUpdateTimeAndIdRangeVariables() {
        service.createTaskFromGenericJdbcStarRocksTemplate(genericRequest(true, "UPDATE_TIME_RANGE"));
        ArgumentCaptor<SyncTaskVersionEntity> updateVersionCaptor = ArgumentCaptor.forClass(SyncTaskVersionEntity.class);
        Mockito.verify(versionService).create(updateVersionCaptor.capture());

        HoconRenderService renderService = new HoconRenderServiceImpl();
        String updateRendered = renderService.render(updateVersionCaptor.getValue().getHoconTemplate(), genericRenderVariables());

        Assertions.assertTrue(updateRendered.contains("2026-06-01 00:00:00"));
        Assertions.assertTrue(updateRendered.contains("2026-06-01 01:00:00"));
        Assertions.assertTrue(updateRendered.contains("lab_sink_order"));

        setUp();
        service.createTaskFromGenericJdbcStarRocksTemplate(genericRequest(true, "ID_RANGE"));
        ArgumentCaptor<SyncTaskVersionEntity> idVersionCaptor = ArgumentCaptor.forClass(SyncTaskVersionEntity.class);
        Mockito.verify(versionService).create(idVersionCaptor.capture());
        String idRendered = renderService.render(idVersionCaptor.getValue().getHoconTemplate(), genericRenderVariables());
        Assertions.assertTrue(idRendered.contains("id > 0"));
        Assertions.assertTrue(idRendered.contains("id <= 3"));

        Map<String, Object> missingVariables = new java.util.LinkedHashMap<>(genericRenderVariables());
        missingVariables.remove("starrocks_password");
        Assertions.assertThrows(ServiceException.class,
                () -> renderService.render(idVersionCaptor.getValue().getHoconTemplate(), missingVariables));
    }

    private CreateFabMesSpcJdbcTaskRequest request(boolean withDatasource) {
        CreateFabMesSpcJdbcTaskRequest request = new CreateFabMesSpcJdbcTaskRequest();
        request.setTaskCode("fab_mes_spc_measure");
        request.setTaskName("Fab MES/SPC Measure Translator");
        request.setClientId(1L);
        request.setSourceSystem("SPC");
        request.setWatermarkField("UPDATE_TIME");
        request.setWatermarkFieldType("DATETIME");
        request.setStartValue("2026-01-01 00:00:00");
        request.setLookbackSeconds(300);
        request.setMaxBatchSeconds(3600);
        request.setSourceJdbcUrl("${source_jdbc_url}");
        request.setSourceJdbcDriver("oracle.jdbc.OracleDriver");
        request.setSourceUsername("${source_username}");
        request.setSourcePassword("${source_password}");
        request.setHeaderSourceSql("SELECT * FROM ST_ORDER_HEADER WHERE UPDATE_TIME >= TO_TIMESTAMP('${batch_start_time}', 'YYYY-MM-DD HH24:MI:SS')");
        request.setSiteSourceSql("SELECT * FROM ST_ORDER_ITEM WHERE UPDATE_TIME >= TO_TIMESTAMP('${batch_start_time}', 'YYYY-MM-DD HH24:MI:SS')");
        request.setStarrocksNodeUrls("\"starrocks.lab:8030\"");
        request.setStarrocksBaseUrl("jdbc:mysql://starrocks.lab:9030/");
        request.setStarrocksUsername("${starrocks_username}");
        request.setStarrocksPassword("${starrocks_password}");
        request.setStarrocksDatabase("st_test");
        request.setEnableDefaultChecks(true);
        if (withDatasource) {
            request.setSourceDatasourceId(10L);
            request.setSinkDatasourceId(20L);
        }
        return request;
    }

    private CreateFabLoaderPublishTaskRequest loaderRequest(boolean withDatasource) {
        CreateFabLoaderPublishTaskRequest request = new CreateFabLoaderPublishTaskRequest();
        request.setTaskCode("fab_loader_publish_measure");
        request.setTaskName("Fab Loader Publish Measure");
        request.setClientId(1L);
        request.setSourceTaskCode("fab_mes_spc_measure");
        request.setSourceSystem("SPC");
        request.setBatchIdMode("MANUAL_PARAM");
        request.setStarrocksJdbcUrl("${starrocks_jdbc_url}");
        request.setStarrocksJdbcDriver("com.mysql.cj.jdbc.Driver");
        request.setStarrocksNodeUrls("\"starrocks.lab:8030\"");
        request.setStarrocksBaseUrl("jdbc:mysql://starrocks.lab:9030/");
        request.setStarrocksUsername("${starrocks_username}");
        request.setStarrocksPassword("${starrocks_password}");
        request.setStarrocksDatabase("st_test");
        request.setXchgHeaderTable("xchg_meas_header");
        request.setXchgSiteTable("xchg_meas_site");
        request.setXchgErrorTable("xchg_meas_error");
        request.setStgHeaderTable("eda_stg_measure_header");
        request.setStgSiteTable("eda_stg_measure_site");
        request.setStgErrorTable("eda_stg_measure_error");
        request.setEnableDefaultChecks(true);
        if (withDatasource) {
            request.setStarrocksDatasourceId(20L);
        }
        return request;
    }

    private CreateGenericJdbcStarRocksTaskRequest genericRequest(boolean withDatasource, String strategy) {
        CreateGenericJdbcStarRocksTaskRequest request = new CreateGenericJdbcStarRocksTaskRequest();
        request.setTaskCode("lab_order_" + strategy.toLowerCase());
        request.setTaskName("Lab Order " + strategy);
        request.setClientId(1L);
        request.setIncrementalStrategy(strategy);
        if ("ID_RANGE".equals(strategy)) {
            request.setWatermarkField("id");
            request.setWatermarkFieldType("LONG");
            request.setStartValue("0");
            request.setSourceQuery("SELECT id, biz_no, amount, update_time FROM lab_src_order "
                    + "WHERE id > ${batch_start_value} AND id <= ${batch_end_value}");
        } else {
            request.setWatermarkField("update_time");
            request.setWatermarkFieldType("DATETIME");
            request.setStartValue("2026-06-01 00:00:00");
            request.setLookbackSeconds(0);
            request.setMaxBatchSeconds(3600);
            request.setSourceQuery("SELECT id, biz_no, amount, update_time FROM lab_src_order "
                    + "WHERE update_time >= '${batch_start_time}' AND update_time < '${batch_end_time}'");
        }
        request.setSourceJdbcUrl("${source_jdbc_url}");
        request.setSourceJdbcDriver("com.mysql.cj.jdbc.Driver");
        request.setSourceUsername("${source_username}");
        request.setSourcePassword("${source_password}");
        request.setStarrocksNodeUrls("\"starrocks.lab:8030\"");
        request.setStarrocksBaseUrl("jdbc:mysql://starrocks.lab:9030/");
        request.setStarrocksUsername("${starrocks_username}");
        request.setStarrocksPassword("${starrocks_password}");
        request.setStarrocksDatabase("st_test");
        request.setStarrocksTable("lab_sink_order");
        request.setStarrocksErrorTable("lab_sink_order_error");
        request.setEnableDefaultChecks(true);
        if (withDatasource) {
            request.setSourceDatasourceId(10L);
            request.setSinkDatasourceId(20L);
        }
        return request;
    }

    private Map<String, Object> renderVariables() {
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("batch_id", "batch_1");
        variables.put("run_id", "run_1");
        variables.put("task_code", "fab_mes_spc_measure");
        variables.put("source_system", "SPC");
        variables.put("source_jdbc_url", "jdbc:oracle:thin:@//oracle.lab:1521/ORCLPDB1");
        variables.put("source_jdbc_driver", "oracle.jdbc.OracleDriver");
        variables.put("source_username", "mes_reader");
        variables.put("source_password", "secret");
        variables.put("header_source_sql", "SELECT 'M1' AS meas_id, CURRENT_TIMESTAMP AS update_time FROM DUAL");
        variables.put("site_source_sql", "SELECT 'M1' AS meas_id, 1 AS site_no, CURRENT_TIMESTAMP AS update_time FROM DUAL");
        variables.put("batch_start_time", "2026-06-01 00:00:00");
        variables.put("batch_end_time", "2026-06-01 01:00:00");
        variables.put("batch_start_value", "2026-06-01 00:00:00");
        variables.put("batch_end_value", "2026-06-01 01:00:00");
        variables.put("starrocks_node_urls", "\"starrocks.lab:8030\"");
        variables.put("starrocks_base_url", "jdbc:mysql://starrocks.lab:9030/");
        variables.put("starrocks_username", "etl");
        variables.put("starrocks_password", "secret");
        variables.put("starrocks_database", "st_test");
        return variables;
    }

    private Map<String, Object> loaderRenderVariables() {
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("batch_id", "system_batch_1");
        variables.put("run_id", "run_1");
        variables.put("task_code", "fab_loader_publish_measure");
        variables.put("xchg_batch_id", "translator_batch_1");
        variables.put("source_system", "SPC");
        variables.put("starrocks_jdbc_url", "jdbc:mysql://starrocks.lab:9030/st_test");
        variables.put("starrocks_jdbc_driver", "com.mysql.cj.jdbc.Driver");
        variables.put("starrocks_username", "etl");
        variables.put("starrocks_password", "secret");
        variables.put("starrocks_node_urls", "\"starrocks.lab:8030\"");
        variables.put("starrocks_base_url", "jdbc:mysql://starrocks.lab:9030/");
        variables.put("starrocks_database", "st_test");
        variables.put("xchg_header_table", "xchg_meas_header");
        variables.put("xchg_site_table", "xchg_meas_site");
        variables.put("xchg_error_table", "xchg_meas_error");
        variables.put("stg_header_table", "eda_stg_measure_header");
        variables.put("stg_site_table", "eda_stg_measure_site");
        variables.put("stg_error_table", "eda_stg_measure_error");
        return variables;
    }

    private Map<String, Object> genericRenderVariables() {
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("batch_id", "batch_1");
        variables.put("run_id", "run_1");
        variables.put("task_code", "lab_order_sync");
        variables.put("source_jdbc_url", "jdbc:mysql://starrocks.lab:9030/st_test");
        variables.put("source_jdbc_driver", "com.mysql.cj.jdbc.Driver");
        variables.put("source_username", "st_lab");
        variables.put("source_password", "secret");
        variables.put("batch_start_time", "2026-06-01 00:00:00");
        variables.put("batch_end_time", "2026-06-01 01:00:00");
        variables.put("batch_start_value", "0");
        variables.put("batch_end_value", "3");
        variables.put("starrocks_node_urls", "\"starrocks.lab:8030\"");
        variables.put("starrocks_base_url", "jdbc:mysql://starrocks.lab:9030/");
        variables.put("starrocks_username", "st_lab");
        variables.put("starrocks_password", "secret");
        variables.put("starrocks_database", "st_test");
        variables.put("starrocks_table", "lab_sink_order");
        variables.put("starrocks_error_table", "lab_sink_order_error");
        return variables;
    }
}
