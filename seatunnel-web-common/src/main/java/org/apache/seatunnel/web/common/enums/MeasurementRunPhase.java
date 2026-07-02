package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MeasurementRunPhase {
    DISCOVER("DISCOVER", "文件发现"),
    PARSE("PARSE", "文件解析"),
    LOAD("LOAD", "数据装载"),
    PARSE_LOAD("PARSE_LOAD", "解析并装载");

    @EnumValue
    private final String code;

    private final String description;
}
