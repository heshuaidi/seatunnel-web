package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncBoundaryValueSource {
    WATERMARK("WATERMARK", "从成功 watermark 获取"),
    SQL("SQL", "执行 SQL 获取"),
    PARAM("PARAM", "运行参数传入"),
    FIXED("FIXED", "固定值"),
    NOW("NOW", "当前时间"),
    NONE("NONE", "不使用");

    @EnumValue
    private final String code;
    private final String description;
}
