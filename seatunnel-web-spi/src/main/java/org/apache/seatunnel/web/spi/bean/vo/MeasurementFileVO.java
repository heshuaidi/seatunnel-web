package org.apache.seatunnel.web.spi.bean.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.apache.seatunnel.web.common.enums.MeasurementFileStatus;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;

import java.util.Date;

@Data
public class MeasurementFileVO {

    private Long id;

    private Long taskId;

    private String batchId;

    private String runId;

    private Long sourceDatasourceId;

    private String sourceType;

    private MeasurementParserType parserType;

    private String rootPath;

    private String relativePath;

    private String fileName;

    private String fullPath;

    private Long fileSize;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastModifiedTime;

    private String checksum;

    private String checksumType;

    private MeasurementFileStatus fileStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date discoverTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date parseTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date loadTime;

    private String stagingFilePath;

    private Long parsedRowCount;

    private Long loadedRowCount;

    private Integer parseErrorCount;

    private String parserConfigSnapshot;

    private String loadJobId;

    private String loadJobName;

    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
