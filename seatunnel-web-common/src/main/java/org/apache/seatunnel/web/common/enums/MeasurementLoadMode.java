package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MeasurementLoadMode {
    APPEND("APPEND", "追加写入"),
    UPSERT("UPSERT", "主键更新");

    @EnumValue
    private final String code;

    private final String description;
}
