package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncAuditEventType {
    CREATE_BATCH("CREATE_BATCH", "创建批次"),
    READ_WATERMARK("READ_WATERMARK", "读取 watermark"),
    DISCOVER_FILES("DISCOVER_FILES", "发现文件"),
    RENDER_HOCON("RENDER_HOCON", "渲染 HOCON"),
    SUBMIT_JOB("SUBMIT_JOB", "提交任务"),
    POLL_STATUS("POLL_STATUS", "轮询状态"),
    ADVANCE_WATERMARK("ADVANCE_WATERMARK", "推进 watermark"),
    RUN_SUCCESS("RUN_SUCCESS", "运行成功"),
    RUN_FAILED("RUN_FAILED", "运行失败");

    @EnumValue
    private final String code;
    private final String description;
}
