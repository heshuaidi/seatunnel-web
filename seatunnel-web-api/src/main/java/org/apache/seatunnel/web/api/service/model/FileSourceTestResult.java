package org.apache.seatunnel.web.api.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSourceTestResult {

    private Boolean success;

    private String message;

    private String rootPath;

    @Builder.Default
    private List<String> sampleFiles = new ArrayList<>();
}
