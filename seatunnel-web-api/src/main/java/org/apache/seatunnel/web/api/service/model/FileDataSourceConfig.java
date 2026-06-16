package org.apache.seatunnel.web.api.service.model;

import lombok.Data;

@Data
public class FileDataSourceConfig {

    private String type;

    private String dbType;

    private String host;

    private Integer port;

    private String username;

    private String password;

    private String rootPath;

    private Boolean passiveMode;

    private String description;

    private Boolean enabled;
}
