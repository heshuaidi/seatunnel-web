package org.apache.seatunnel.web.api.service.impl;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "seatunnel.sync")
public class SyncRunProperties {

    private long pollIntervalMs = 3000L;

    private long pollTimeoutMs = 600000L;

    private int checkSqlTimeoutSeconds = 60;
}
