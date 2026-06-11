package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.api.service.SyncBuiltInTemplateService;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.api.service.SyncIncrementalConfigService;
import org.apache.seatunnel.web.api.service.SyncTaskService;
import org.apache.seatunnel.web.api.service.SyncTaskVersionService;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.common.constants.SyncConstants;
import org.apache.seatunnel.web.common.enums.SyncCheckDatasourceType;
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
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskVersionEntity;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.spi.bean.dto.CreateFabMesSpcJdbcTaskRequest;
import org.apache.seatunnel.web.spi.bean.vo.CreateTaskFromTemplateResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBuiltInTemplateVO;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncBuiltInTemplateServiceImpl
        extends SyncServiceSupport
        implements SyncBuiltInTemplateService {

    private static final String TEMPLATE_RESOURCE =
            "sync/templates/fab_mes_spc_jdbc_translator.conf";

    @Resource
    private SyncTaskService syncTaskService;

    @Resource
    private SyncTaskVersionService syncTaskVersionService;

    @Resource
    private SyncIncrementalConfigService syncIncrementalConfigService;

    @Resource
    private SyncWatermarkService syncWatermarkService;

    @Resource
    private SyncCheckConfigService syncCheckConfigService;

    @Resource
    private HoconRenderService hoconRenderService;

    @Override
    public List<SyncBuiltInTemplateVO> listTemplates() {
        return List.of(fabMesSpcTemplate(false));
    }

    @Override
    public SyncBuiltInTemplateVO getTemplate(String templateCode) {
        if (!FAB_MES_SPC_JDBC_TRANSLATOR.equalsIgnoreCase(templateCode)) {
            throw new ServiceException("Unknown sync built-in template: " + templateCode);
        }
        return fabMesSpcTemplate(true);
    }

    @Override
    @Transactional
    public CreateTaskFromTemplateResultVO createTaskFromFabMesSpcJdbcTemplate(
            CreateFabMesSpcJdbcTaskRequest request
    ) {
        if (request == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "request");
        }
        if (isBlank(request.getTaskCode())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskCode");
        }
        if (isBlank(request.getTaskName())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskName");
        }
        if (request.getClientId() == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "clientId");
        }
        if (isBlank(request.getStartValue())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "startValue");
        }
        if (syncTaskService.getByTaskCode(request.getTaskCode()) != null) {
            throw new ServiceException("Sync task already exists, taskCode=" + request.getTaskCode());
        }

        SyncSourceType sourceType = parseEnum(
                SyncSourceType.class,
                request.getSourceType(),
                SyncSourceType.JDBC,
                "sourceType"
        );
        SyncSinkType sinkType = parseEnum(
                SyncSinkType.class,
                request.getSinkType(),
                SyncSinkType.STARROCKS,
                "sinkType"
        );
        SyncEngineType engineType = parseEnum(
                SyncEngineType.class,
                request.getEngineType(),
                SyncEngineType.ZETA,
                "engineType"
        );
        SyncIncrementalStrategy strategy = parseEnum(
                SyncIncrementalStrategy.class,
                request.getIncrementalStrategy(),
                SyncIncrementalStrategy.UPDATE_TIME_RANGE,
                "incrementalStrategy"
        );
        SyncWatermarkValueType watermarkValueType = parseEnum(
                SyncWatermarkValueType.class,
                request.getWatermarkFieldType(),
                SyncWatermarkValueType.DATETIME,
                "watermarkFieldType"
        );
        if (strategy != SyncIncrementalStrategy.UPDATE_TIME_RANGE) {
            throw new ServiceException("Fab MES/SPC JDBC template supports UPDATE_TIME_RANGE only");
        }
        if (sourceType != SyncSourceType.JDBC && sourceType != SyncSourceType.SQL) {
            throw new ServiceException("Fab MES/SPC JDBC template supports JDBC or SQL source only");
        }

        SyncTaskEntity task = SyncTaskEntity.builder()
                .taskCode(request.getTaskCode())
                .taskName(request.getTaskName())
                .taskType(SyncTaskType.BATCH)
                .sourceType(sourceType)
                .sinkType(sinkType)
                .engineType(engineType)
                .clientId(request.getClientId())
                .incrementalEnabled(true)
                .incrementalStrategy(strategy)
                .status(SyncTaskStatus.DRAFT)
                .description(request.getDescription())
                .build();
        Long taskId = syncTaskService.create(task);
        task.setId(taskId);

        String hoconTemplate = instantiateTemplate(loadFabMesSpcTemplate(), request);
        SyncTaskVersionEntity version = SyncTaskVersionEntity.builder()
                .taskId(taskId)
                .versionNo(1)
                .hoconTemplate(hoconTemplate)
                .hoconHash(hoconRenderService.calculateHash(hoconTemplate))
                .paramSchemaJson(paramSchemaJson())
                .publishStatus(SyncPublishStatus.PUBLISHED)
                .build();
        Long versionId = syncTaskVersionService.create(version);
        version.setId(versionId);

        task.setCurrentVersionId(versionId);
        syncTaskService.update(task);

        SyncIncrementalConfigEntity incrementalConfig = SyncIncrementalConfigEntity.builder()
                .taskId(taskId)
                .sourceType(sourceType)
                .strategy(strategy)
                .watermarkKey(SyncConstants.DEFAULT_WATERMARK_KEY)
                .watermarkField(isBlank(request.getWatermarkField()) ? "UPDATE_TIME" : request.getWatermarkField())
                .watermarkFieldType(watermarkValueType)
                .startValue(request.getStartValue())
                .lookbackSeconds(request.getLookbackSeconds())
                .maxBatchSeconds(request.getMaxBatchSeconds())
                .build();
        Long incrementalConfigId = syncIncrementalConfigService.create(incrementalConfig);

        SyncWatermarkEntity watermark = SyncWatermarkEntity.builder()
                .taskId(taskId)
                .watermarkKey(SyncConstants.DEFAULT_WATERMARK_KEY)
                .currentValue(request.getStartValue())
                .currentValueType(watermarkValueType)
                .build();
        Long watermarkId = syncWatermarkService.create(watermark);

        List<String> warnings = new ArrayList<>();
        int createdCheckCount = createDefaultChecksIfNeeded(taskId, request, warnings);

        CreateTaskFromTemplateResultVO result = new CreateTaskFromTemplateResultVO();
        result.setTaskId(taskId);
        result.setTaskCode(task.getTaskCode());
        result.setVersionId(versionId);
        result.setIncrementalConfigId(incrementalConfigId);
        result.setWatermarkId(watermarkId);
        result.setCreatedCheckCount(createdCheckCount);
        result.setWarnings(warnings);
        result.setNextActions(List.of(
                "1. Call preview-hocon with runtime params.",
                "2. Create StarRocks xchg tables from docs/sql/fab_mes_spc_xchg_starrocks.sql.",
                "3. Call run API after verifying source/sink datasource and StarRocks xchg DDL."
        ));
        return result;
    }

    private SyncBuiltInTemplateVO fabMesSpcTemplate(boolean includeTemplate) {
        SyncBuiltInTemplateVO vo = new SyncBuiltInTemplateVO();
        vo.setTemplateCode(FAB_MES_SPC_JDBC_TRANSLATOR);
        vo.setTemplateName("Fab MES/SPC JDBC Translator");
        vo.setDescription("Fab MES/SPC JDBC incremental translator to StarRocks xchg tables.");
        vo.setSourceType(SyncSourceType.JDBC.getCode());
        vo.setSinkType(SyncSinkType.STARROCKS.getCode());
        vo.setIncrementalStrategy(SyncIncrementalStrategy.UPDATE_TIME_RANGE.getCode());
        vo.setRequiredVariables(requiredVariables());
        vo.setDocs(List.of(
                "docs/fab-mes-spc-translator-template.md",
                "docs/templates/fab_mes_spc_jdbc_translator.conf",
                "docs/sql/fab_mes_spc_xchg_starrocks.sql"
        ));
        if (includeTemplate) {
            vo.setHoconTemplate(loadFabMesSpcTemplate());
        }
        return vo;
    }

    private int createDefaultChecksIfNeeded(
            Long taskId,
            CreateFabMesSpcJdbcTaskRequest request,
            List<String> warnings
    ) {
        boolean enableDefaultChecks = request.getEnableDefaultChecks() == null
                || Boolean.TRUE.equals(request.getEnableDefaultChecks());
        if (!enableDefaultChecks) {
            return 0;
        }
        if (request.getSourceDatasourceId() == null || request.getSinkDatasourceId() == null) {
            warnings.add("Default checks were not created because sourceDatasourceId or sinkDatasourceId is empty.");
            return 0;
        }

        String headerSql = sqlOrPlaceholder(request.getHeaderSourceSql(), "header_source_sql");
        List<SyncCheckConfigEntity> checks = List.of(
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("source_count")
                        .checkName("Source header count")
                        .checkType(SyncCheckType.SOURCE_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SOURCE)
                        .datasourceId(request.getSourceDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM (\n" + stripTrailingSemicolon(headerSql) + "\n) t")
                        .expectedOperator(SyncCheckExpectedOperator.GE)
                        .expectedValue("0")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(10)
                        .description("Counts source header rows for this incremental batch.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("sink_header_count")
                        .checkName("Sink header count")
                        .checkType(SyncCheckType.SINK_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SINK)
                        .datasourceId(request.getSinkDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM xchg_meas_header\nWHERE batch_id = '${batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.EQ)
                        .compareToCheckCode("source_count")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(20)
                        .description("Compares StarRocks header rows with source_count.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("sink_site_count")
                        .checkName("Sink site count")
                        .checkType(SyncCheckType.CUSTOM_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SINK)
                        .datasourceId(request.getSinkDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM xchg_meas_site\nWHERE batch_id = '${batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.GE)
                        .expectedValue("0")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(30)
                        .description("Records StarRocks site row count; it is not expected to equal header_count.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("error_count")
                        .checkName("Translator error count")
                        .checkType(SyncCheckType.ERROR_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SINK)
                        .datasourceId(request.getSinkDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM xchg_meas_error\nWHERE batch_id = '${batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.EQ)
                        .expectedValue("0")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(40)
                        .description("Fails the run if translator validation writes error rows.")
                        .build()
        );

        for (SyncCheckConfigEntity check : checks) {
            syncCheckConfigService.create(check);
        }
        return checks.size();
    }

    private String instantiateTemplate(String template, CreateFabMesSpcJdbcTaskRequest request) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("source_system", valueOrPlaceholder(request.getSourceSystem(), "source_system"));
        replacements.put("source_jdbc_url", valueOrPlaceholder(request.getSourceJdbcUrl(), "source_jdbc_url"));
        replacements.put("source_jdbc_driver", valueOrDefault(request.getSourceJdbcDriver(), "oracle.jdbc.OracleDriver"));
        replacements.put("source_username", valueOrPlaceholder(request.getSourceUsername(), "source_username"));
        replacements.put("source_password", valueOrPlaceholder(request.getSourcePassword(), "source_password"));
        replacements.put("header_source_sql", sqlOrPlaceholder(request.getHeaderSourceSql(), "header_source_sql"));
        replacements.put("site_source_sql", sqlOrPlaceholder(request.getSiteSourceSql(), "site_source_sql"));
        replacements.put("starrocks_node_urls", valueOrPlaceholder(request.getStarrocksNodeUrls(), "starrocks_node_urls"));
        replacements.put("starrocks_base_url", valueOrPlaceholder(request.getStarrocksBaseUrl(), "starrocks_base_url"));
        replacements.put("starrocks_username", valueOrPlaceholder(request.getStarrocksUsername(), "starrocks_username"));
        replacements.put("starrocks_password", valueOrPlaceholder(request.getStarrocksPassword(), "starrocks_password"));
        replacements.put("starrocks_database", valueOrPlaceholder(request.getStarrocksDatabase(), "starrocks_database"));

        String renderedTemplate = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            renderedTemplate = renderedTemplate.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return renderedTemplate;
    }

    private String paramSchemaJson() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("templateCode", FAB_MES_SPC_JDBC_TRANSLATOR);
        schema.put("description", "Runtime and credential variables required by the Fab MES/SPC JDBC translator template.");
        schema.put("requiredVariables", requiredVariables());
        schema.put("runtimeVariables", List.of(
                "batch_id",
                "run_id",
                "task_code",
                "batch_start_time",
                "batch_end_time",
                "batch_start_value",
                "batch_end_value"
        ));
        schema.put("sensitiveVariables", List.of(
                "source_password",
                "starrocks_password",
                "source_username",
                "starrocks_username"
        ));
        return JSONUtils.toJsonString(schema);
    }

    private List<String> requiredVariables() {
        return List.of(
                "batch_id",
                "run_id",
                "task_code",
                "source_system",
                "source_jdbc_url",
                "source_jdbc_driver",
                "source_username",
                "source_password",
                "header_source_sql",
                "site_source_sql",
                "batch_start_time",
                "batch_end_time",
                "batch_start_value",
                "batch_end_value",
                "starrocks_node_urls",
                "starrocks_base_url",
                "starrocks_username",
                "starrocks_password",
                "starrocks_database"
        );
    }

    private String loadFabMesSpcTemplate() {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ServiceException("Load built-in template failed: " + TEMPLATE_RESOURCE, e);
        }
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, T defaultValue, String fieldName) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("Unsupported " + fieldName + ": " + value);
        }
    }

    private String valueOrPlaceholder(String value, String variableName) {
        if (isBlank(value)) {
            return "${" + variableName + "}";
        }
        return value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        return value;
    }

    private String sqlOrPlaceholder(String value, String variableName) {
        return valueOrPlaceholder(value, variableName);
    }

    private String stripTrailingSemicolon(String sql) {
        String result = sql == null ? "" : sql.trim();
        while (result.endsWith(";")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }
}
