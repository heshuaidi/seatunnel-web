package org.apache.seatunnel.plugin.datasource.starrocks.option;

import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.plugin.datasource.api.jdbc.AbstractSourceOptionRule;
import org.apache.seatunnel.web.common.config.OptionRule;

import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.DATABASE;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.MAX_RETRIES;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.NODE_URLS;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.PASSWORD;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.SCAN_BATCH_ROWS;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.SCAN_FILTER;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.SCHEMA;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.TABLE;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.TABLE_LIST;
import static org.apache.seatunnel.plugin.datasource.starrocks.option.StarRocksOptions.USERNAME;

@Slf4j
public class StarRocksSourceOptionRule extends AbstractSourceOptionRule {

    @Override
    public String pluginName() {
        return "StarRocks";
    }

    @Override
    public OptionRule sourceOptionRule() {
        return OptionRule.builder()
                .required(NODE_URLS, USERNAME, DATABASE)
                .exclusive(TABLE, TABLE_LIST)
                .optional(PASSWORD, SCHEMA, SCAN_FILTER, SCAN_BATCH_ROWS, MAX_RETRIES)
                .build();
    }
}
