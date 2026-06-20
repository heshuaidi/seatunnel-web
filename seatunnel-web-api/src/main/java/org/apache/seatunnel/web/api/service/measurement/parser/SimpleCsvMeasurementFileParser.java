package org.apache.seatunnel.web.api.service.measurement.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.spi.measurement.MeasurementFileContext;
import org.apache.seatunnel.web.spi.measurement.MeasurementFileParser;
import org.apache.seatunnel.web.spi.measurement.MeasurementRowWriter;
import org.apache.seatunnel.web.spi.measurement.ParsedMeasurementResult;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SimpleCsvMeasurementFileParser implements MeasurementFileParser {

    private static final int DEFAULT_MAX_ERROR_ROWS = 100;
    private static final int DEFAULT_MAX_ROWS_IN_MEMORY = 1000;

    @Override
    public String parserType() {
        return MeasurementParserType.SIMPLE_CSV.name();
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

        ParserConfig config = ParserConfig.of(context.getParserConfigJson());
        int maxErrorRows = positive(context.getParseMaxErrorRows(), DEFAULT_MAX_ERROR_ROWS);
        int maxRowsInMemory = metadataInt(context, "maxRowsInMemory", DEFAULT_MAX_ROWS_IN_MEMORY);
        boolean failFast = Boolean.TRUE.equals(context.getParseFailFast());
        String parseTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getInputStream(),
                Charset.forName(StringUtils.defaultIfBlank(context.getCharset(), "UTF-8"))))) {
            String line;
            long physicalLineNo = 0L;
            long rowNo = 0L;
            Map<String, Integer> headerIndex = new HashMap<>();

            if (config.header) {
                line = reader.readLine();
                physicalLineNo++;
                if (line == null) {
                    result.setSuccess(false);
                    result.setErrorMessage("CSV file is empty");
                    return result;
                }
                List<String> headers = parseCsvLine(line, config.delimiter);
                for (int i = 0; i < headers.size(); i++) {
                    headerIndex.put(headers.get(i).trim().toUpperCase(Locale.ROOT), i);
                }
                String missing = missingHeader(config.columns, headerIndex);
                if (missing != null) {
                    result.setSuccess(false);
                    result.setErrorMessage("CSV header missing required column: " + missing);
                    return result;
                }
            }

            while ((line = reader.readLine()) != null) {
                physicalLineNo++;
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                rowNo++;
                List<String> values = parseCsvLine(line, config.delimiter);
                try {
                    Map<String, Object> row = buildRow(context, config, headerIndex, values, rowNo, line, parseTime);
                    if (rowWriter != null) {
                        rowWriter.write(row);
                    }
                    if (maxRowsInMemory > 0 && result.getMeasurementRows().size() < maxRowsInMemory) {
                        result.getMeasurementRows().add(row);
                    }
                    result.setRowCount(result.getRowCount() + 1);
                } catch (Exception e) {
                    addErrorRow(result, physicalLineNo, line, e.getMessage(), maxErrorRows);
                    if (failFast) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Parse SIMPLE_CSV failed: " + rootMessage(e));
            return result;
        }

        result.setErrorRowCount(result.getErrorRows().size());
        if (!result.getErrorRows().isEmpty()) {
            result.setSuccess(false);
            result.setErrorMessage("CSV contains parse error rows: " + result.getErrorRows().size());
        }
        return result;
    }

    private Map<String, Object> buildRow(
            MeasurementFileContext context,
            ParserConfig config,
            Map<String, Integer> headerIndex,
            List<String> values,
            long rowNo,
            String rawLine,
            String parseTime
    ) {
        Map<String, Object> row = standardRow(context, rowNo, rawLine, parseTime);
        row.put("lot_id", getValue(config, headerIndex, values, "lot_id", 0));
        row.put("wafer_id", getValue(config, headerIndex, values, "wafer_id", 1));
        row.put("item_name", getValue(config, headerIndex, values, "item_name", 2));
        String itemValue = getValue(config, headerIndex, values, "item_value", 3);
        row.put("item_value", parseDouble(itemValue));
        row.put("item_unit", getValue(config, headerIndex, values, "item_unit", 4));
        return row;
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
        row.put("raw_line", rawLine);
        row.put("parse_time", parseTime);
        row.put("ingest_time", parseTime);
        return row;
    }

    private String getValue(
            ParserConfig config,
            Map<String, Integer> headerIndex,
            List<String> values,
            String field,
            int defaultIndex
    ) {
        String mapping = config.columns.get(field);
        int index = defaultIndex;
        if (StringUtils.isNotBlank(mapping)) {
            if (config.header) {
                Integer headerPos = headerIndex.get(mapping.trim().toUpperCase(Locale.ROOT));
                index = headerPos == null ? -1 : headerPos;
            } else {
                index = parseIndex(mapping, defaultIndex);
            }
        }
        return index >= 0 && index < values.size() ? StringUtils.trimToNull(values.get(index)) : null;
    }

    private Double parseDouble(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid item_value: " + value);
        }
    }

    private static List<String> parseCsvLine(String line, String delimiter) {
        char separator = StringUtils.defaultIfEmpty(delimiter, ",").charAt(0);
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (c == separator && !quoted) {
                values.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        values.add(current.toString());
        return values;
    }

    private String missingHeader(Map<String, String> columns, Map<String, Integer> headerIndex) {
        for (String column : columns.values()) {
            if (StringUtils.isNotBlank(column)
                    && !headerIndex.containsKey(column.trim().toUpperCase(Locale.ROOT))) {
                return column;
            }
        }
        return null;
    }

    private void addErrorRow(
            ParsedMeasurementResult result,
            long lineNo,
            String rawLine,
            String message,
            int maxErrorRows
    ) {
        if (result.getErrorRows().size() >= maxErrorRows) {
            return;
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("line_no", lineNo);
        error.put("raw_line", rawLine);
        error.put("error_message", message);
        result.getErrorRows().add(error);
    }

    private int parseIndex(String value, int defaultIndex) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultIndex;
        }
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

    private int positive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String rootMessage(Exception e) {
        Throwable cursor = e;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage();
    }

    private static final class ParserConfig {

        private boolean header = true;

        private String delimiter = ",";

        private Map<String, String> columns = new LinkedHashMap<>();

        static ParserConfig of(String json) {
            ParserConfig config = new ParserConfig();
            config.columns.put("lot_id", "LOT_ID");
            config.columns.put("wafer_id", "WAFER_ID");
            config.columns.put("item_name", "ITEM");
            config.columns.put("item_value", "VALUE");
            config.columns.put("item_unit", "UNIT");
            if (StringUtils.isBlank(json)) {
                return config;
            }
            Map<String, Object> map = JSONUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
            if (map == null) {
                return config;
            }
            Object headerValue = map.get("header");
            if (headerValue instanceof Boolean) {
                config.header = (Boolean) headerValue;
            }
            Object delimiterValue = map.get("delimiter");
            if (delimiterValue != null && StringUtils.isNotBlank(String.valueOf(delimiterValue))) {
                config.delimiter = String.valueOf(delimiterValue);
            }
            Object columnsValue = map.get("columns");
            if (columnsValue instanceof Map<?, ?>) {
                Map<?, ?> rawColumns = (Map<?, ?>) columnsValue;
                for (Map.Entry<?, ?> entry : rawColumns.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        config.columns.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
            }
            return config;
        }
    }
}
