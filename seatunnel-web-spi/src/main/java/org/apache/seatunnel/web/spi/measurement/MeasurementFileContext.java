package org.apache.seatunnel.web.spi.measurement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;

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

    private String sourceType;

    private String rootPath;

    private String relativePath;

    private String fullPath;

    private Long fileSize;

    private Date lastModifiedTime;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
