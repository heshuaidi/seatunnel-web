package org.apache.seatunnel.plugin.datasource.starrocks.connection;

import org.apache.seatunnel.plugin.datasource.api.constants.DataSourceConstants;
import org.apache.seatunnel.plugin.datasource.api.jdbc.AbstractJdbcConnectionProvider;
import org.apache.seatunnel.plugin.datasource.starrocks.param.StarRocksConnectionParam;

public class StarRocksConnectionProvider
        extends AbstractJdbcConnectionProvider<StarRocksConnectionParam> {

    @Override
    protected String defaultDriverClass() {
        return DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER;
    }

    @Override
    protected String resolveDriverLocation(StarRocksConnectionParam t) {
        return defaultBaseUrl() + t.getDriverLocation();
    }
}
