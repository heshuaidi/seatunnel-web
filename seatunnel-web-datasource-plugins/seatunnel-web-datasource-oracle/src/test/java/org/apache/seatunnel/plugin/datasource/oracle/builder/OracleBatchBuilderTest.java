package org.apache.seatunnel.plugin.datasource.oracle.builder;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.seatunnel.plugin.datasource.api.constants.DataSourceConstants;
import org.apache.seatunnel.plugin.datasource.api.hocon.HoconBuildContext;
import org.apache.seatunnel.plugin.datasource.oracle.param.OracleParamConverter;
import org.apache.seatunnel.web.common.enums.HoconBuildStage;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OracleBatchBuilderTest {

    private final OracleBatchBuilder builder = new OracleBatchBuilder();

    @Test
    void buildsSourceHoconWithDefaultOracleDriver() {
        BaseConnectionParam param = new OracleParamConverter().createConnectionParams(
                "{"
                        + "\"host\":\"oracle-dev\","
                        + "\"port\":\"1521\","
                        + "\"database\":\"FREEPDB1\","
                        + "\"schemaName\":\"ST\","
                        + "\"user\":\"st\","
                        + "\"password\":\"St_dev_123456!\""
                        + "}");

        Config source = builder.buildSourceHocon(HoconBuildContext.builder()
                .connectionConfig(ConfigFactory.parseString(JSONUtils.toJsonString(param)))
                .nodeConfig(ConfigFactory.parseString(
                        "sql = \"" + DataSourceConstants.ORACLE_VALIDATION_QUERY + "\"\n"
                                + "readMode = sql"))
                .stage(HoconBuildStage.INSTANCE)
                .build());

        assertEquals("JDBC-ORACLE", builder.pluginName());
        assertEquals("jdbc:oracle:thin:@//oracle-dev:1521/FREEPDB1", source.getString("url"));
        assertEquals(DataSourceConstants.COM_ORACLE_JDBC_DRIVER, param.getDriver());
        assertEquals(DataSourceConstants.COM_ORACLE_JDBC_DRIVER, source.getString("driver"));
        assertEquals("st", source.getString("username"));
        assertTrue(source.hasPath("password"));
        assertEquals("St_dev_123456!", source.getString("password"));
        assertEquals(DataSourceConstants.ORACLE_VALIDATION_QUERY, source.getString("query"));
    }

    @Test
    void buildsSourceTablePathWithDatabaseSchemaAndTable() {
        BaseConnectionParam param = new OracleParamConverter().createConnectionParams(
                "{"
                        + "\"host\":\"oracle-dev\","
                        + "\"port\":\"1521\","
                        + "\"database\":\"FREEPDB1\","
                        + "\"schemaName\":\"ST\","
                        + "\"user\":\"st\","
                        + "\"password\":\"St_dev_123456!\""
                        + "}");

        Config source = builder.buildSourceHocon(HoconBuildContext.builder()
                .connectionConfig(ConfigFactory.parseString(JSONUtils.toJsonString(param)))
                .nodeConfig(ConfigFactory.parseString("table = issue108_user"))
                .stage(HoconBuildStage.INSTANCE)
                .build());

        assertEquals("jdbc:oracle:thin:@//oracle-dev:1521/FREEPDB1", source.getString("url"));
        assertEquals(DataSourceConstants.COM_ORACLE_JDBC_DRIVER, source.getString("driver"));
        assertEquals("st", source.getString("username"));
        assertEquals("St_dev_123456!", source.getString("password"));
        assertEquals("FREEPDB1", source.getString("database"));
        assertEquals("FREEPDB1.ST.ISSUE108_USER", source.getString("table_path"));
    }

    @Test
    void derivesSourceSchemaFromUsernameWhenSchemaIsMissing() {
        BaseConnectionParam param = new OracleParamConverter().createConnectionParams(
                "{"
                        + "\"host\":\"oracle-dev\","
                        + "\"port\":\"1521\","
                        + "\"database\":\"freepdb1\","
                        + "\"user\":\"st\","
                        + "\"password\":\"St_dev_123456!\""
                        + "}");

        Config source = builder.buildSourceHocon(HoconBuildContext.builder()
                .connectionConfig(ConfigFactory.parseString(JSONUtils.toJsonString(param)))
                .nodeConfig(ConfigFactory.parseString("table = issue108_user"))
                .stage(HoconBuildStage.INSTANCE)
                .build());

        assertEquals("FREEPDB1.ST.ISSUE108_USER", source.getString("table_path"));
    }

    @Test
    void keepsSchemaQualifiedSourceTableFromBeingDuplicated() {
        BaseConnectionParam param = new OracleParamConverter().createConnectionParams(
                "{"
                        + "\"host\":\"oracle-dev\","
                        + "\"port\":\"1521\","
                        + "\"database\":\"FREEPDB1\","
                        + "\"schemaName\":\"ST\","
                        + "\"user\":\"st\","
                        + "\"password\":\"St_dev_123456!\""
                        + "}");

        Config source = builder.buildSourceHocon(HoconBuildContext.builder()
                .connectionConfig(ConfigFactory.parseString(JSONUtils.toJsonString(param)))
                .nodeConfig(ConfigFactory.parseString("table_path = \"ST.issue108_user\""))
                .stage(HoconBuildStage.INSTANCE)
                .build());

        assertEquals("FREEPDB1.ST.ISSUE108_USER", source.getString("table_path"));
    }

    @Test
    void buildsSinkTableWithOracleSchemaAndTable() {
        BaseConnectionParam param = new OracleParamConverter().createConnectionParams(
                "{"
                        + "\"host\":\"oracle-dev\","
                        + "\"port\":\"1521\","
                        + "\"database\":\"FREEPDB1\","
                        + "\"schemaName\":\"ST\","
                        + "\"user\":\"st\","
                        + "\"password\":\"St_dev_123456!\""
                        + "}");

        Config sink = builder.buildSinkHocon(HoconBuildContext.builder()
                .connectionConfig(ConfigFactory.parseString(JSONUtils.toJsonString(param)))
                .nodeConfig(ConfigFactory.parseString("table = issue108_user"))
                .stage(HoconBuildStage.INSTANCE)
                .build());

        assertEquals("FREEPDB1", sink.getString("database"));
        assertEquals("ST.ISSUE108_USER", sink.getString("table"));
        assertEquals(DataSourceConstants.COM_ORACLE_JDBC_DRIVER, sink.getString("driver"));
        assertEquals("st", sink.getString("username"));
    }

    @Test
    void derivesSinkSchemaFromUsernameWhenSchemaIsMissing() {
        BaseConnectionParam param = new OracleParamConverter().createConnectionParams(
                "{"
                        + "\"host\":\"oracle-dev\","
                        + "\"port\":\"1521\","
                        + "\"database\":\"FREEPDB1\","
                        + "\"user\":\"st\","
                        + "\"password\":\"St_dev_123456!\""
                        + "}");

        Config sink = builder.buildSinkHocon(HoconBuildContext.builder()
                .connectionConfig(ConfigFactory.parseString(JSONUtils.toJsonString(param)))
                .nodeConfig(ConfigFactory.parseString("table = issue108_user"))
                .stage(HoconBuildStage.INSTANCE)
                .build());

        assertEquals("ST.ISSUE108_USER", sink.getString("table"));
    }
}
