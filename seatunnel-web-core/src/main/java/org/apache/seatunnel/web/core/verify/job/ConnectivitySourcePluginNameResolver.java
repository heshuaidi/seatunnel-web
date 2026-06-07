package org.apache.seatunnel.web.core.verify.job;

import org.apache.seatunnel.web.spi.enums.DbType;
import org.springframework.stereotype.Component;

@Component
public class ConnectivitySourcePluginNameResolver {

    public String resolvePluginName(DbType dbType) {
        return switch (dbType) {
            /*
             * StarRocks query port is MySQL-protocol compatible, so the connectivity
             * test renders source options with StarRocks builder and runs SeaTunnel Jdbc source.
             */
            case MYSQL, POSTGRE_SQL, ORACLE, STARROCKS -> "Jdbc";
            default -> throw new IllegalArgumentException("暂不支持该数据源类型的 Source 插件名解析: " + dbType);
        };
    }
}
