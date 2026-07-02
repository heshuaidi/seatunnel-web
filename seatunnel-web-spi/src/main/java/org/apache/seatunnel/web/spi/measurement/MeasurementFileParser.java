package org.apache.seatunnel.web.spi.measurement;

public interface MeasurementFileParser {

    String parserType();

    ParsedMeasurementResult parse(MeasurementFileContext fileContext);

    default ParsedMeasurementResult parse(MeasurementFileContext fileContext, MeasurementRowWriter rowWriter) {
        ParsedMeasurementResult result = parse(fileContext);
        if (rowWriter != null && result != null && result.getMeasurementRows() != null) {
            for (java.util.Map<String, Object> row : result.getMeasurementRows()) {
                rowWriter.write(row);
            }
        }
        return result;
    }
}
