package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PreviewHoconRequest {

    private Map<String, Object> params;
}
