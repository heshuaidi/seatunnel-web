package org.apache.seatunnel.plugin.datasource.starrocks.option;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.seatunnel.web.common.config.Option;
import org.apache.seatunnel.web.common.config.Options;

import java.util.List;
import java.util.Map;

public interface StarRocksOptions {

    Option<List<String>> NODE_URLS =
            Options.key("nodeUrls").listType().noDefaultValue().withDescription("StarRocks BE HTTP node urls");

    Option<String> BASE_URL =
            Options.key("base-url").stringType().noDefaultValue().withDescription("StarRocks JDBC base url");

    Option<String> USERNAME =
            Options.key("username").stringType().noDefaultValue().withDescription("username");

    Option<String> PASSWORD =
            Options.key("password").stringType().noDefaultValue().withDescription("password");

    Option<String> DATABASE =
            Options.key("database").stringType().noDefaultValue().withDescription("database");

    Option<String> TABLE =
            Options.key("table").stringType().noDefaultValue().withDescription("table");

    Option<List<Map<String, Object>>> TABLE_LIST =
            Options.key("table_list")
                    .type(new TypeReference<List<Map<String, Object>>>() {})
                    .noDefaultValue()
                    .withDescription("StarRocks source table list with schema");

    Option<Map<String, Object>> SCHEMA =
            Options.key("schema")
                    .mapObjectType()
                    .noDefaultValue()
                    .withDescription("StarRocks source schema");

    Option<String> SCAN_FILTER =
            Options.key("scan_filter").stringType().noDefaultValue().withDescription("StarRocks source scan filter");

    Option<Integer> SCAN_BATCH_ROWS =
            Options.key("scan_batch_rows").intType().noDefaultValue().withDescription("StarRocks source scan batch rows");

    Option<Integer> MAX_RETRIES =
            Options.key("max_retries").intType().noDefaultValue().withDescription("StarRocks source max retries");

    Option<Map<String, String>> STARROCKS_CONFIG =
            Options.key("starrocks.config").mapType().noDefaultValue().withDescription("starrocks config");
}
