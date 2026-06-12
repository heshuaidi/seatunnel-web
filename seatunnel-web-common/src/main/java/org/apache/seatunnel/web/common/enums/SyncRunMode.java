package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncRunMode {
    NORMAL("NORMAL", "正常运行"),
    BACKFILL("BACKFILL", "补数"),
    RERUN("RERUN", "重跑");

    @EnumValue
    private final String code;
    private final String description;
}
