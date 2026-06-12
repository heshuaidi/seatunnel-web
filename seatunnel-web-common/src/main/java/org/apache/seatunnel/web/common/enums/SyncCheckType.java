package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncCheckType {
    SOURCE_COUNT("SOURCE_COUNT", "源端数量"),
    SINK_COUNT("SINK_COUNT", "目标端数量"),
    ERROR_COUNT("ERROR_COUNT", "错误数量"),
    CUSTOM_COUNT("CUSTOM_COUNT", "自定义数量"),
    CUSTOM_BOOLEAN("CUSTOM_BOOLEAN", "自定义布尔校验");

    @EnumValue
    private final String code;
    private final String description;
}
