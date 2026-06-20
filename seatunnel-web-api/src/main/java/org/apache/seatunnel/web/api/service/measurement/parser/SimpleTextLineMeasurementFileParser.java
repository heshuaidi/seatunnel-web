package org.apache.seatunnel.web.api.service.measurement.parser;

import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;
import org.apache.seatunnel.web.spi.measurement.MeasurementFileContext;
import org.apache.seatunnel.web.spi.measurement.MeasurementFileParser;
import org.apache.seatunnel.web.spi.measurement.MeasurementRowWriter;
import org.apache.seatunnel.web.spi.measurement.ParsedMeasurementResult;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SimpleTextLineMeasurementFileParser implements MeasurementFileParser {

    private static final int DEFAULT_MAX_ROWS_IN_MEMORY = 1000;

    @Override
    public String parserType() {
        return MeasurementParserType.SIMPLE_TEXT.name();
    }

    @Override
    public ParsedMeasurementResult parse(MeasurementFileContext fileContext) {
        return parse(fileContext, null);
    }

    @Override
    public ParsedMeasurementResult parse(MeasurementFileContext context, MeasurementRowWriter rowWriter) {
        ParsedMeasurementResult result = new ParsedMeasurementResult();
        result.setSuccess(true);
        result.setRowCount(0L);
        result.setErrorRowCount(0);
        if (context == null || context.getInputStream() == null) {
            result.setSuccess(false);
            result.setErrorMessage("Input stream is required");
            return result;
        }

        int maxRowsInMemory = metadataInt(context, "maxRowsInMemory", DEFAULT_MAX_ROWS_IN_MEMORY);
        String parseTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getInputStream(),
                Charset.forName(StringUtils.defaultIfBlank(context.getCharset(), "UTF-8"))))) {
            String line;
            long rowNo = 0L;
            while ((line = reader.readLine()) != null) {
                rowNo++;
                Map<String, Object> row = standardRow(context, rowNo, line, parseTime);
                if (rowWriter != null) {
                    rowWriter.write(row);
                }
                if (maxRowsInMemory > 0 && result.getMeasurementRows().size() < maxRowsInMemory) {
                    result.getMeasurementRows().add(row);
                }
                result.setRowCount(result.getRowCount() + 1);
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Parse SIMPLE_TEXT failed: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> standardRow(
            MeasurementFileContext context,
            long rowNo,
            String rawLine,
            String parseTime
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("file_id", context.getFileId());
        row.put("task_id", context.getTaskId());
        row.put("batch_id", context.getBatchId());
        row.put("run_id", context.getRunId());
        row.put("source_file_name", context.getFileName());
        row.put("source_relative_path", context.getRelativePath());
        row.put("parser_type", parserType());
        row.put("row_no", rowNo);
        row.put("lot_id", null);
        row.put("wafer_id", null);
        row.put("item_name", null);
        row.put("item_value", null);
        row.put("item_unit", null);
        row.put("raw_line", rawLine);
        row.put("parse_time", parseTime);
        row.put("ingest_time", parseTime);
        return row;
    }

    private int metadataInt(MeasurementFileContext context, String key, int defaultValue) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.isNumeric((String) value)) {
            return Integer.parseInt((String) value);
        }
        return defaultValue;
    }
}
