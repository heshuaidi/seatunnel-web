package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.MeasurementDedupStrategy;
import org.apache.seatunnel.web.common.enums.MeasurementDiscoveryMode;
import org.apache.seatunnel.web.common.enums.MeasurementLoadBatchMode;
import org.apache.seatunnel.web.common.enums.MeasurementLoadMode;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_measurement_file_task")
public class MeasurementFileSyncTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;

    private String taskCode;

    private MeasurementParserType parserType;

    private String parserConfigJson;

    private String parseCharset;

    private Integer parseMaxErrorRows;

    private Boolean parseFailFast;

    private Long sourceDatasourceId;

    private String sourceRootPath;

    private String includePatterns;

    private String excludePatterns;

    private Boolean recursive;

    private Integer maxDepth;

    private Date minLastModifiedTime;

    private Integer fileStableSeconds;

    private Boolean enabled;

    private MeasurementDiscoveryMode discoveryMode;

    private String watermarkKey;

    private String currentWatermark;

    private MeasurementDedupStrategy dedupStrategy;

    private Boolean checksumEnabled;

    private Integer maxFilesPerRun;

    private Integer lockTtlMinutes;

    private String scheduleCron;

    private String stagingDir;

    private String stagingFormat;

    private Integer stagingRetentionDays;

    private Boolean keepStagingFile;

    private Long targetDatasourceId;

    private String targetDatabase;

    private String targetTable;

    private MeasurementLoadMode loadMode;

    private MeasurementLoadBatchMode loadBatchMode;

    private String starrocksNodeUrls;

    private String starrocksBaseUrl;

    private Integer maxFilesPerParseRun;

    private Boolean retryParseFailed;

    private Boolean retryLoadFailed;

    private Boolean cleanupBeforeReload;

    private Long seatunnelClientId;

    private String description;

    private Date createTime;

    private Date updateTime;
}
