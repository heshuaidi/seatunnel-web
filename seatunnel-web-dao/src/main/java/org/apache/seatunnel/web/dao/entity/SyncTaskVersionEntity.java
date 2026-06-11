package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncPublishStatus;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_task_version")
public class SyncTaskVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Integer versionNo;

    private String hoconTemplate;

    private String hoconHash;

    private String paramSchemaJson;

    private SyncPublishStatus publishStatus;

    private String createUser;

    private Date createTime;
}
