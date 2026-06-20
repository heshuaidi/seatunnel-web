package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.spi.bean.vo.MeasurementPreflightVO;

public interface MeasurementFileSchemaService {

    void checkOrThrow();

    MeasurementPreflightVO check();
}
