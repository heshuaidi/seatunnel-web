package org.apache.seatunnel.web.api.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteFileScanRequest {

    private Long taskId;

    private String taskCode;

    private String remotePath;

    private String pattern;

    private Boolean recursive;

    private String timezone;
}
