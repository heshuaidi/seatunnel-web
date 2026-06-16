package org.apache.seatunnel.web.spi.measurement;

public interface MeasurementFileParser {

    ParsedMeasurementResult parse(MeasurementFileContext fileContext);
}
