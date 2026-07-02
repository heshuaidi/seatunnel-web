package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MeasurementDiscoveryMode {
    BY_LAST_MODIFIED("BY_LAST_MODIFIED", "按最后修改时间"),
    BY_FILE_NAME("BY_FILE_NAME", "按文件名"),
    FULL_SCAN("FULL_SCAN", "全量扫描");

    @EnumValue
    private final String code;

    private final String description;
}
