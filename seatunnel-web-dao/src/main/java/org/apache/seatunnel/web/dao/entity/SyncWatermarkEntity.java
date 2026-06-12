package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncWatermarkValueType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_watermark")
public class SyncWatermarkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String watermarkKey;

    private String currentValue;

    private String previousValue;

    private SyncWatermarkValueType currentValueType;

    private Long lastSuccessRunId;

    private String lastSuccessBatchId;

    private Date updateTime;
}
