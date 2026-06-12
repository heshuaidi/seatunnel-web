package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncCheckDatasourceType;
import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.common.enums.SyncCheckType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_check_config")
public class SyncCheckConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String checkCode;

    private String checkName;

    private SyncCheckType checkType;

    private SyncCheckDatasourceType datasourceType;

    private Long datasourceId;

    private String sqlText;

    private SyncCheckExpectedOperator expectedOperator;

    private String expectedValue;

    private String compareToCheckCode;

    private Boolean failOnMismatch;

    private Boolean enabled;

    private Integer sortOrder;

    private String description;

    private Date createTime;

    private Date updateTime;
}
