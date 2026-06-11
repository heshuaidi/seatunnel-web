package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class CreateTaskFromTemplateResultVO {

    private Long taskId;

    private String taskCode;

    private Long versionId;

    private Long incrementalConfigId;

    private Long watermarkId;

    private Integer createdCheckCount;

    private List<String> warnings;

    private List<String> nextActions;
}
