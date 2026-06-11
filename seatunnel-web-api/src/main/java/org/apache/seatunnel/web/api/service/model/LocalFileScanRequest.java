package org.apache.seatunnel.web.api.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalFileScanRequest {

    private String basePath;

    private String pattern;

    private Boolean recursive;

    private String timezone;
}
