package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MeasurementLoadBatchMode {
    ONE_FILE_ONE_JOB("ONE_FILE_ONE_JOB", "一个文件一个 SeaTunnel Job"),
    MULTI_FILE_ONE_JOB("MULTI_FILE_ONE_JOB", "多个文件一个 SeaTunnel Job");

    @EnumValue
    private final String code;

    private final String description;
}
