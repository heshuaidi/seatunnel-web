package org.apache.seatunnel.web.core.verify.job;

import org.apache.seatunnel.plugin.datasource.api.hocon.DataSourceHoconPluginNameResolver;
import org.apache.seatunnel.web.spi.enums.DbType;
import org.springframework.stereotype.Component;

@Component
public class ConnectivitySourceBuilderResolver {

    public String resolveBuilderKey(DbType dbType) {
        return DataSourceHoconPluginNameResolver.resolveBuilderName(dbType);
    }
}
