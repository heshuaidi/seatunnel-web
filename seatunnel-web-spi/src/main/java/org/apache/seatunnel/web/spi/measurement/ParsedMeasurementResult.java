package org.apache.seatunnel.web.spi.measurement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedMeasurementResult {

    private Boolean success;

    private Long rowCount;

    private Integer errorRowCount;

    private String errorMessage;

    @Builder.Default
    private Map<String, Object> fileMetadata = new HashMap<>();

    @Builder.Default
    private Map<String, Object> header = new HashMap<>();

    @Builder.Default
    private List<Map<String, Object>> measurementRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> errorRows = new ArrayList<>();
}
