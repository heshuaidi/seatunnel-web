package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class SyncHoconDiagnosticVO {

    private Boolean renderable;

    private List<String> missingVariables;

    private String renderedHash;

    private String renderedHocon;

    private String renderedHoconPreview;

    private Set<String> variablesUsed;

    private Map<String, Object> maskedParams;

    private String errorMessage;
}
