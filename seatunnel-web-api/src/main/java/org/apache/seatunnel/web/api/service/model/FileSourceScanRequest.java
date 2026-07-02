package org.apache.seatunnel.web.api.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.seatunnel.web.spi.enums.DbType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSourceScanRequest {

    private DbType sourceType;

    private FileDataSourceConfig config;

    private String rootPath;

    private String includePatterns;

    private String excludePatterns;

    private Boolean recursive;

    private Integer maxDepth;

    private Integer maxFiles;
}
