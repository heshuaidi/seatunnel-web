package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.SyncBoundaryMode;
import org.apache.seatunnel.web.common.enums.SyncBoundaryValueSource;
import org.apache.seatunnel.web.common.enums.SyncFileCursorMode;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncWatermarkValueType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_sync_incremental_config")
public class SyncIncrementalConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long batchLinkUpTaskId;

    private Boolean enabled;

    private String rangeType;

    private SyncBoundaryMode boundaryMode;

    private SyncBoundaryValueSource startValueSource;

    private SyncBoundaryValueSource endValueSource;

    private SyncBoundaryValueSource startTimeSource;

    private SyncBoundaryValueSource endTimeSource;

    private Long boundaryDatasourceId;

    private String batchPrepareSql;

    private String batchStartValueSql;

    private String batchEndValueSql;

    private String batchStartTimeSql;

    private String batchEndTimeSql;

    private String fixedStartValue;

    private String fixedEndValue;

    private String fixedStartTime;

    private String fixedEndTime;

    private String defaultParamsJson;

    private String customContextJson;

    private Boolean successUpdateWatermark;

    private Boolean checkEnabled;

    private Long checkDatasourceId;

    private String checkSql;

    private String cleanupSql;

    private Long cleanupDatasourceId;

    private Boolean cleanupOnRerun;

    private Boolean cleanupBeforeRetryOnly;

    private SyncSourceType sourceType;

    private SyncIncrementalStrategy strategy;

    private String watermarkKey;

    private String watermarkField;

    private SyncWatermarkValueType watermarkFieldType;

    private String startValue;

    private Integer lookbackSeconds;

    private Integer maxBatchSeconds;

    private Long maxBatchRows;

    private String filePath;

    private String filePattern;

    private Boolean fileRecursive;

    private String fileTimezone;

    private SyncFileCursorMode fileCursorMode;

    private Boolean backfillAdvanceWatermark;

    private Date createTime;

    private Date updateTime;
}
