package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncBatchStatus;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_batch")
public class SyncBatchEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;

    private Long taskId;

    private String taskCode;

    private SyncTriggerType triggerType;

    private SyncRunMode runMode;

    private String batchStartValue;

    private String batchEndValue;

    private Date batchStartTime;

    private Date batchEndTime;

    private SyncBatchStatus status;

    private Long expectedCount;

    private Long sourceCount;

    private Long sinkCount;

    private Long errorCount;

    private String errorMessage;

    private Date createTime;

    private Date updateTime;
}
