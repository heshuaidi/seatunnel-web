package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncCheckDatasourceType {
    SOURCE("SOURCE", "源端"),
    SINK("SINK", "目标端"),
    CUSTOM("CUSTOM", "自定义");

    @EnumValue
    private final String code;
    private final String description;
}
