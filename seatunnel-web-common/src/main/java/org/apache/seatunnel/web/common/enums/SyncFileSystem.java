package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncFileSystem {
    LOCAL("LOCAL", "本地文件系统"),
    FTP("FTP", "FTP"),
    SFTP("SFTP", "SFTP"),
    NAS("NAS", "NAS");

    @EnumValue
    private final String code;
    private final String description;
}
