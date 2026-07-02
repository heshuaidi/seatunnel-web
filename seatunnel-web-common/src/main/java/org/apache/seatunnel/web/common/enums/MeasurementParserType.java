package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MeasurementParserType {
    SIMPLE_CSV("SIMPLE_CSV", "Simple CSV"),
    SIMPLE_TEXT("SIMPLE_TEXT", "Simple text line"),
    WAT("WAT", "WAT"),
    CP("CP", "CP"),
    CUSTOM("CUSTOM", "CUSTOM");

    @EnumValue
    private final String code;

    private final String description;
}
