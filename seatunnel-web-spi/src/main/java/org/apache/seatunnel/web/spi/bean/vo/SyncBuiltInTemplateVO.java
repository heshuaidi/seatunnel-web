package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.List;

@Data
public class SyncBuiltInTemplateVO {

    private String templateCode;

    private String templateName;

    private String description;

    private String sourceType;

    private String sinkType;

    private String incrementalStrategy;

    private String hoconTemplate;

    private List<String> requiredVariables;

    private List<String> docs;
}
