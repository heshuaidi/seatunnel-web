package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncSinkType {
    STARROCKS("STARROCKS", "StarRocks"),
    JDBC("JDBC", "JDBC 数据源"),
    LOCAL_FILE("LOCAL_FILE", "本地文件");

    @EnumValue
    private final String code;
    private final String description;
}
