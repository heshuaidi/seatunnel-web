package org.apache.seatunnel.web.core.verify.job;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.seatunnel.plugin.datasource.api.constants.DataSourceConstants;
import org.apache.seatunnel.web.dao.entity.DataSource;
import org.apache.seatunnel.web.dao.entity.SeaTunnelClient;
import org.apache.seatunnel.web.spi.enums.DbType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcConnectivityTestJobDefinitionBuilderTest {

    @Test
    void buildsStarRocksConnectivityJobWithJdbcQuerySource() throws Exception {
        JdbcConnectivityTestJobDefinitionBuilder builder = newBuilder();

        SeaTunnelClient client = new SeaTunnelClient();
        client.setId(200L);

        DataSource datasource = new DataSource();
        datasource.setId(100L);
        datasource.setDbType(DbType.STARROCKS);
        datasource.setConnectionParams("{"
                + "\"host\":\"starrocks-dev\","
                + "\"queryPort\":\"9030\","
                + "\"httpPort\":\"8040\","
                + "\"database\":\"st_test\","
                + "\"user\":\"st\","
                + "\"password\":\"secret\","
                + "\"driver\":\"" + DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER + "\""
                + "}");

        ConnectivityTestJob job = builder.build(client, datasource);

        Config jobConfig = ConfigFactory.parseString(job.getJobConfig());
        assertEquals("hocon", job.getConfigFormat());
        assertTrue(job.isCleanupRequired());

        assertTrue(jobConfig.hasPath("source.Jdbc"));
        assertFalse(jobConfig.hasPath("source.StarRocks"));
        assertEquals(
                "jdbc:mysql://starrocks-dev:9030/st_test",
                jobConfig.getString("source.Jdbc.url"));
        assertEquals("st", jobConfig.getString("source.Jdbc.username"));
        assertEquals("secret", jobConfig.getString("source.Jdbc.password"));
        assertEquals(
                DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER,
                jobConfig.getString("source.Jdbc.driver"));
        assertEquals("select 1 as connectivity_check", jobConfig.getString("source.Jdbc.query"));
        assertFalse(jobConfig.hasPath("source.Jdbc.readMode"));
    }

    private JdbcConnectivityTestJobDefinitionBuilder newBuilder() throws Exception {
        JdbcConnectivityTestJobDefinitionBuilder builder =
                new JdbcConnectivityTestJobDefinitionBuilder();
        inject(builder, "sourcePluginNameResolver", new ConnectivitySourcePluginNameResolver());
        inject(builder, "consoleSinkHoconBuilder", new ConsoleSinkHoconBuilder());
        inject(builder, "testJobEnvConfigBuilder", new TestJobEnvConfigBuilder());
        inject(builder, "seaTunnelJobConfigAssembler", new SeaTunnelJobConfigAssembler());
        return builder;
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
