package org.apache.seatunnel.plugin.datasource.starrocks.builder;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.seatunnel.plugin.datasource.api.hocon.HoconBuildContext;
import org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksSourceOptionRule;
import org.apache.seatunnel.plugin.datasource.starrocks.param.StarRocksDataSourceProcessor;
import org.apache.seatunnel.plugin.datasource.starrocks.param.StarRocksParamConverter;
import org.apache.seatunnel.web.common.config.ConfigValidator;
import org.apache.seatunnel.web.common.config.ReadonlyConfig;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.apache.seatunnel.web.spi.enums.DbType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarRocksBatchBuilderTest {

    private final StarRocksBatchBuilder builder = new StarRocksBatchBuilder();

    @Test
    void buildsStarRocksSourceHoconWithSchema() {
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
                .nodeConfig(ConfigFactory.parseString(
                        "table = sink_user\n"
                                + "schema { fields { id = \"BIGINT\", name = \"VARCHAR(64)\" } }\n"
                                + "extraParams = [\n"
                                + "  { key = \"scan_filter\", value = \"dt >= '2026-01-01'\" },\n"
                                + "  { key = \"scan_batch_rows\", value = 4096 },\n"
                                + "  { key = \"max_retries\", value = 3 }\n"
                                + "]"))
                .build());

        assertEquals("StarRocks", builder.pluginName());
        assertEquals(List.of("starrocks-dev:8040"), source.getStringList("nodeUrls"));
        assertEquals("st", source.getString("username"));
        assertEquals("St_dev_123456!", source.getString("password"));
        assertEquals("st_test", source.getString("database"));
        assertEquals("sink_user", source.getString("table"));
        assertEquals("bigint", source.getString("schema.fields.id"));
        assertEquals("string", source.getString("schema.fields.name"));
        assertEquals("dt >= '2026-01-01'", source.getString("scan_filter"));
        assertEquals(4096, source.getInt("scan_batch_rows"));
        assertEquals(3, source.getInt("max_retries"));

        ConfigValidator.of(ReadonlyConfig.fromConfig(source))
                .validate(new StarRocksSourceOptionRule().sourceOptionRule());
    }

    @Test
    void buildsStarRocksMultiTableSourceHoconWithTableListSchemas() {
        Config source = builder.buildSourceHocon(HoconBuildContext.builder()
                .connectionConfig(connectionConfig())
                .nodeConfig(ConfigFactory.parseString(
                        "multiTable = true\n"
                                + "table_list = [\n"
                                + "  { table = \"st_test.users\", schema { fields { id = \"BIGINT\", name = \"VARCHAR(64)\" } } },\n"
                                + "  { table = \"orders\", schema { fields { id = \"BIGINT\", amount = \"DECIMAL(10,2)\", create_time = \"DATETIME\" } } }\n"
                                + "]"))
                .build());

        assertEquals("st_test", source.getString("database"));

        List<? extends Config> tableList = source.getConfigList("table_list");
        assertEquals(2, tableList.size());

        assertEquals("users", tableList.get(0).getString("table"));
        assertEquals("bigint", tableList.get(0).getString("schema.fields.id"));
        assertEquals("string", tableList.get(0).getString("schema.fields.name"));

        assertEquals("orders", tableList.get(1).getString("table"));
        assertEquals("decimal(10,2)", tableList.get(1).getString("schema.fields.amount"));
        assertEquals("timestamp", tableList.get(1).getString("schema.fields.create_time"));

        ConfigValidator.of(ReadonlyConfig.fromConfig(source))
                .validate(new StarRocksSourceOptionRule().sourceOptionRule());
    }

    @Test
    void starRocksProcessorReturnsStarRocksDbType() {
        assertEquals(DbType.STARROCKS, new StarRocksDataSourceProcessor().getDbType());
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
