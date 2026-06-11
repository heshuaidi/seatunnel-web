package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncEngineType;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncSinkType;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncTaskStatus;
import org.apache.seatunnel.web.common.enums.SyncTaskType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_task")
public class SyncTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskCode;

    private String taskName;

    private SyncTaskType taskType;

    private SyncSourceType sourceType;

    private SyncSinkType sinkType;

    private SyncEngineType engineType;

    private Long clientId;

    private Boolean incrementalEnabled;

    private SyncIncrementalStrategy incrementalStrategy;

    private SyncTaskStatus status;

    private Long currentVersionId;

    private String description;

    private String createUser;

    private Date createTime;

    private String updateUser;

    private Date updateTime;
}
