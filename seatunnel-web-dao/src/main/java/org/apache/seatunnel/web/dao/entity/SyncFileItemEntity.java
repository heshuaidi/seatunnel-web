package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.common.enums.SyncFileSystem;
import org.apache.seatunnel.web.common.enums.SyncSourceType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_file_item")
public class SyncFileItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String batchId;

    private String runId;

    private SyncSourceType sourceType;

    private SyncFileSystem fileSystem;

    private String filePath;

    private String fileName;

    private String relativePath;

    private Long fileSize;

    private Date lastModifiedTime;

    private String checksum;

    private Date discoveredTime;

    private SyncFileItemStatus status;

    private String errorMessage;

    private Date createTime;

    private Date updateTime;
}
