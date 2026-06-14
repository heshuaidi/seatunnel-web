package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncRunStatus {
    CREATED("CREATED", "已创建"),
    SUBMITTED("SUBMITTED", "已提交"),
    RUNNING("RUNNING", "运行中"),
    SUCCESS("SUCCESS", "成功"),
    CHECK_FAILED("CHECK_FAILED", "校验失败"),
    FAILED("FAILED", "失败"),
    CANCELED("CANCELED", "已取消");

    @EnumValue
    private final String code;
    private final String description;
}
