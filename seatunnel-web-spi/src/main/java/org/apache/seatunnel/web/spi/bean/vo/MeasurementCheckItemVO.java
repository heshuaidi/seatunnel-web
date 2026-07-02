package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class MeasurementCheckItemVO {

    private String name;

    private String status;

    private String message;

    private String suggestedDdl;
}
