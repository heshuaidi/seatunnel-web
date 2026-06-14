package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_task_lock")
public class SyncTaskLockEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String watermarkKey;

    private String lockToken;

    private String lockOwner;

    private String runId;

    private String batchId;

    private Date lockedAt;

    private Date expiresAt;

    private String status;

    private Date createTime;

    private Date updateTime;
}
