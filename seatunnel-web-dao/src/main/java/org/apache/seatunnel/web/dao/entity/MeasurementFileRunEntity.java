package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.MeasurementRunStatus;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_measurement_file_run")
public class MeasurementFileRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;

    private String batchId;

    private Long taskId;

    private SyncTriggerType triggerType;

    private MeasurementRunStatus status;

    private Long sourceDatasourceId;

    private Integer scannedCount;

    private Integer discoveredCount;

    private Integer skippedCount;

    private Integer failedCount;

    private String errorMessage;

    private Date startTime;

    private Date endTime;

    private Date createTime;

    private Date updateTime;
}
