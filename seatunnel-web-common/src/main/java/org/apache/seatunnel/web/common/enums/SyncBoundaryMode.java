package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncBoundaryMode {
    SEPARATE_SQL("SEPARATE_SQL", "分别执行边界 SQL"),
    PREPARE_SQL("PREPARE_SQL", "一次执行 batch_prepare_sql"),
    SIMPLE_WATERMARK("SIMPLE_WATERMARK", "仅使用 watermark");

    @EnumValue
    private final String code;
    private final String description;
}
