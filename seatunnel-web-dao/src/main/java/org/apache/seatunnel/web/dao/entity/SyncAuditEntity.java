package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.common.enums.SyncAuditLevel;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_audit")
public class SyncAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;

    private String batchId;

    private Long taskId;

    private String taskCode;

    private SyncAuditEventType eventType;

    private SyncAuditLevel eventLevel;

    private String eventMessage;

    private String detailJson;

    private Date createTime;
}
