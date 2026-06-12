package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.common.enums.SyncCheckType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_check_result")
public class SyncCheckResultEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;

    private String batchId;

    private Long taskId;

    private String taskCode;

    private String checkCode;

    private String checkName;

    private SyncCheckType checkType;

    private String renderedSql;

    private String actualValue;

    private SyncCheckExpectedOperator expectedOperator;

    private String expectedValue;

    private String compareToCheckCode;

    private String compareToActualValue;

    private Boolean passed;

    private Boolean failOnMismatch;

    private String errorMessage;

    private Date startTime;

    private Date endTime;

    private Date createTime;
}
