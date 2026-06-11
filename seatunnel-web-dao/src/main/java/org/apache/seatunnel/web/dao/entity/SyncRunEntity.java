package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncRunStatus;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_run")
public class SyncRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;

    private Long taskId;

    private Long taskVersionId;

    private String batchId;

    private SyncTriggerType triggerType;

    private String schedulerRunId;

    private String runParamJson;

    private String generatedHocon;

    private String seatunnelJobId;

    private String seatunnelJobName;

    private SyncRunStatus status;

    private String errorMessage;

    private Date submitTime;

    private Date startTime;

    private Date endTime;

    private Long sourceCount;

    private Long sinkCount;

    private Long errorCount;

    private Date createTime;

    private Date updateTime;
}
