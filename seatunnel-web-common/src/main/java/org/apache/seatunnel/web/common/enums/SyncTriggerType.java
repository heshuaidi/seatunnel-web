package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncTriggerType {
    MANUAL("MANUAL", "手动触发"),
    SCHEDULE("SCHEDULE", "调度触发"),
    BACKFILL("BACKFILL", "补数触发"),
    RETRY("RETRY", "重试触发");

    @EnumValue
    private final String code;
    private final String description;
}
