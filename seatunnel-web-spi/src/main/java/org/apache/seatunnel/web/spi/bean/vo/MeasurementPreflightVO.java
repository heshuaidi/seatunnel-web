package org.apache.seatunnel.web.spi.bean.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MeasurementPreflightVO {

    private Boolean success;

    private List<String> errors = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();

    private List<MeasurementCheckItemVO> checks = new ArrayList<>();
}
