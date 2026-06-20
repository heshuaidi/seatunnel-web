package org.apache.seatunnel.web.spi.measurement;

import java.util.Map;

@FunctionalInterface
public interface MeasurementRowWriter {

    void write(Map<String, Object> row);
}
