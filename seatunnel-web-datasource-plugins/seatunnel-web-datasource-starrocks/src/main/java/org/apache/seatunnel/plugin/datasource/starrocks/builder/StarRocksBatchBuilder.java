package org.apache.seatunnel.plugin.datasource.starrocks.builder;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.plugin.datasource.api.constants.DataSourceConstants;
import org.apache.seatunnel.plugin.datasource.api.hocon.AbstractJdbcBatchBuilder;
import org.apache.seatunnel.plugin.datasource.api.hocon.HoconBuildContext;
import org.apache.seatunnel.plugin.datasource.api.hocon.JdbcExtraOptionAppender;
import org.apache.seatunnel.plugin.datasource.api.hocon.table.JdbcTableMode;
import org.apache.seatunnel.plugin.datasource.api.hocon.table.JdbcTableNameResolver;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcConfigReaders;
import org.apache.seatunnel.plugin.datasource.starrocks.param.StarRocksConnectionParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.DATABASE;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.PASSWORD;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.QUERY;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.READ_MODE;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.SCHEMA;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.SQL;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.TABLE;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.TABLE_LIST;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.TABLE_PATH;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.TARGET_TABLE_NAME;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.USER;

public class StarRocksBatchBuilder extends AbstractJdbcBatchBuilder {

    private static final String NODE_URLS = "nodeUrls";
    private static final String BASE_URL = "base-url";
    private static final String USERNAME = "username";
    private static final String QUERY_PORT = "queryPort";
    private static final String HTTP_PORT = "httpPort";
    private static final String STARROCKS_CONFIG = "starrocks.config";
    private static final String FORMAT = "format";
    private static final String STRIP_OUTER_ARRAY = "strip_outer_array";
    private static final String READ_MODE_UNDERSCORE = "read_mode";
    private static final String READ_MODE_SQL = "sql";
    private static final String FIELDS = "fields";
    private static final String OUTPUT_SCHEMA = "outputSchema";
    private static final String COLUMNS = "columns";
    private static final String ORIGIN_FIELD_NAME = "originFieldName";
    private static final String FIELD_NAME = "fieldName";
    private static final String COLUMN_NAME = "columnName";
    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String FIELD_TYPE = "fieldType";
    private static final String SOURCE_TYPE = "sourceType";
    private static final String COLUMN_TYPE = "columnType";

    private final JdbcTableNameResolver tableNameResolver = new JdbcTableNameResolver();
    private final JdbcExtraOptionAppender extraOptionAppender = new JdbcExtraOptionAppender();

    @Override
    protected String defaultDriver() {
        return DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER;
    }

    @Override
    public Config buildSourceHocon(HoconBuildContext context) {
        Config conn = context.getConnectionConfig();
        Config config = context.getNodeConfig();

        validateSourceTableMode(config);

        Map<String, Object> map = new LinkedHashMap<>(32);

        putSourceCommon(conn, config, map);

        List<String> sourceTables = tableNameResolver.resolveSourceTableNames(config);
        JdbcTableMode tableMode = tableNameResolver.resolveTableMode(config, sourceTables);

        if (JdbcTableMode.MULTI == tableMode) {
            List<Map<String, Object>> tableList = buildSourceTableList(config, sourceTables);
            if (CollectionUtils.isEmpty(tableList)) {
                throw new IllegalArgumentException("Missing StarRocks source table_list");
            }
            map.put(TABLE_LIST, tableList);
        } else {
            String table = resolveSingleSourceTable(config, sourceTables);
            if (StringUtils.isBlank(table)) {
                throw new IllegalArgumentException("Missing StarRocks source table");
            }
            map.put(TABLE, table);
            map.put(SCHEMA, buildSchema(config, table));
        }

        extraOptionAppender.append(config, map);

        return ConfigFactory.parseMap(map);
    }

    @Override
    public Config buildSinkHocon(HoconBuildContext context) {
        Config conn = context.getConnectionConfig();
        Config config = context.getNodeConfig();

        Map<String, Object> map = new HashMap<>(16);

        String host = JdbcConfigReaders.getStringRequired(conn, "host");
        String queryPort = firstNonBlank(
                JdbcConfigReaders.getString(conn, QUERY_PORT, ""),
                JdbcConfigReaders.getString(conn, "port", ""),
                StarRocksConnectionParam.DEFAULT_QUERY_PORT);
        String httpPort = firstNonBlank(
                JdbcConfigReaders.getString(conn, HTTP_PORT, ""),
                StarRocksConnectionParam.DEFAULT_HTTP_PORT);
        String database = firstNonBlank(
                JdbcConfigReaders.getString(config, DATABASE, ""),
                JdbcConfigReaders.getString(conn, DATABASE, ""));
        String table = resolveSinkTable(config);

        map.put(NODE_URLS, Collections.singletonList(host + ":" + httpPort));
        map.put(BASE_URL, buildBaseUrl(host, queryPort, database));
        map.put(USERNAME, firstNonBlank(
                JdbcConfigReaders.getString(conn, USER, ""),
                JdbcConfigReaders.getString(conn, USERNAME, "")));

        String password = JdbcConfigReaders.getString(conn, PASSWORD, "");
        if (StringUtils.isNotBlank(password)) {
            map.put(PASSWORD, processPassword(password));
        }

        map.put(DATABASE, database);
        map.put(TABLE, table);
        map.put(STARROCKS_CONFIG, defaultStarRocksConfig());

        extraOptionAppender.append(config, map);

        return ConfigFactory.parseMap(map);
    }

    @Override
    public String pluginName() {
        return "StarRocks";
    }

    private void validateSourceTableMode(Config config) {
        String readMode = firstNonBlank(
                JdbcConfigReaders.getString(config, READ_MODE, ""),
                JdbcConfigReaders.getString(config, READ_MODE_UNDERSCORE, ""));

        String sql = firstNonBlank(
                JdbcConfigReaders.getString(config, SQL, ""),
                JdbcConfigReaders.getString(config, QUERY, ""));

        if (READ_MODE_SQL.equalsIgnoreCase(readMode) || StringUtils.isNotBlank(sql)) {
            throw new IllegalArgumentException(
                    "StarRocks source supports table mode only; use SCRIPT mode for custom StarRocks HOCON");
        }
    }

    private void putSourceCommon(Config conn, Config config, Map<String, Object> map) {
        String database = firstNonBlank(
                JdbcConfigReaders.getString(config, DATABASE, ""),
                JdbcConfigReaders.getString(conn, DATABASE, ""));

        String username = firstNonBlank(
                JdbcConfigReaders.getString(conn, USER, ""),
                JdbcConfigReaders.getString(conn, USERNAME, ""),
                JdbcConfigReaders.getString(config, USERNAME, ""),
                JdbcConfigReaders.getString(config, USER, ""));

        if (StringUtils.isBlank(database)) {
            throw new IllegalArgumentException("Missing StarRocks source database");
        }
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("Missing StarRocks source username");
        }

        map.put(NODE_URLS, resolveNodeUrls(conn, config));
        map.put(USERNAME, username);

        String password = firstNonBlank(
                JdbcConfigReaders.getString(conn, PASSWORD, ""),
                JdbcConfigReaders.getString(config, PASSWORD, ""));
        if (StringUtils.isNotBlank(password)) {
            map.put(PASSWORD, processPassword(password));
        }

        map.put(DATABASE, database);
    }

    private List<String> resolveNodeUrls(Config conn, Config config) {
        List<String> explicitNodeUrls = firstNonEmpty(
                getStringList(config, NODE_URLS),
                getStringList(conn, NODE_URLS));
        if (CollectionUtils.isNotEmpty(explicitNodeUrls)) {
            return explicitNodeUrls;
        }

        String host = firstNonBlank(
                JdbcConfigReaders.getString(config, "host", ""),
                JdbcConfigReaders.getString(conn, "host", ""));
        String httpPort = firstNonBlank(
                JdbcConfigReaders.getString(config, HTTP_PORT, ""),
                JdbcConfigReaders.getString(conn, HTTP_PORT, ""),
                StarRocksConnectionParam.DEFAULT_HTTP_PORT);

        if (StringUtils.isBlank(host)) {
            throw new IllegalArgumentException("Missing StarRocks source host");
        }

        return Collections.singletonList(host + ":" + httpPort);
    }

    private List<Map<String, Object>> buildSourceTableList(Config config, List<String> sourceTables) {
        List<Map<String, Object>> configuredTableList = readConfiguredTableList(config);
        if (CollectionUtils.isNotEmpty(configuredTableList)) {
            return configuredTableList.stream()
                    .map(item -> normalizeConfiguredTableItem(config, item))
                    .filter(item -> StringUtils.isNotBlank(String.valueOf(item.get(TABLE))))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> tableList = new ArrayList<>();
        for (String table : sourceTables) {
            String normalizedTable = normalizeStarRocksTable(table);
            if (StringUtils.isBlank(normalizedTable)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put(TABLE, normalizedTable);
            item.put(SCHEMA, buildSchema(config, normalizedTable));
            tableList.add(item);
        }
        return tableList;
    }

    private Map<String, Object> normalizeConfiguredTableItem(Config config, Map<String, Object> rawItem) {
        Map<String, Object> item = new LinkedHashMap<>(rawItem);

        String table = firstNonBlank(
                asString(item.get(TABLE)),
                asString(item.get(TABLE_PATH)));
        table = normalizeStarRocksTable(table);

        item.remove(TABLE_PATH);
        item.put(TABLE, table);

        Object schema = item.get(SCHEMA);
        if (schema instanceof Map) {
            item.put(SCHEMA, normalizeSchemaMap((Map<?, ?>) schema));
        } else {
            item.put(SCHEMA, buildSchema(config, table));
        }

        return item;
    }

    private String resolveSingleSourceTable(Config config, List<String> sourceTables) {
        String table = firstNonBlank(
                JdbcConfigReaders.getString(config, TABLE, ""),
                JdbcConfigReaders.getString(config, TABLE_PATH, ""),
                tableNameResolver.firstTable(sourceTables));

        return normalizeStarRocksTable(table);
    }

    private Map<String, Object> buildSchema(Config config, String table) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(FIELDS, resolveFields(config, table));
        return schema;
    }

    private Map<String, Object> normalizeSchemaMap(Map<?, ?> rawSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        Object rawFields = rawSchema.get(FIELDS);

        Map<String, Object> fields = normalizeFieldsMap(rawFields);
        if (fields.isEmpty()) {
            fields = normalizeFieldsMap(rawSchema);
        }

        schema.put(FIELDS, fields);
        return schema;
    }

    private Map<String, Object> resolveFields(Config config, String table) {
        Map<String, Object> fields = resolveFieldsFromConfiguredTableList(config, table);
        if (!fields.isEmpty()) {
            return fields;
        }

        fields = resolveFieldsObject(config, SCHEMA + "." + FIELDS);
        if (!fields.isEmpty()) {
            return fields;
        }

        fields = resolveFieldsObject(config, FIELDS);
        if (!fields.isEmpty()) {
            return fields;
        }

        fields = resolveFieldsFromColumnList(config, OUTPUT_SCHEMA);
        if (!fields.isEmpty()) {
            return fields;
        }

        return resolveFieldsFromColumnList(config, COLUMNS);
    }

    private Map<String, Object> resolveFieldsFromConfiguredTableList(Config config, String table) {
        if (config == null || !config.hasPath(TABLE_LIST)) {
            return Collections.emptyMap();
        }

        try {
            for (Config item : config.getConfigList(TABLE_LIST)) {
                String itemTable = firstNonBlank(
                        JdbcConfigReaders.getString(item, TABLE, ""),
                        JdbcConfigReaders.getString(item, TABLE_PATH, ""));

                if (!sameTable(itemTable, table) || !item.hasPath(SCHEMA)) {
                    continue;
                }

                Object schemaValue = item.getValue(SCHEMA).unwrapped();
                if (schemaValue instanceof Map) {
                    Map<String, Object> fields = normalizeSchemaMap((Map<?, ?>) schemaValue)
                            .entrySet()
                            .stream()
                            .filter(entry -> FIELDS.equals(entry.getKey()))
                            .findFirst()
                            .map(entry -> normalizeFieldsMap(entry.getValue()))
                            .orElse(Collections.emptyMap());
                    if (!fields.isEmpty()) {
                        return fields;
                    }
                }
            }
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }

        return Collections.emptyMap();
    }

    private Map<String, Object> resolveFieldsObject(Config config, String path) {
        if (config == null || !config.hasPath(path)) {
            return Collections.emptyMap();
        }

        try {
            Config fieldsConfig = config.getConfig(path);
            Map<String, Object> fields = new LinkedHashMap<>();
            for (Map.Entry<String, ConfigValue> entry : fieldsConfig.entrySet()) {
                fields.put(entry.getKey(), normalizeSeaTunnelType(asString(entry.getValue().unwrapped())));
            }
            return fields;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> resolveFieldsFromColumnList(Config config, String path) {
        if (config == null || !config.hasPath(path)) {
            return Collections.emptyMap();
        }

        Map<String, Object> fields = new LinkedHashMap<>();

        try {
            for (Config item : config.getConfigList(path)) {
                String name = firstNonBlank(
                        JdbcConfigReaders.getString(item, ORIGIN_FIELD_NAME, ""),
                        JdbcConfigReaders.getString(item, FIELD_NAME, ""),
                        JdbcConfigReaders.getString(item, COLUMN_NAME, ""),
                        JdbcConfigReaders.getString(item, NAME, ""));

                String type = firstNonBlank(
                        JdbcConfigReaders.getString(item, TYPE, ""),
                        JdbcConfigReaders.getString(item, FIELD_TYPE, ""),
                        JdbcConfigReaders.getString(item, SOURCE_TYPE, ""),
                        JdbcConfigReaders.getString(item, COLUMN_TYPE, ""));

                if (StringUtils.isNotBlank(name)) {
                    fields.put(name.trim(), normalizeSeaTunnelType(type));
                }
            }
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }

        return fields;
    }

    private Map<String, Object> normalizeFieldsMap(Object rawFields) {
        if (!(rawFields instanceof Map)) {
            return Collections.emptyMap();
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        ((Map<?, ?>) rawFields).forEach((key, value) -> {
            if (key != null && StringUtils.isNotBlank(String.valueOf(key))) {
                fields.put(String.valueOf(key).trim(), normalizeSeaTunnelType(asString(value)));
            }
        });
        return fields;
    }

    private List<Map<String, Object>> readConfiguredTableList(Config config) {
        if (config == null || !config.hasPath(TABLE_LIST)) {
            return Collections.emptyList();
        }

        try {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Config item : config.getConfigList(TABLE_LIST)) {
                Map<String, Object> tableItem = new LinkedHashMap<>();
                for (Map.Entry<String, ConfigValue> entry : item.root().entrySet()) {
                    tableItem.put(entry.getKey(), entry.getValue().unwrapped());
                }
                result.add(tableItem);
            }
            return result;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private String resolveSinkTable(Config config) {
        String targetTableName = JdbcConfigReaders.getString(config, TARGET_TABLE_NAME, "");
        if (StringUtils.isNotBlank(targetTableName)) {
            return targetTableName;
        }

        String table = JdbcConfigReaders.getString(config, TABLE, "");
        if (StringUtils.isNotBlank(table)) {
            return table;
        }

        List<String> sinkTables = tableNameResolver.resolveSinkTableNames(config);
        return tableNameResolver.firstTable(sinkTables);
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

    private boolean sameTable(String left, String right) {
        String normalizedLeft = normalizeStarRocksTable(left);
        String normalizedRight = normalizeStarRocksTable(right);
        return StringUtils.equals(normalizedLeft, normalizedRight);
    }

    private String buildBaseUrl(String host, String queryPort, String database) {
        return String.format("%s%s:%s/%s",
                DataSourceConstants.JDBC_MYSQL,
                host,
                queryPort,
                database);
    }

    private Map<String, Object> defaultStarRocksConfig() {
        Map<String, Object> config = new LinkedHashMap<>(2);
        config.put(FORMAT, "JSON");
        config.put(STRIP_OUTER_ARRAY, true);
        return config;
    }

    private List<String> getStringList(Config config, String path) {
        if (config == null || !config.hasPath(path)) {
            return Collections.emptyList();
        }

        try {
            return config.getStringList(path).stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            String value = JdbcConfigReaders.getString(config, path, "");
            if (StringUtils.isBlank(value)) {
                return Collections.emptyList();
            }
            return List.of(value.split(",")).stream()
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
    }

    private List<String> firstNonEmpty(List<String>... values) {
        for (List<String> value : values) {
            if (CollectionUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return Collections.emptyList();
    }

    private String normalizeSeaTunnelType(String rawType) {
        if (StringUtils.isBlank(rawType)) {
            return "string";
        }

        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        int leftParen = normalized.indexOf('(');
        String baseType = leftParen > 0 ? normalized.substring(0, leftParen) : normalized;

        if (baseType.contains("char") || baseType.contains("text")
                || "string".equals(baseType) || "json".equals(baseType)
                || "bitmap".equals(baseType) || "hll".equals(baseType)) {
            return "string";
        }

        switch (baseType) {
            case "bool":
            case "boolean":
                return "boolean";
            case "tinyint":
                return "tinyint";
            case "smallint":
                return "smallint";
            case "int":
            case "integer":
                return "int";
            case "bigint":
                return "bigint";
            case "largeint":
                return "decimal(38,0)";
            case "float":
                return "float";
            case "double":
                return "double";
            case "decimal":
            case "decimalv2":
            case "decimal32":
            case "decimal64":
            case "decimal128":
            case "numeric":
                return normalized.startsWith(baseType + "(")
                        ? "decimal" + normalized.substring(baseType.length())
                        : "decimal";
            case "date":
                return "date";
            case "datetime":
            case "timestamp":
                return "timestamp";
            case "binary":
            case "varbinary":
                return "bytes";
            default:
                return baseType;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
