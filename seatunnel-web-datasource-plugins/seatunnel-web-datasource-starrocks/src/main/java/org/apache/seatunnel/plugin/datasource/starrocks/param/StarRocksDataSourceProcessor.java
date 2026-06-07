package org.apache.seatunnel.plugin.datasource.starrocks.param;

import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.plugin.datasource.api.hocon.DataSourceHoconBuilder;
import org.apache.seatunnel.plugin.datasource.api.hocon.DataSourceHoconBuilderFactory;
import org.apache.seatunnel.plugin.datasource.api.jdbc.AbstractDataSourceProcessor;
import org.apache.seatunnel.plugin.datasource.api.jdbc.DataSourceProcessor;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcCatalog;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcConnectionProvider;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcParamConverter;
import org.apache.seatunnel.plugin.datasource.starrocks.connection.StarRocksConnectionProvider;
import org.apache.seatunnel.plugin.datasource.starrocks.metadata.StarRocksCatalog;
import org.apache.seatunnel.web.common.config.OptionRule;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.apache.seatunnel.web.spi.enums.DbType;

import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.BASE_URL;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.DATABASE;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.NODE_URLS;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.PASSWORD;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.STARROCKS_CONFIG;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.TABLE;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.USERNAME;

@Slf4j
public class StarRocksDataSourceProcessor extends AbstractDataSourceProcessor {

    private final JdbcConnectionProvider connectionManager = new StarRocksConnectionProvider();
    private final JdbcParamConverter paramConverter = new StarRocksParamConverter();

    @Override
    public DataSourceHoconBuilder getQueryBuilder(String pluginName) {
        return DataSourceHoconBuilderFactory.getBuilder(pluginName);
    }

    @Override
    public JdbcConnectionProvider getConnectionManager() {
        return connectionManager;
    }

    @Override
    public JdbcParamConverter getParamConverter() {
        return paramConverter;
    }

    @Override
    public JdbcCatalog getMetadataService(BaseConnectionParam connectionParam) {
        return new StarRocksCatalog(connectionParam, connectionManager);
    }

    @Override
    public OptionRule sinkOptionRule() {
        return OptionRule.builder()
                .required(NODE_URLS, BASE_URL, USERNAME, DATABASE, TABLE)
                .optional(PASSWORD, STARROCKS_CONFIG)
                .build();
    }

    @Override
    public DbType getDbType() {
        return DbType.STARROCKS;
    }

    @Override
    public boolean acceptsURL(String url) {
        return false;
    }

    @Override
    public DataSourceProcessor create() {
        return new StarRocksDataSourceProcessor();
    }
}
