package org.apache.seatunnel.web.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.MeasurementFileStatus;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;
import org.apache.seatunnel.web.spi.enums.DbType;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seatunnel_web_measurement_file")
public class MeasurementFileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String batchId;

    private String runId;

    private Long sourceDatasourceId;

    private DbType sourceType;

    private MeasurementParserType parserType;

    private String rootPath;

    private String relativePath;

    private String fileName;

    private String fullPath;

    private Long fileSize;

    private Date lastModifiedTime;

    private String checksum;

    private String checksumType;

    private MeasurementFileStatus fileStatus;

    private Date discoverTime;

    private Date parseTime;

    private Date loadTime;

    private String errorMessage;

    private Date createTime;

    private Date updateTime;
}
