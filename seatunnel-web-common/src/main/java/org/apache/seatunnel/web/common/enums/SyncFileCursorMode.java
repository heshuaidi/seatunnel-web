package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncFileCursorMode {
    MTIME("MTIME", "修改时间"),
    PATH_MTIME_SIZE("PATH_MTIME_SIZE", "路径、修改时间和大小"),
    CHECKSUM("CHECKSUM", "校验和"),
    MANIFEST("MANIFEST", "清单");

    @EnumValue
    private final String code;
    private final String description;
}
