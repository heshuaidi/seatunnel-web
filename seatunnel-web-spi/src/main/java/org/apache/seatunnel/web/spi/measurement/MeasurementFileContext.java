package org.apache.seatunnel.web.spi.measurement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementFileContext {

    private Long fileId;

    private Long taskId;

    private MeasurementParserType parserType;

    private String runId;

    private String batchId;

    private String sourceType;

    private String rootPath;

    private String relativePath;

    private String fullPath;

    private String fileName;

    private Long fileSize;

    private Date lastModifiedTime;

    private String parserConfigJson;

    private String charset;

    private Integer parseMaxErrorRows;

    private Boolean parseFailFast;

    private transient InputStream inputStream;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
