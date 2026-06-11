package org.apache.seatunnel.web.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SyncAuditEventType {
    CREATE_BATCH("CREATE_BATCH", "创建批次"),
    CREATE_RUN("CREATE_RUN", "创建运行"),
    READ_WATERMARK("READ_WATERMARK", "读取 watermark"),
    DISCOVER_FILES("DISCOVER_FILES", "发现文件"),
    CLAIM_FILES("CLAIM_FILES", "声明文件"),
    MARK_FILES_PROCESSING("MARK_FILES_PROCESSING", "标记文件处理中"),
    MARK_FILES_SUCCESS("MARK_FILES_SUCCESS", "标记文件成功"),
    MARK_FILES_FAILED("MARK_FILES_FAILED", "标记文件失败"),
    RENDER_HOCON("RENDER_HOCON", "渲染 HOCON"),
    SUBMIT_JOB("SUBMIT_JOB", "提交任务"),
    POLL_STATUS("POLL_STATUS", "轮询状态"),
    VERIFYING("VERIFYING", "运行校验"),
    ADVANCE_WATERMARK("ADVANCE_WATERMARK", "推进 watermark"),
    MANUAL_UPDATE_WATERMARK("MANUAL_UPDATE_WATERMARK", "手动修正 watermark"),
    RUN_SUCCESS("RUN_SUCCESS", "运行成功"),
    RUN_FAILED("RUN_FAILED", "运行失败");

    @EnumValue
    private final String code;
    private final String description;
}
