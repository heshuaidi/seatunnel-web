package org.apache.seatunnel.plugin.datasource.oracle.metadata;

import org.apache.seatunnel.plugin.datasource.api.jdbc.TablePath;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OracleCatalogTest {

    @Test
    void resolvesDefaultOwnerFromUsernameWhenSchemaIsMissing() {
        OracleCatalog catalog = new OracleCatalog(connectionParam("st", null, "FREEPDB1"), null);

        assertEquals("ST", catalog.resolveOwner());
    }

    @Test
    void resolvesOwnerFromSchemaBeforeUsername() {
        OracleCatalog catalog = new OracleCatalog(connectionParam("st", "app", "FREEPDB1"), null);

        assertEquals("APP", catalog.resolveOwner());
    }

    @Test
    void normalizesTablePathOwnerAndTableName() {
        OracleCatalog catalog = new OracleCatalog(connectionParam("st", null, "FREEPDB1"), null);

        OracleCatalog.OracleTablePath tablePath =
                catalog.resolveTablePath(TablePath.of("FREEPDB1", null, "issue108_user"));

        assertEquals("ST", tablePath.getOwner());
        assertEquals("ISSUE108_USER", tablePath.getTableName());
    }

    @Test
    void resolvesSchemaQualifiedTablePathAsOracleOwner() {
        OracleCatalog catalog = new OracleCatalog(connectionParam("st", null, "FREEPDB1"), null);

        OracleCatalog.OracleTablePath tablePath =
                catalog.resolveTablePath(TablePath.of("FREEPDB1", null, "ST.issue108_user"));

        assertEquals("ST", tablePath.getOwner());
        assertEquals("ISSUE108_USER", tablePath.getTableName());
    }

    private BaseConnectionParam connectionParam(String user, String schemaName, String database) {
        BaseConnectionParam param = new BaseConnectionParam() {
        };
        param.setUrl("jdbc:oracle:thin:@//oracle-dev:1521/FREEPDB1");
        param.setUser(user);
        param.setSchemaName(schemaName);
        param.setDatabase(database);
        return param;
    }
}
