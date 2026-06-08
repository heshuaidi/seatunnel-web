package org.apache.seatunnel.plugin.datasource.oracle.builder;

import com.google.auto.service.AutoService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.plugin.datasource.api.constants.DataSourceConstants;
import org.apache.seatunnel.plugin.datasource.api.hocon.AbstractJdbcBatchBuilder;
import org.apache.seatunnel.plugin.datasource.api.hocon.DataSourceHoconBuilder;
import org.apache.seatunnel.plugin.datasource.api.hocon.HoconBuildContext;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcConfigReaders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.DATABASE;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.QUERY;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.SCHEMA;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.SCHEMA_NAME;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.TABLE_LIST;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.TABLE_PATH;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.USER;

@AutoService(DataSourceHoconBuilder.class)
public class OracleBatchBuilder extends AbstractJdbcBatchBuilder {
    @Override
    public String pluginName() {
        return "JDBC-ORACLE";
    }

    @Override
    public Config buildSourceHocon(HoconBuildContext context) {
        Config source = super.buildSourceHocon(context);
        if (source.hasPath(QUERY)) {
            return source;
        }

        Map<String, Object> map = new LinkedHashMap<>(source.root().unwrapped());
        String database = resolveDatabase(context.getNodeConfig(), context.getConnectionConfig());
        String schema = resolveSchema(context.getNodeConfig(), context.getConnectionConfig());

        if (StringUtils.isNotBlank(database)) {
            map.put(DATABASE, database);
        }

        if (map.containsKey(TABLE_PATH)) {
            map.put(TABLE_PATH, normalizeOracleTablePath(String.valueOf(map.get(TABLE_PATH)), database, schema));
        }

        if (map.containsKey(TABLE_LIST)) {
            map.put(TABLE_LIST, normalizeOracleTableList(map.get(TABLE_LIST), database, schema));
        }

        return ConfigFactory.parseMap(map);
    }

    @Override
    public String renderTimeLiteral(String value, String timeFormat) {
        if (value == null) {
            return "NULL";
        }

        String oracleFormat = toOracleDateFormat(timeFormat);
        String escapedValue = value.replace("'", "''");

        if (StringUtils.containsIgnoreCase(timeFormat, "SSS")) {
            return "TO_TIMESTAMP('" + escapedValue + "', '" + oracleFormat + "')";
        }

        return "TO_DATE('" + escapedValue + "', '" + oracleFormat + "')";
    }

    private String toOracleDateFormat(String javaFormat) {
        if (StringUtils.isBlank(javaFormat)) {
            return "YYYY-MM-DD HH24:MI:SS";
        }

        return javaFormat
                .replace("yyyy", "YYYY")
                .replace("dd", "DD")
                .replace("HH", "HH24")
                .replace("mm", "MI")
                .replace("ss", "SS")
                .replace("SSS", "FF3");
    }

    @Override
    protected String defaultDriver() {
        return DataSourceConstants.COM_ORACLE_JDBC_DRIVER;
    }

    private List<Map<String, Object>> normalizeOracleTableList(
            Object rawTableList,
            String database,
            String schema) {
        if (!(rawTableList instanceof List<?>)) {
            return List.of();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object rawItem : (List<?>) rawTableList) {
            if (!(rawItem instanceof Map<?, ?>)) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            ((Map<?, ?>) rawItem).forEach((key, value) -> item.put(String.valueOf(key), value));
            Object tablePath = item.get(TABLE_PATH);
            if (tablePath != null) {
                item.put(TABLE_PATH, normalizeOracleTablePath(String.valueOf(tablePath), database, schema));
            }
            normalized.add(item);
        }
        return normalized;
    }

    private String normalizeOracleTablePath(String tablePath, String database, String schema) {
        if (StringUtils.isBlank(tablePath)) {
            return tablePath;
        }

        String[] parts = StringUtils.split(tablePath.trim(), '.');
        if (parts == null || parts.length == 0) {
            return tablePath.trim();
        }

        String normalizedDatabase = normalizeOracleIdentifier(database);
        String normalizedSchema = normalizeOracleIdentifier(schema);

        if (parts.length >= 3) {
            return joinTablePath(
                    normalizeOracleIdentifier(parts[0]),
                    normalizeOracleIdentifier(parts[1]),
                    normalizeOracleIdentifier(parts[2]));
        }

        if (parts.length == 2) {
            String firstPart = normalizeOracleIdentifier(parts[0]);
            String secondPart = normalizeOracleIdentifier(parts[1]);
            if (StringUtils.equals(firstPart, normalizedDatabase) && StringUtils.isNotBlank(normalizedSchema)) {
                return joinTablePath(normalizedDatabase, normalizedSchema, secondPart);
            }
            return joinTablePath(normalizedDatabase, firstPart, secondPart);
        }

        return joinTablePath(normalizedDatabase, normalizedSchema, normalizeOracleIdentifier(parts[0]));
    }

    private String joinTablePath(String database, String schema, String table) {
        if (StringUtils.isNotBlank(database) && StringUtils.isNotBlank(schema)) {
            return database + "." + schema + "." + table;
        }
        if (StringUtils.isNotBlank(schema)) {
            return schema + "." + table;
        }
        if (StringUtils.isNotBlank(database)) {
            return database + "." + table;
        }
        return table;
    }

    private String resolveDatabase(Config config, Config conn) {
        return normalizeOracleIdentifier(firstNonBlank(
                JdbcConfigReaders.getString(config, DATABASE, ""),
                JdbcConfigReaders.getString(conn, DATABASE, "")));
    }

    private String resolveSchema(Config config, Config conn) {
        return normalizeOracleIdentifier(firstNonBlank(
                JdbcConfigReaders.getString(config, SCHEMA, ""),
                JdbcConfigReaders.getString(config, SCHEMA_NAME, ""),
                JdbcConfigReaders.getString(conn, SCHEMA, ""),
                JdbcConfigReaders.getString(conn, SCHEMA_NAME, ""),
                JdbcConfigReaders.getString(conn, USER, "")));
    }

    private String normalizeOracleIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return "";
        }

        String trimmed = identifier.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
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
