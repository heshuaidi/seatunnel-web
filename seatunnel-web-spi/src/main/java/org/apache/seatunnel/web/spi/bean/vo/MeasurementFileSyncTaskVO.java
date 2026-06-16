package org.apache.seatunnel.web.spi.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.apache.seatunnel.web.common.enums.MeasurementDedupStrategy;
import org.apache.seatunnel.web.common.enums.MeasurementDiscoveryMode;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;

import java.util.Date;

@Data
public class MeasurementFileSyncTaskVO {

    private Long id;

    private String taskName;

    private String taskCode;

    private MeasurementParserType parserType;

    private Long sourceDatasourceId;

    private String sourceDatasourceName;

    private String sourceType;

    private String sourceRootPath;

    private String includePatterns;

    private String excludePatterns;

    private Boolean recursive;

    private Integer maxDepth;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
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

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
