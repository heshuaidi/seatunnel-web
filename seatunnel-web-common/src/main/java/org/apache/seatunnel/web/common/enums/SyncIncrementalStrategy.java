package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncIncrementalStrategy {
    UPDATE_TIME_RANGE("UPDATE_TIME_RANGE", "更新时间范围"),
    ID_RANGE("ID_RANGE", "ID 范围"),
    FILE_MANIFEST("FILE_MANIFEST", "文件清单"),
    FILE_MTIME("FILE_MTIME", "文件修改时间"),
    COMPOSITE_CURSOR("COMPOSITE_CURSOR", "组合游标"),
    CUSTOM_SQL_CURSOR("CUSTOM_SQL_CURSOR", "自定义 SQL 游标");

    @EnumValue
    private final String code;
    private final String description;
}
