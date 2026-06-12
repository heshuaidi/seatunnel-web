package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncWatermarkValueType {
    DATETIME("DATETIME", "日期时间"),
    LONG("LONG", "长整型"),
    STRING("STRING", "字符串"),
    JSON("JSON", "JSON");

    @EnumValue
    private final String code;
    private final String description;
}
