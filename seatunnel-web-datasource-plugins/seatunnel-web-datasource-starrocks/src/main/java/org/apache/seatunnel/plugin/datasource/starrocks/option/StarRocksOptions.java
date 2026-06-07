package org.apache.seatunnel.plugin.datasource.starrocks.option;

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

    Option<Map<String, String>> STARROCKS_CONFIG =
            Options.key("starrocks.config").mapType().noDefaultValue().withDescription("starrocks config");
}
