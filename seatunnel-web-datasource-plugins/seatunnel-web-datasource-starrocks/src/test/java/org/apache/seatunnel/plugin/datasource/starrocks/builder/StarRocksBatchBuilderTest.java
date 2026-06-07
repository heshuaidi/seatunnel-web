package org.apache.seatunnel.plugin.datasource.starrocks.builder;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.seatunnel.plugin.datasource.api.hocon.HoconBuildContext;
import org.apache.seatunnel.plugin.datasource.starrocks.param.StarRocksParamConverter;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarRocksBatchBuilderTest {

    private final StarRocksBatchBuilder builder = new StarRocksBatchBuilder();

    @Test
    void buildsJdbcCompatibleSourceHocon() {
        BaseConnectionParam param = new StarRocksParamConverter().createConnectionParams(
                "{"
                        + "\"host\":\"starrocks-dev\","
                        + "\"queryPort\":\"9030\","
                        + "\"httpPort\":\"8040\","
                        + "\"database\":\"st_test\","
                        + "\"user\":\"st\","
                        + "\"password\":\"St_dev_123456!\""
                        + "}");

        Config source = builder.buildSourceHocon(HoconBuildContext.builder()
                .connectionConfig(ConfigFactory.parseString(JSONUtils.toJsonString(param)))
                .nodeConfig(ConfigFactory.parseString("table = sink_user"))
                .build());

        assertEquals("StarRocks", builder.pluginName());
        assertEquals("jdbc:mysql://starrocks-dev:9030/st_test", source.getString("url"));
        assertEquals("com.mysql.cj.jdbc.Driver", source.getString("driver"));
        assertEquals("st", source.getString("username"));
        assertEquals("st_test.sink_user", source.getString("table_path"));
    }

    @Test
    void buildsStarRocksSinkHoconWithQueryAndHttpPorts() {
        Config sink = builder.buildSinkHocon(HoconBuildContext.builder()
                .connectionConfig(connectionConfig())
                .nodeConfig(ConfigFactory.parseString("table = sink_user"))
                .build());

        assertEquals("StarRocks", builder.pluginName());
        assertEquals(List.of("starrocks-dev:8040"), sink.getStringList("nodeUrls"));
        assertEquals("jdbc:mysql://starrocks-dev:9030/st_test", sink.getString("base-url"));
        assertEquals("st", sink.getString("username"));
        assertEquals("St_dev_123456!", sink.getString("password"));
        assertEquals("st_test", sink.getString("database"));
        assertEquals("sink_user", sink.getString("table"));
        assertEquals("JSON", sink.getString("starrocks.config.format"));
        assertTrue(sink.getBoolean("starrocks.config.strip_outer_array"));
    }

    @Test
    void treatsPortAsQueryPortAndDefaultsHttpPort() {
        Config sink = builder.buildSinkHocon(HoconBuildContext.builder()
                .connectionConfig(ConfigFactory.parseString(
                        "host = starrocks-dev\n"
                                + "port = 9031\n"
                                + "database = st_test\n"
                                + "user = st\n"
                                + "password = \"St_dev_123456!\""))
                .nodeConfig(ConfigFactory.parseString("targetTableName = sink_user"))
                .build());

        assertEquals(List.of("starrocks-dev:8040"), sink.getStringList("nodeUrls"));
        assertEquals("jdbc:mysql://starrocks-dev:9031/st_test", sink.getString("base-url"));
        assertEquals("sink_user", sink.getString("table"));
    }

    private Config connectionConfig() {
        return ConfigFactory.parseString(
                "host = starrocks-dev\n"
                        + "queryPort = 9030\n"
                        + "httpPort = 8040\n"
                        + "database = st_test\n"
                        + "user = st\n"
                        + "password = \"St_dev_123456!\"\n"
                        + "url = \"jdbc:mysql://starrocks-dev:9999/st_test\"");
    }
}
