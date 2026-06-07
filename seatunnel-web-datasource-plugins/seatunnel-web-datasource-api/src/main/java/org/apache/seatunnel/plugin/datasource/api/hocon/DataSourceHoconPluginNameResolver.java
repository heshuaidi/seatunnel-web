package org.apache.seatunnel.plugin.datasource.api.hocon;

import org.apache.seatunnel.web.spi.enums.DbType;

public final class DataSourceHoconPluginNameResolver {

    private DataSourceHoconPluginNameResolver() {
    }

    public static String resolveBuilderName(DbType dbType) {
        return switch (dbType) {
            case MYSQL -> "JDBC-MYSQL";
            case POSTGRE_SQL -> "JDBC-POSTGRESQL";
            case ORACLE -> "JDBC-ORACLE";
            case STARROCKS -> "StarRocks";
            default -> throw new IllegalArgumentException(
                    "Unsupported datasource hocon builder dbType: " + dbType);
        };
    }
}
