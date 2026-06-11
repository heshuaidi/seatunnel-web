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
import org.apache.seatunnel.web.spi.bean.dto.CreateFabLoaderPublishTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.CreateFabMesSpcJdbcTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.CreateGenericJdbcStarRocksTaskRequest;
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

    private static final String FAB_MES_SPC_TEMPLATE_RESOURCE =
            "sync/templates/fab_mes_spc_jdbc_translator.conf";

    private static final String FAB_LOADER_PUBLISH_TEMPLATE_RESOURCE =
            "sync/templates/fab_loader_publish_xchg_to_stg.conf";

    private static final String GENERIC_JDBC_STARROCKS_TEMPLATE_RESOURCE =
            "sync/templates/generic_jdbc_sql_to_starrocks_incremental.conf";

    private static final String BATCH_ID_MODE_MANUAL_PARAM = "MANUAL_PARAM";

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
        return List.of(
                fabMesSpcTemplate(false),
                fabLoaderPublishTemplate(false),
                genericJdbcStarRocksTemplate(false)
        );
    }

    @Override
    public SyncBuiltInTemplateVO getTemplate(String templateCode) {
        if (FAB_MES_SPC_JDBC_TRANSLATOR.equalsIgnoreCase(templateCode)) {
            return fabMesSpcTemplate(true);
        }
        if (FAB_LOADER_PUBLISH_XCHG_TO_STG.equalsIgnoreCase(templateCode)) {
            return fabLoaderPublishTemplate(true);
        }
        if (GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL.equalsIgnoreCase(templateCode)) {
            return genericJdbcStarRocksTemplate(true);
        }
        throw new ServiceException("Unknown sync built-in template: " + templateCode);
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
                .paramSchemaJson(fabMesSpcParamSchemaJson())
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

    @Override
    @Transactional
    public CreateTaskFromTemplateResultVO createTaskFromFabLoaderPublishTemplate(
            CreateFabLoaderPublishTaskRequest request
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
        String batchIdMode = valueOrDefault(request.getBatchIdMode(), BATCH_ID_MODE_MANUAL_PARAM);
        if (!BATCH_ID_MODE_MANUAL_PARAM.equalsIgnoreCase(batchIdMode)) {
            throw new ServiceException("Fab loader publish template supports MANUAL_PARAM batchIdMode only");
        }
        if (syncTaskService.getByTaskCode(request.getTaskCode()) != null) {
            throw new ServiceException("Sync task already exists, taskCode=" + request.getTaskCode());
        }

        SyncTaskEntity task = SyncTaskEntity.builder()
                .taskCode(request.getTaskCode())
                .taskName(request.getTaskName())
                .taskType(SyncTaskType.BATCH)
                .sourceType(SyncSourceType.SQL)
                .sinkType(SyncSinkType.STARROCKS)
                .engineType(SyncEngineType.ZETA)
                .clientId(request.getClientId())
                .incrementalEnabled(false)
                .incrementalStrategy(null)
                .status(SyncTaskStatus.PUBLISHED)
                .description(request.getDescription())
                .build();
        Long taskId = syncTaskService.create(task);
        task.setId(taskId);

        String hoconTemplate = instantiateLoaderPublishTemplate(loadLoaderPublishTemplate(), request);
        SyncTaskVersionEntity version = SyncTaskVersionEntity.builder()
                .taskId(taskId)
                .versionNo(1)
                .hoconTemplate(hoconTemplate)
                .hoconHash(hoconRenderService.calculateHash(hoconTemplate))
                .paramSchemaJson(loaderPublishParamSchemaJson(request))
                .publishStatus(SyncPublishStatus.PUBLISHED)
                .build();
        Long versionId = syncTaskVersionService.create(version);
        version.setId(versionId);

        task.setCurrentVersionId(versionId);
        syncTaskService.update(task);

        List<String> warnings = new ArrayList<>();
        int createdCheckCount = createLoaderPublishChecksIfNeeded(taskId, request, warnings);

        CreateTaskFromTemplateResultVO result = new CreateTaskFromTemplateResultVO();
        result.setTaskId(taskId);
        result.setTaskCode(task.getTaskCode());
        result.setVersionId(versionId);
        result.setIncrementalConfigId(null);
        result.setWatermarkId(null);
        result.setCreatedCheckCount(createdCheckCount);
        result.setWarnings(warnings);
        result.setNextActions(List.of(
                "1. Create StarRocks stg/dwd/ads tables from docs/sql/fab_loader_publish_starrocks.sql.",
                "2. Call preview-hocon with xchg_batch_id and runtime credentials if placeholders remain.",
                "3. Call run API with params.xchg_batch_id to publish one translator batch to eda_stg.",
                "4. Execute docs/sql/fab_loader_publish_dwd_ads.sql separately when dwd/ads publish is required."
        ));
        return result;
    }

    @Override
    @Transactional
    public CreateTaskFromTemplateResultVO createTaskFromGenericJdbcStarRocksTemplate(
            CreateGenericJdbcStarRocksTaskRequest request
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
        if (isBlank(request.getSourceQuery())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "sourceQuery");
        }
        if (syncTaskService.getByTaskCode(request.getTaskCode()) != null) {
            throw new ServiceException("Sync task already exists, taskCode=" + request.getTaskCode());
        }

        SyncIncrementalStrategy strategy = parseEnum(
                SyncIncrementalStrategy.class,
                request.getIncrementalStrategy(),
                SyncIncrementalStrategy.UPDATE_TIME_RANGE,
                "incrementalStrategy"
        );
        if (strategy != SyncIncrementalStrategy.UPDATE_TIME_RANGE && strategy != SyncIncrementalStrategy.ID_RANGE) {
            throw new ServiceException("Generic JDBC StarRocks template supports UPDATE_TIME_RANGE or ID_RANGE only");
        }
        SyncWatermarkValueType watermarkValueType = parseEnum(
                SyncWatermarkValueType.class,
                request.getWatermarkFieldType(),
                strategy == SyncIncrementalStrategy.ID_RANGE
                        ? SyncWatermarkValueType.LONG
                        : SyncWatermarkValueType.DATETIME,
                "watermarkFieldType"
        );

        SyncTaskEntity task = SyncTaskEntity.builder()
                .taskCode(request.getTaskCode())
                .taskName(request.getTaskName())
                .taskType(SyncTaskType.BATCH)
                .sourceType(SyncSourceType.SQL)
                .sinkType(SyncSinkType.STARROCKS)
                .engineType(SyncEngineType.ZETA)
                .clientId(request.getClientId())
                .incrementalEnabled(true)
                .incrementalStrategy(strategy)
                .status(SyncTaskStatus.PUBLISHED)
                .description(request.getDescription())
                .build();
        Long taskId = syncTaskService.create(task);
        task.setId(taskId);

        String hoconTemplate = instantiateGenericJdbcStarRocksTemplate(loadGenericJdbcStarRocksTemplate(), request);
        SyncTaskVersionEntity version = SyncTaskVersionEntity.builder()
                .taskId(taskId)
                .versionNo(1)
                .hoconTemplate(hoconTemplate)
                .hoconHash(hoconRenderService.calculateHash(hoconTemplate))
                .paramSchemaJson(genericJdbcStarRocksParamSchemaJson(strategy, request))
                .publishStatus(SyncPublishStatus.PUBLISHED)
                .build();
        Long versionId = syncTaskVersionService.create(version);
        version.setId(versionId);

        task.setCurrentVersionId(versionId);
        syncTaskService.update(task);

        SyncIncrementalConfigEntity incrementalConfig = SyncIncrementalConfigEntity.builder()
                .taskId(taskId)
                .sourceType(SyncSourceType.SQL)
                .strategy(strategy)
                .watermarkKey(SyncConstants.DEFAULT_WATERMARK_KEY)
                .watermarkField(defaultWatermarkField(request, strategy))
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
        int createdCheckCount = createGenericJdbcStarRocksChecksIfNeeded(taskId, request, warnings);

        CreateTaskFromTemplateResultVO result = new CreateTaskFromTemplateResultVO();
        result.setTaskId(taskId);
        result.setTaskCode(task.getTaskCode());
        result.setVersionId(versionId);
        result.setIncrementalConfigId(incrementalConfigId);
        result.setWatermarkId(watermarkId);
        result.setCreatedCheckCount(createdCheckCount);
        result.setWarnings(warnings);
        result.setNextActions(List.of(
                "1. Create lab tables from docs/sql/generic_jdbc_sql_incremental_starrocks_lab.sql.",
                "2. Call preview-hocon with runtime credentials and, for ID_RANGE, batchEndValue.",
                "3. Call run API and verify run/check/watermark results.",
                "4. Use backfill API to validate failure-safe watermark behavior before production rollout."
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
        vo.setRequiredVariables(fabMesSpcRequiredVariables());
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

    private SyncBuiltInTemplateVO fabLoaderPublishTemplate(boolean includeTemplate) {
        SyncBuiltInTemplateVO vo = new SyncBuiltInTemplateVO();
        vo.setTemplateCode(FAB_LOADER_PUBLISH_XCHG_TO_STG);
        vo.setTemplateName("Fab Loader Publish XCHG to STG");
        vo.setDescription("Fab loader publish template from StarRocks xchg measure tables to eda_stg tables.");
        vo.setSourceType(SyncSourceType.SQL.getCode());
        vo.setSinkType(SyncSinkType.STARROCKS.getCode());
        vo.setIncrementalStrategy(null);
        vo.setRequiredVariables(loaderPublishRequiredVariables());
        vo.setDocs(List.of(
                "docs/fab-loader-publish-template.md",
                "docs/templates/fab_loader_publish_xchg_to_stg.conf",
                "docs/sql/fab_loader_publish_starrocks.sql",
                "docs/sql/fab_loader_publish_dwd_ads.sql"
        ));
        if (includeTemplate) {
            vo.setHoconTemplate(loadLoaderPublishTemplate());
        }
        return vo;
    }

    private SyncBuiltInTemplateVO genericJdbcStarRocksTemplate(boolean includeTemplate) {
        SyncBuiltInTemplateVO vo = new SyncBuiltInTemplateVO();
        vo.setTemplateCode(GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL);
        vo.setTemplateName("Generic JDBC/SQL to StarRocks Incremental");
        vo.setDescription("Generic JDBC/SQL incremental batch source to StarRocks sink for testing batch, watermark, audit and verification.");
        vo.setSourceType(SyncSourceType.SQL.getCode());
        vo.setSinkType(SyncSinkType.STARROCKS.getCode());
        vo.setIncrementalStrategy("UPDATE_TIME_RANGE/ID_RANGE");
        vo.setRequiredVariables(genericJdbcStarRocksRequiredVariables());
        vo.setDocs(List.of(
                "docs/generic-jdbc-starrocks-incremental-test.md",
                "docs/templates/generic_jdbc_sql_to_starrocks_incremental.conf",
                "docs/sql/generic_jdbc_sql_incremental_starrocks_lab.sql"
        ));
        if (includeTemplate) {
            vo.setHoconTemplate(loadGenericJdbcStarRocksTemplate());
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

    private int createLoaderPublishChecksIfNeeded(
            Long taskId,
            CreateFabLoaderPublishTaskRequest request,
            List<String> warnings
    ) {
        boolean enableDefaultChecks = request.getEnableDefaultChecks() == null
                || Boolean.TRUE.equals(request.getEnableDefaultChecks());
        if (!enableDefaultChecks) {
            return 0;
        }
        if (request.getStarrocksDatasourceId() == null) {
            warnings.add("Default checks were not created because starrocksDatasourceId is empty.");
            return 0;
        }

        String xchgHeaderTable = valueOrDefault(request.getXchgHeaderTable(), "xchg_meas_header");
        String xchgSiteTable = valueOrDefault(request.getXchgSiteTable(), "xchg_meas_site");
        String xchgErrorTable = valueOrDefault(request.getXchgErrorTable(), "xchg_meas_error");
        String stgHeaderTable = valueOrDefault(request.getStgHeaderTable(), "eda_stg_measure_header");
        String stgSiteTable = valueOrDefault(request.getStgSiteTable(), "eda_stg_measure_site");
        String stgErrorTable = valueOrDefault(request.getStgErrorTable(), "eda_stg_measure_error");

        List<SyncCheckConfigEntity> checks = List.of(
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("xchg_header_count")
                        .checkName("XCHG header count")
                        .checkType(SyncCheckType.SOURCE_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SOURCE)
                        .datasourceId(request.getStarrocksDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM " + xchgHeaderTable + "\nWHERE batch_id = '${xchg_batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.GE)
                        .expectedValue("0")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(10)
                        .description("Counts xchg header rows for the translator batch.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("stg_header_count")
                        .checkName("STG header count")
                        .checkType(SyncCheckType.SINK_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SINK)
                        .datasourceId(request.getStarrocksDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM " + stgHeaderTable + "\nWHERE batch_id = '${xchg_batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.EQ)
                        .compareToCheckCode("xchg_header_count")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(20)
                        .description("Compares eda_stg header rows with xchg_header_count.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("xchg_site_count")
                        .checkName("XCHG site count")
                        .checkType(SyncCheckType.CUSTOM_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SOURCE)
                        .datasourceId(request.getStarrocksDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM " + xchgSiteTable + "\nWHERE batch_id = '${xchg_batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.GE)
                        .expectedValue("0")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(30)
                        .description("Counts xchg site rows for the translator batch.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("stg_site_count")
                        .checkName("STG site count")
                        .checkType(SyncCheckType.CUSTOM_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SINK)
                        .datasourceId(request.getStarrocksDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM " + stgSiteTable + "\nWHERE batch_id = '${xchg_batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.EQ)
                        .compareToCheckCode("xchg_site_count")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(40)
                        .description("Compares eda_stg site rows with xchg_site_count.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("xchg_error_count")
                        .checkName("XCHG error count")
                        .checkType(SyncCheckType.CUSTOM_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SOURCE)
                        .datasourceId(request.getStarrocksDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM " + xchgErrorTable + "\nWHERE batch_id = '${xchg_batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.GE)
                        .expectedValue("0")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(50)
                        .description("Counts xchg error rows; loader publish should carry them forward.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("stg_error_count")
                        .checkName("STG error count")
                        .checkType(SyncCheckType.CUSTOM_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SINK)
                        .datasourceId(request.getStarrocksDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM " + stgErrorTable + "\nWHERE batch_id = '${xchg_batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.EQ)
                        .compareToCheckCode("xchg_error_count")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(60)
                        .description("Compares eda_stg error rows with xchg_error_count.")
                        .build()
        );

        for (SyncCheckConfigEntity check : checks) {
            syncCheckConfigService.create(check);
        }
        return checks.size();
    }

    private int createGenericJdbcStarRocksChecksIfNeeded(
            Long taskId,
            CreateGenericJdbcStarRocksTaskRequest request,
            List<String> warnings
    ) {
        boolean enableDefaultChecks = request.getEnableDefaultChecks() == null
                || Boolean.TRUE.equals(request.getEnableDefaultChecks());
        if (!enableDefaultChecks) {
            return 0;
        }
        if (request.getSourceDatasourceId() == null || request.getSinkDatasourceId() == null) {
            warnings.add("sourceDatasourceId or sinkDatasourceId is missing, default checks are skipped.");
            return 0;
        }

        String sourceQuery = stripTrailingSemicolon(request.getSourceQuery());
        String starrocksTable = valueOrDefault(request.getStarrocksTable(), "lab_sink_order");
        String starrocksErrorTable = valueOrDefault(request.getStarrocksErrorTable(), "lab_sink_order_error");
        List<SyncCheckConfigEntity> checks = List.of(
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("source_count")
                        .checkName("Source count")
                        .checkType(SyncCheckType.SOURCE_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SOURCE)
                        .datasourceId(request.getSourceDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM (\n" + sourceQuery + "\n) t")
                        .expectedOperator(SyncCheckExpectedOperator.GE)
                        .expectedValue("0")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(10)
                        .description("Counts source rows in the current incremental range.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("sink_count")
                        .checkName("Sink count")
                        .checkType(SyncCheckType.SINK_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SINK)
                        .datasourceId(request.getSinkDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM " + starrocksTable + "\nWHERE batch_id = '${batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.EQ)
                        .compareToCheckCode("source_count")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(20)
                        .description("Compares StarRocks sink rows with source_count.")
                        .build(),
                SyncCheckConfigEntity.builder()
                        .taskId(taskId)
                        .checkCode("error_count")
                        .checkName("Error count")
                        .checkType(SyncCheckType.ERROR_COUNT)
                        .datasourceType(SyncCheckDatasourceType.SINK)
                        .datasourceId(request.getSinkDatasourceId())
                        .sqlText("SELECT COUNT(*)\nFROM " + starrocksErrorTable + "\nWHERE batch_id = '${batch_id}'")
                        .expectedOperator(SyncCheckExpectedOperator.EQ)
                        .expectedValue("0")
                        .failOnMismatch(true)
                        .enabled(true)
                        .sortOrder(30)
                        .description("Fails the run if validation error rows are written.")
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

    private String instantiateLoaderPublishTemplate(String template, CreateFabLoaderPublishTaskRequest request) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("source_system", valueOrPlaceholder(request.getSourceSystem(), "source_system"));
        replacements.put("starrocks_jdbc_url", valueOrPlaceholder(request.getStarrocksJdbcUrl(), "starrocks_jdbc_url"));
        replacements.put("starrocks_jdbc_driver", valueOrDefault(request.getStarrocksJdbcDriver(), "com.mysql.cj.jdbc.Driver"));
        replacements.put("starrocks_node_urls", valueOrPlaceholder(request.getStarrocksNodeUrls(), "starrocks_node_urls"));
        replacements.put("starrocks_base_url", valueOrPlaceholder(request.getStarrocksBaseUrl(), "starrocks_base_url"));
        replacements.put("starrocks_username", valueOrPlaceholder(request.getStarrocksUsername(), "starrocks_username"));
        replacements.put("starrocks_password", valueOrPlaceholder(request.getStarrocksPassword(), "starrocks_password"));
        replacements.put("starrocks_database", valueOrPlaceholder(request.getStarrocksDatabase(), "starrocks_database"));
        replacements.put("xchg_header_table", valueOrDefault(request.getXchgHeaderTable(), "xchg_meas_header"));
        replacements.put("xchg_site_table", valueOrDefault(request.getXchgSiteTable(), "xchg_meas_site"));
        replacements.put("xchg_error_table", valueOrDefault(request.getXchgErrorTable(), "xchg_meas_error"));
        replacements.put("stg_header_table", valueOrDefault(request.getStgHeaderTable(), "eda_stg_measure_header"));
        replacements.put("stg_site_table", valueOrDefault(request.getStgSiteTable(), "eda_stg_measure_site"));
        replacements.put("stg_error_table", valueOrDefault(request.getStgErrorTable(), "eda_stg_measure_error"));

        String renderedTemplate = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            renderedTemplate = renderedTemplate.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return renderedTemplate;
    }

    private String instantiateGenericJdbcStarRocksTemplate(
            String template,
            CreateGenericJdbcStarRocksTaskRequest request
    ) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("source_jdbc_url", valueOrPlaceholder(request.getSourceJdbcUrl(), "source_jdbc_url"));
        replacements.put("source_jdbc_driver", valueOrDefault(request.getSourceJdbcDriver(), "com.mysql.cj.jdbc.Driver"));
        replacements.put("source_username", valueOrPlaceholder(request.getSourceUsername(), "source_username"));
        replacements.put("source_password", valueOrPlaceholder(request.getSourcePassword(), "source_password"));
        replacements.put("source_query", request.getSourceQuery());
        replacements.put("starrocks_node_urls", valueOrPlaceholder(request.getStarrocksNodeUrls(), "starrocks_node_urls"));
        replacements.put("starrocks_base_url", valueOrPlaceholder(request.getStarrocksBaseUrl(), "starrocks_base_url"));
        replacements.put("starrocks_username", valueOrPlaceholder(request.getStarrocksUsername(), "starrocks_username"));
        replacements.put("starrocks_password", valueOrPlaceholder(request.getStarrocksPassword(), "starrocks_password"));
        replacements.put("starrocks_database", valueOrPlaceholder(request.getStarrocksDatabase(), "starrocks_database"));
        replacements.put("starrocks_table", valueOrDefault(request.getStarrocksTable(), "lab_sink_order"));
        replacements.put("starrocks_error_table", valueOrDefault(request.getStarrocksErrorTable(), "lab_sink_order_error"));

        String renderedTemplate = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            renderedTemplate = renderedTemplate.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return renderedTemplate;
    }

    private String fabMesSpcParamSchemaJson() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("templateCode", FAB_MES_SPC_JDBC_TRANSLATOR);
        schema.put("description", "Runtime and credential variables required by the Fab MES/SPC JDBC translator template.");
        schema.put("requiredVariables", fabMesSpcRequiredVariables());
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

    private String loaderPublishParamSchemaJson(CreateFabLoaderPublishTaskRequest request) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("templateCode", FAB_LOADER_PUBLISH_XCHG_TO_STG);
        schema.put("description", "Runtime variables required by the Fab loader publish xchg-to-stg template.");
        schema.put("sourceTaskCode", request.getSourceTaskCode());
        schema.put("batchIdMode", valueOrDefault(request.getBatchIdMode(), BATCH_ID_MODE_MANUAL_PARAM));
        schema.put("requiredVariables", loaderPublishRequiredVariables());
        schema.put("runtimeVariables", List.of(
                "batch_id",
                "run_id",
                "task_code",
                "xchg_batch_id"
        ));
        schema.put("sensitiveVariables", List.of(
                "starrocks_username",
                "starrocks_password"
        ));
        schema.put("notes", List.of(
                "batch_id is the seatunnel-web sync batch id.",
                "xchg_batch_id is the business translator batch id used to filter xchg/stg rows.",
                "Only MANUAL_PARAM batchIdMode is implemented in this version."
        ));
        return JSONUtils.toJsonString(schema);
    }

    private String genericJdbcStarRocksParamSchemaJson(
            SyncIncrementalStrategy strategy,
            CreateGenericJdbcStarRocksTaskRequest request
    ) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("templateCode", GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL);
        schema.put("description", "Runtime variables required by the generic JDBC/SQL to StarRocks incremental template.");
        schema.put("incrementalStrategy", strategy.getCode());
        schema.put("watermarkField", defaultWatermarkField(request, strategy));
        schema.put("requiredVariables", genericJdbcStarRocksRequiredVariables());
        schema.put("runtimeVariables", List.of(
                "batch_id",
                "run_id",
                "task_code",
                "batch_start_time",
                "batch_end_time",
                "batch_start_value",
                "batch_end_value"
        ));
        schema.put("idRangeRequiredRunParams", strategy == SyncIncrementalStrategy.ID_RANGE
                ? List.of("batchEndValue")
                : List.of());
        schema.put("sensitiveVariables", List.of(
                "source_username",
                "source_password",
                "starrocks_username",
                "starrocks_password"
        ));
        return JSONUtils.toJsonString(schema);
    }

    private List<String> fabMesSpcRequiredVariables() {
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

    private List<String> loaderPublishRequiredVariables() {
        return List.of(
                "batch_id",
                "run_id",
                "task_code",
                "xchg_batch_id",
                "source_system",
                "starrocks_jdbc_url",
                "starrocks_jdbc_driver",
                "starrocks_username",
                "starrocks_password",
                "starrocks_node_urls",
                "starrocks_base_url",
                "starrocks_database",
                "xchg_header_table",
                "xchg_site_table",
                "xchg_error_table",
                "stg_header_table",
                "stg_site_table",
                "stg_error_table"
        );
    }

    private List<String> genericJdbcStarRocksRequiredVariables() {
        return List.of(
                "batch_id",
                "run_id",
                "task_code",
                "source_jdbc_url",
                "source_jdbc_driver",
                "source_username",
                "source_password",
                "source_query",
                "batch_start_time",
                "batch_end_time",
                "batch_start_value",
                "batch_end_value",
                "starrocks_node_urls",
                "starrocks_base_url",
                "starrocks_username",
                "starrocks_password",
                "starrocks_database",
                "starrocks_table",
                "starrocks_error_table"
        );
    }

    private String loadFabMesSpcTemplate() {
        return loadTemplate(FAB_MES_SPC_TEMPLATE_RESOURCE);
    }

    private String loadLoaderPublishTemplate() {
        return loadTemplate(FAB_LOADER_PUBLISH_TEMPLATE_RESOURCE);
    }

    private String loadGenericJdbcStarRocksTemplate() {
        return loadTemplate(GENERIC_JDBC_STARROCKS_TEMPLATE_RESOURCE);
    }

    private String loadTemplate(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ServiceException("Load built-in template failed: " + resourcePath, e);
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

    private String defaultWatermarkField(
            CreateGenericJdbcStarRocksTaskRequest request,
            SyncIncrementalStrategy strategy
    ) {
        if (!isBlank(request.getWatermarkField())) {
            return request.getWatermarkField();
        }
        return strategy == SyncIncrementalStrategy.ID_RANGE ? "id" : "update_time";
    }
}
