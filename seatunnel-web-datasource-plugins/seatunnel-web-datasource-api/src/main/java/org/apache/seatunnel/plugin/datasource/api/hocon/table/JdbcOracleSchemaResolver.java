package org.apache.seatunnel.plugin.datasource.api.hocon.table;

import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcConfigReaders;

import java.util.Locale;

import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.*;

final class JdbcOracleSchemaResolver {

    private JdbcOracleSchemaResolver() {
    }

    static String normalizeSingleTable(Config config, Config conn, String table) {
        String schema = resolveOracleSchema(config, conn);
        if (StringUtils.isBlank(schema) || StringUtils.isBlank(table)) {
            return table;
        }

        String trimmedTable = table.trim();
        if (trimmedTable.contains(".")) {
            return normalizeOracleTable(trimmedTable);
        }

        return schema + "." + normalizeOracleIdentifier(trimmedTable);
    }

    static String defaultMultiTablePattern(Config config, Config conn, String fallback) {
        String schema = resolveOracleSchema(config, conn);
        if (StringUtils.isBlank(schema)) {
            return fallback;
        }

        return schema + "." + TABLE_NAME_PLACEHOLDER;
    }

    private static String resolveOracleSchema(Config config, Config conn) {
        if (!isOracle(config, conn)) {
            return "";
        }

        return normalizeOracleIdentifier(firstNonBlank(
                JdbcConfigReaders.getString(config, SCHEMA, ""),
                JdbcConfigReaders.getString(config, SCHEMA_NAME, ""),
                JdbcConfigReaders.getString(conn, SCHEMA, ""),
                JdbcConfigReaders.getString(conn, SCHEMA_NAME, ""),
                JdbcConfigReaders.getString(conn, USER, "")
        ));
    }

    private static String normalizeOracleTable(String table) {
        String[] parts = StringUtils.split(table, '.');
        if (parts == null || parts.length == 0) {
            return table.trim();
        }

        if (parts.length >= 2) {
            return normalizeOracleIdentifier(parts[parts.length - 2])
                    + "."
                    + normalizeOracleIdentifier(parts[parts.length - 1]);
        }

        return normalizeOracleIdentifier(parts[0]);
    }

    private static String normalizeOracleIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return "";
        }

        String trimmed = identifier.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static boolean isOracle(Config config, Config conn) {
        return containsIgnoreCase(config, PLUGIN_NAME, "oracle")
                || containsIgnoreCase(conn, PLUGIN_NAME, "oracle")
                || containsIgnoreCase(config, DB_TYPE, "oracle")
                || containsIgnoreCase(conn, DB_TYPE, "oracle")
                || containsIgnoreCase(config, DRIVER, "oracle")
                || containsIgnoreCase(conn, DRIVER, "oracle")
                || startsWithIgnoreCase(config, URL, "jdbc:oracle:")
                || startsWithIgnoreCase(conn, URL, "jdbc:oracle:");
    }

    private static boolean containsIgnoreCase(Config config, String path, String searchText) {
        return StringUtils.containsIgnoreCase(JdbcConfigReaders.getString(config, path, ""), searchText);
    }

    private static boolean startsWithIgnoreCase(Config config, String path, String prefix) {
        return StringUtils.startsWithIgnoreCase(JdbcConfigReaders.getString(config, path, ""), prefix);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
