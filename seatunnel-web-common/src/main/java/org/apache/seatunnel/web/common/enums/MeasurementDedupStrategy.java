package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MeasurementDedupStrategy {
    PATH_SIZE_MTIME("PATH_SIZE_MTIME", "路径 + 大小 + 修改时间"),
    PATH_CHECKSUM("PATH_CHECKSUM", "路径 + 校验和"),
    PATH_ONLY("PATH_ONLY", "路径");

    @EnumValue
    private final String code;

    private final String description;
}
