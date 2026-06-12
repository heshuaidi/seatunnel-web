package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

@Data
public class HoconPreviewVO {

    private Long taskId;

    private String taskCode;

    private Long taskVersionId;

    private String renderedHocon;

    private String hoconHash;
}
