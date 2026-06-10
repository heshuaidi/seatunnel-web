package org.apache.seatunnel.web.core.builder.source;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.plugin.datasource.api.hocon.DataSourceHoconBuilder;
import org.apache.seatunnel.plugin.datasource.api.hocon.HoconBuildContext;
import org.apache.seatunnel.plugin.datasource.api.hocon.table.JdbcTableMode;
import org.apache.seatunnel.plugin.datasource.api.hocon.table.JdbcTableNameResolver;
import org.apache.seatunnel.plugin.datasource.api.utils.DataSourceUtils;
import org.apache.seatunnel.plugin.datasource.api.jdbc.DataSourceProcessor;
import org.apache.seatunnel.plugin.datasource.api.modal.DataSourceTableColumn;
import org.apache.seatunnel.web.common.config.ConfigValidator;
import org.apache.seatunnel.web.common.config.ReadonlyConfig;
import org.apache.seatunnel.web.common.enums.HoconBuildStage;
import org.apache.seatunnel.web.core.builder.context.DagBuildContext;
import org.apache.seatunnel.web.core.time.TimeVariableJdbcSqlRenderService;
import org.apache.seatunnel.web.dao.entity.DataSource;
import org.apache.seatunnel.web.dao.repository.DataSourceDao;
import org.apache.seatunnel.web.spi.bean.dto.config.JobScheduleConfig;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.apache.seatunnel.web.spi.enums.DbType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataSourceSourceBuilder implements SourceNodeConfigBuilder {

    private static final String NODE_TYPE = "source";

    private static final String KEY_DATA_SOURCE_ID = "dataSourceId";
    private static final String KEY_DB_TYPE = "dbType";
    private static final String KEY_PLUGIN_NAME = "pluginName";
    private static final String KEY_CONNECTOR_TYPE = "connectorType";

    private static final String KEY_SQL = "sql";
    private static final String KEY_WHERE_CONDITION = "where_condition";
    private static final String KEY_OUTPUT_SCHEMA = "outputSchema";
    private static final String KEY_SCHEMA = "schema";
    private static final String KEY_FIELDS = "fields";
    private static final String KEY_TABLE = "table";
    private static final String KEY_TABLE_LIST = "table_list";
    private static final String KEY_TABLE_PATH = "table_path";
    private static final String KEY_READ_MODE = "read_mode";
    private static final String READ_MODE_TABLE = "table";

    @Resource
    private DataSourceDao dataSourceDao;

    @Resource
    private TimeVariableJdbcSqlRenderService timeVariableJdbcSqlRenderService;

    private final JdbcTableNameResolver tableNameResolver = new JdbcTableNameResolver();

    @Override
    public String nodeType() {
        return NODE_TYPE;
    }

    @Override
    public Config build(Config data) {
        return build(data, DagBuildContext.empty());
    }

    @Override
    public Config build(Config data, DagBuildContext dagContext) {
        Config nodeConfig = resolveNodeConfig(data);
        nodeConfig = appendPluginOutputIfNecessary(data, nodeConfig, dagContext);
        nodeConfig = appendOutputSchemaMetaIfNecessary(data, nodeConfig);

        Long dataSourceId = parseDataSourceId(nodeConfig);
        DataSource dataSource = getRequiredDataSource(dataSourceId);

        DbType dbType = parseDbType(nodeConfig);
        String pluginName = getRequiredPluginName(nodeConfig);

        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(dbType);
        DataSourceHoconBuilder hoconBuilder = processor.getQueryBuilder(pluginName);

        if (!hoconBuilder.supportsSource()) {
            throw new IllegalArgumentException(pluginName + " does not support source side");
        }

        nodeConfig = enrichStarRocksSchemaIfNecessary(nodeConfig, dataSource, dbType, processor);

        nodeConfig = renderTimeVariablesIfNecessary(
                nodeConfig,
                hoconBuilder,
                dagContext.getScheduleConfig()
        );

        Config connectionConfig = ConfigFactory.parseString(dataSource.getConnectionParams());

        HoconBuildContext buildContext = HoconBuildContext.builder()
                .connectionParam(dataSource.getConnectionParams())
                .connectionConfig(connectionConfig)
                .nodeConfig(nodeConfig)
                .scheduleConfig(dagContext.getScheduleConfig())
                .stage(HoconBuildStage.INSTANCE)
                .dataSourceId(dataSource.getId())
                .dataSourceName(dataSource.getName())
                .dbType(dbType.name())
                .build();

        Config sourceConfig = hoconBuilder.buildSourceHocon(buildContext);

        validateSourceConfig(processor, pluginName, sourceConfig);

        return sourceConfig;
    }

    private Config appendOutputSchemaMetaIfNecessary(Config data, Config config) {
        if (data == null || config == null || !data.hasPath("meta." + KEY_OUTPUT_SCHEMA)) {
            return config;
        }

        Map<String, Object> extra = new HashMap<>();
        extra.put(KEY_OUTPUT_SCHEMA, data.getValue("meta." + KEY_OUTPUT_SCHEMA).unwrapped());

        return config.withFallback(ConfigFactory.parseMap(extra)).resolve();
    }

    private Config enrichStarRocksSchemaIfNecessary(Config config,
                                                    DataSource dataSource,
                                                    DbType dbType,
                                                    DataSourceProcessor processor) {
        if (dbType != DbType.STARROCKS || config == null || isSqlRead(config)) {
            return config;
        }

        List<String> sourceTables = tableNameResolver.resolveSourceTableNames(config);
        if (sourceTables.isEmpty()) {
            return config;
        }

        JdbcTableMode tableMode = tableNameResolver.resolveTableMode(config, sourceTables);
        if (JdbcTableMode.MULTI == tableMode) {
            if (hasTableListSchemas(config)) {
                return config;
            }

            Map<String, Object> extra = new HashMap<>();
            extra.put(KEY_TABLE_LIST, buildStarRocksTableListWithSchema(
                    dataSource,
                    processor,
                    sourceTables));
            return ConfigFactory.parseMap(extra).withFallback(config).resolve();
        }

        if (hasGlobalSchema(config)) {
            return config;
        }

        String table = normalizeStarRocksTable(tableNameResolver.firstTable(sourceTables));
        if (StringUtils.isBlank(table)) {
            return config;
        }

        Map<String, Object> extra = new HashMap<>();
        extra.put(KEY_SCHEMA, buildStarRocksSchema(
                listStarRocksColumns(dataSource, processor, table)));

        return ConfigFactory.parseMap(extra).withFallback(config).resolve();
    }

    private boolean isSqlRead(Config config) {
        String readMode = firstNonBlank(
                getTrimmedString(config, "readMode"),
                getTrimmedString(config, KEY_READ_MODE));
        String sql = firstNonBlank(
                getTrimmedString(config, KEY_SQL),
                getTrimmedString(config, "query"));
        return "sql".equalsIgnoreCase(readMode) || StringUtils.isNotBlank(sql);
    }

    private boolean hasGlobalSchema(Config config) {
        return config.hasPath(KEY_SCHEMA + "." + KEY_FIELDS)
                || config.hasPath(KEY_OUTPUT_SCHEMA)
                || config.hasPath(KEY_FIELDS);
    }

    private boolean hasTableListSchemas(Config config) {
        if (!config.hasPath(KEY_TABLE_LIST)) {
            return false;
        }

        try {
            List<? extends Config> tableList = config.getConfigList(KEY_TABLE_LIST);
            if (tableList.isEmpty()) {
                return false;
            }

            for (Config item : tableList) {
                if (!item.hasPath(KEY_SCHEMA + "." + KEY_FIELDS)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<Map<String, Object>> buildStarRocksTableListWithSchema(DataSource dataSource,
                                                                        DataSourceProcessor processor,
                                                                        List<String> sourceTables) {
        List<Map<String, Object>> tableList = new ArrayList<>();
        for (String sourceTable : sourceTables) {
            String table = normalizeStarRocksTable(sourceTable);
            if (StringUtils.isBlank(table)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put(KEY_TABLE, table);
            item.put(KEY_SCHEMA, buildStarRocksSchema(
                    listStarRocksColumns(dataSource, processor, table)));
            tableList.add(item);
        }
        return tableList;
    }

    private Map<String, Object> buildStarRocksSchema(List<DataSourceTableColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("StarRocks source schema columns can not be empty");
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        for (DataSourceTableColumn column : columns) {
            if (column == null || StringUtils.isBlank(column.getColumnName())) {
                continue;
            }

            fields.put(column.getColumnName(), firstNonBlank(
                    column.getSourceType(),
                    column.getColumnType(),
                    column.getColumnName()));
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(KEY_FIELDS, fields);
        return schema;
    }

    private List<DataSourceTableColumn> listStarRocksColumns(DataSource dataSource,
                                                             DataSourceProcessor processor,
                                                             String table) {
        try {
            BaseConnectionParam connectionParam = processor
                    .getParamConverter()
                    .createConnectionParams(dataSource.getConnectionParams());

            Map<String, Object> request = new HashMap<>();
            request.put(KEY_READ_MODE, READ_MODE_TABLE);
            request.put(KEY_TABLE_PATH, table);

            return processor.getMetadataService(connectionParam).listColumns(request);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to resolve StarRocks source schema for table: " + table,
                    e);
        }
    }

    private String normalizeStarRocksTable(String table) {
        if (StringUtils.isBlank(table)) {
            return "";
        }

        String[] parts = StringUtils.split(table.trim(), '.');
        if (parts == null || parts.length == 0) {
            return "";
        }

        return parts[parts.length - 1].trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private Config renderTimeVariablesIfNecessary(Config config,
                                                  DataSourceHoconBuilder hoconBuilder,
                                                  JobScheduleConfig scheduleConfig) {
        /*
         * 这里先只处理 JDBC SQL 类场景。
         * CDC 一般不需要渲染 sql / where_condition。
         */
        Map<String, Object> extra = new HashMap<>();

        renderSqlFragmentIfNecessary(config, hoconBuilder, scheduleConfig, KEY_SQL, extra);
        renderSqlFragmentIfNecessary(config, hoconBuilder, scheduleConfig, KEY_WHERE_CONDITION, extra);

        if (extra.isEmpty()) {
            return config;
        }

        return ConfigFactory.parseMap(extra)
                .withFallback(config)
                .resolve();
    }

    private void renderSqlFragmentIfNecessary(Config config,
                                              DataSourceHoconBuilder hoconBuilder,
                                              JobScheduleConfig scheduleConfig,
                                              String key,
                                              Map<String, Object> extra) {
        String value = getTrimmedString(config, key);
        if (StringUtils.isBlank(value)) {
            return;
        }

        String renderedValue = timeVariableJdbcSqlRenderService.renderSql(
                value,
                hoconBuilder,
                scheduleConfig
        );

        extra.put(key, renderedValue);
    }

    private Config appendPluginOutputIfNecessary(Config data,
                                                 Config config,
                                                 DagBuildContext context) {
        if (context == null || !context.hasTransform()) {
            return config;
        }

        String pluginOutput = getTrimmedString(config, "pluginOutput");
        if (StringUtils.isBlank(pluginOutput)) {
            pluginOutput = getTrimmedString(data, "pluginOutput");
        }

        if (StringUtils.isBlank(pluginOutput)) {
            return config;
        }

        Map<String, Object> extra = new HashMap<>();
        extra.put("plugin_output", pluginOutput);

        return ConfigFactory.parseMap(extra).withFallback(config).resolve();
    }

    @Override
    public String connectorName(Config data) {
        String connectorType = getTrimmedString(data, KEY_CONNECTOR_TYPE);
        if (StringUtils.isNotBlank(connectorType)) {
            return connectorType;
        }

        throw new IllegalArgumentException(
                "Missing connector name, field '" + KEY_CONNECTOR_TYPE + "' is not provided");
    }

    private Long parseDataSourceId(Config config) {
        String value = getTrimmedString(config, KEY_DATA_SOURCE_ID);
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(
                    "Missing required field '" + KEY_DATA_SOURCE_ID + "' in source node config");
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid '" + KEY_DATA_SOURCE_ID + "': " + value + ", expected numeric value", e);
        }
    }

    private DataSource getRequiredDataSource(Long dataSourceId) {
        DataSource dataSource = dataSourceDao.queryById(dataSourceId);
        if (dataSource == null) {
            throw new IllegalArgumentException(
                    "Source data source does not exist, dataSourceId=" + dataSourceId);
        }
        return dataSource;
    }

    private DbType parseDbType(Config config) {
        String dbTypeValue = getTrimmedString(config, KEY_DB_TYPE);
        if (StringUtils.isBlank(dbTypeValue)) {
            throw new IllegalArgumentException(
                    "Missing required field '" + KEY_DB_TYPE + "' in source node config");
        }

        try {
            return DbType.valueOf(dbTypeValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported dbType: " + dbTypeValue, e);
        }
    }

    private String getRequiredPluginName(Config config) {
        String pluginName = getTrimmedString(config, KEY_PLUGIN_NAME);
        if (StringUtils.isBlank(pluginName)) {
            throw new IllegalArgumentException(
                    "Missing required field '" + KEY_PLUGIN_NAME + "' in source node config");
        }
        return pluginName;
    }

    private void validateSourceConfig(DataSourceProcessor processor,
                                      String pluginName,
                                      Config sourceConfig) {
        ConfigValidator.of(ReadonlyConfig.fromConfig(sourceConfig))
                .validate(processor.sourceOptionRule(pluginName));
    }

    private String getTrimmedString(Config config, String path) {
        if (config == null || !config.hasPath(path)) {
            return null;
        }

        String value = config.getString(path);
        return value == null ? null : value.trim();
    }
}
