package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncCheckExpectedOperator {
    EQ("EQ", "等于"),
    NE("NE", "不等于"),
    GT("GT", "大于"),
    GE("GE", "大于等于"),
    LT("LT", "小于"),
    LE("LE", "小于等于"),
    IS_NULL("IS_NULL", "为空"),
    IS_NOT_NULL("IS_NOT_NULL", "不为空");

    @EnumValue
    private final String code;
    private final String description;
}
