package org.apache.seatunnel.web.api.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveredFile {

    private String absolutePath;

    private String fileName;

    private String relativePath;

    private Long size;

    private LocalDateTime lastModifiedTime;
}
