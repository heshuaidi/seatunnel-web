package org.apache.seatunnel.web.api.scheduler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "seatunnel.scheduler")
public class SchedulerProperties {

    private boolean enabled = true;

    private String token = "";

    private String operator = "scheduler";

    private int runSyncDefaultTimeoutSeconds = 600;

    private int runSyncDefaultPollIntervalSeconds = 5;
}
