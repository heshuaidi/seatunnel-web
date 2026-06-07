package org.apache.seatunnel.plugin.datasource.starrocks.option;

import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.plugin.datasource.api.jdbc.AbstractSourceOptionRule;

@Slf4j
public class StarRocksSourceOptionRule extends AbstractSourceOptionRule {

    @Override
    public String pluginName() {
        return "StarRocks";
    }
}
