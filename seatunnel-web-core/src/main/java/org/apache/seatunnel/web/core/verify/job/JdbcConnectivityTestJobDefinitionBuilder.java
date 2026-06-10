package org.apache.seatunnel.web.core.verify.job;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.annotation.Resource;
import org.apache.seatunnel.plugin.datasource.api.constants.DataSourceConstants;
import org.apache.seatunnel.plugin.datasource.api.hocon.DataSourceHoconBuilder;
import org.apache.seatunnel.plugin.datasource.api.hocon.HoconBuildContext;
import org.apache.seatunnel.plugin.datasource.api.jdbc.DataSourceProcessor;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcConfigReaders;
import org.apache.seatunnel.plugin.datasource.api.utils.DataSourceUtils;
import org.apache.seatunnel.web.common.enums.HoconBuildStage;
import org.apache.seatunnel.web.dao.entity.DataSource;
import org.apache.seatunnel.web.dao.entity.SeaTunnelClient;
import org.apache.seatunnel.web.spi.enums.DbType;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class JdbcConnectivityTestJobDefinitionBuilder implements ConnectivityTestJobDefinitionBuilder {

    private static final Set<DbType> SUPPORTED = new HashSet<>(Arrays.asList(
            DbType.MYSQL,
            DbType.POSTGRE_SQL,
            DbType.ORACLE,
            DbType.STARROCKS
    ));

    @Resource
    private ConnectivitySourceBuilderResolver sourceBuilderResolver;

    @Resource
    private ConnectivitySourcePluginNameResolver sourcePluginNameResolver;

    @Resource
    private ConsoleSinkHoconBuilder consoleSinkHoconBuilder;

    @Resource
    private TestJobEnvConfigBuilder testJobEnvConfigBuilder;

    @Resource
    private SeaTunnelJobConfigAssembler seaTunnelJobConfigAssembler;

    @Override
    public boolean supports(DbType dbType) {
        return SUPPORTED.contains(dbType);
    }

    @Override
    public ConnectivityTestJob build(SeaTunnelClient client, DataSource datasource) {
        DbType dbType = datasource.getDbType();

        String hoconPluginName = sourcePluginNameResolver.resolvePluginName(dbType);

        Config sourceNodeConfig = buildMinimalSourceNodeConfig(dbType);
        Config connectionConfig = ConfigFactory.parseString(datasource.getConnectionParams());
        HoconBuildContext buildContext = HoconBuildContext.builder()
                .connectionParam(datasource.getConnectionParams())
                .connectionConfig(connectionConfig)
                .nodeConfig(sourceNodeConfig)
                .stage(HoconBuildStage.INSTANCE)
                .build();
        Config sourcePluginConfig = buildSourcePluginConfig(dbType, buildContext);

        String jobName = buildJobName(client.getId(), datasource.getId());
        String jobConfig = seaTunnelJobConfigAssembler.assemble(
                testJobEnvConfigBuilder.buildBatchEnv(),
                hoconPluginName,
                sourcePluginConfig,
                consoleSinkHoconBuilder.pluginName(),
                consoleSinkHoconBuilder.build()
        );

        return new ConnectivityTestJob(jobName, jobConfig, "hocon", true);
    }

    private Config buildSourcePluginConfig(DbType dbType, HoconBuildContext buildContext) {
        if (DbType.STARROCKS.equals(dbType)) {
            return buildStarRocksJdbcSourceConfig(buildContext.getConnectionConfig());
        }

        String builderKey = sourceBuilderResolver.resolveBuilderKey(dbType);
        DataSourceProcessor processor = DataSourceUtils.getDatasourceProcessor(dbType);
        DataSourceHoconBuilder sourceBuilder = processor.getQueryBuilder(builderKey);
        return sourceBuilder.buildSourceHocon(buildContext);
    }

    private Config buildStarRocksJdbcSourceConfig(Config conn) {
        Map<String, Object> map = new LinkedHashMap<String, Object>(8);

        String url = firstNonBlank(
                JdbcConfigReaders.getString(conn, "url", ""),
                JdbcConfigReaders.getString(conn, "jdbcUrl", ""),
                buildStarRocksJdbcUrl(conn)
        );
        if (isBlank(url)) {
            throw new IllegalArgumentException("Missing StarRocks JDBC url");
        }

        String username = firstNonBlank(
                JdbcConfigReaders.getString(conn, "user", ""),
                JdbcConfigReaders.getString(conn, "username", "")
        );
        if (isBlank(username)) {
            throw new IllegalArgumentException("Missing StarRocks username");
        }

        map.put("url", url);
        map.put("username", username);
        map.put("driver", firstNonBlank(
                JdbcConfigReaders.getString(conn, "driver", ""),
                DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER
        ));
        map.put("query", validationQuery(DbType.STARROCKS));

        String password = JdbcConfigReaders.getString(conn, "password", "");
        if (!isBlank(password)) {
            map.put("password", password);
        }

        return ConfigFactory.parseMap(map);
    }

    private String buildStarRocksJdbcUrl(Config conn) {
        String host = JdbcConfigReaders.getString(conn, "host", "");
        String database = JdbcConfigReaders.getString(conn, "database", "");
        String queryPort = firstNonBlank(
                JdbcConfigReaders.getString(conn, "queryPort", ""),
                JdbcConfigReaders.getString(conn, "port", "")
        );

        if (isBlank(host) || isBlank(database) || isBlank(queryPort)) {
            return "";
        }

        return DataSourceConstants.JDBC_MYSQL + host.trim() + ":" + queryPort.trim() + "/" + database.trim();
    }

    private Config buildMinimalSourceNodeConfig(DbType dbType) {
        Map<String, Object> map = new LinkedHashMap<String, Object>(4);
        map.put("sql", validationQuery(dbType));
        map.put("readMode", "sql");
        return ConfigFactory.parseMap(map);
    }

    private String validationQuery(DbType dbType) {
        if (DbType.ORACLE.equals(dbType)) {
            return DataSourceConstants.ORACLE_VALIDATION_QUERY;
        }
        return "select 1 as connectivity_check";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String buildJobName(Long clientId, Long datasourceId) {
        return "connectivity_check_" + datasourceId + "_" + clientId + "_" + System.currentTimeMillis();
    }
}
