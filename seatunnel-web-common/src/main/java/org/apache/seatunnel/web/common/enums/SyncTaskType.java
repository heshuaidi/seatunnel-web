package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncTaskType {
    BATCH("BATCH", "批同步"),
    STREAM("STREAM", "流同步");

    @EnumValue
    private final String code;
    private final String description;
}
