package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MeasurementFileStatus {
    DISCOVERED("DISCOVERED", "已发现"),
    SKIPPED("SKIPPED", "已跳过"),
    PARSE_PENDING("PARSE_PENDING", "等待解析"),
    PARSING("PARSING", "解析中"),
    PARSED("PARSED", "已解析"),
    LOAD_PENDING("LOAD_PENDING", "等待入库"),
    LOADED("LOADED", "已入库"),
    FAILED("FAILED", "失败");

    @EnumValue
    private final String code;

    private final String description;
}
