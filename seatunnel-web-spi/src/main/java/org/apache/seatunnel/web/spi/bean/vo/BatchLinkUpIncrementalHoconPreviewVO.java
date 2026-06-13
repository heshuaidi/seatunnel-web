package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class BatchLinkUpIncrementalHoconPreviewVO {

    private String originalHocon;

    private String renderedHocon;

    private Map<String, Object> variables;

    private List<String> missingVariables = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();
}
