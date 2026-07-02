package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class MeasurementParsePreviewVO {

    private Long fileId;

    private Long taskId;

    private String fileName;

    private String parserType;

    private Boolean success;

    private Long rowCount;

    private Integer errorRowCount;

    private String errorMessage;

    private List<Map<String, Object>> rows = new ArrayList<>();

    private List<Map<String, Object>> errorRows = new ArrayList<>();
}
