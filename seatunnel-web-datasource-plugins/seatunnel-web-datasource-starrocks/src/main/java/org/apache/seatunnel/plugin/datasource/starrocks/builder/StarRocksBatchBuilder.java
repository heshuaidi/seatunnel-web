package org.apache.seatunnel.plugin.datasource.starrocks.builder;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.plugin.datasource.api.constants.DataSourceConstants;
import org.apache.seatunnel.plugin.datasource.api.hocon.AbstractJdbcBatchBuilder;
import org.apache.seatunnel.plugin.datasource.api.hocon.HoconBuildContext;
import org.apache.seatunnel.plugin.datasource.api.hocon.JdbcExtraOptionAppender;
import org.apache.seatunnel.plugin.datasource.api.hocon.table.JdbcTableNameResolver;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcConfigReaders;
import org.apache.seatunnel.plugin.datasource.starrocks.param.StarRocksConnectionParam;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.DATABASE;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.PASSWORD;
import static org.apache.seatunnel.plugin.datasource.api.hocon.JdbcBatchConstants.TABLE;
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

    private final JdbcTableNameResolver tableNameResolver = new JdbcTableNameResolver();
    private final JdbcExtraOptionAppender extraOptionAppender = new JdbcExtraOptionAppender();

    @Override
    protected String defaultDriver() {
        return DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER;
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
