package org.apache.seatunnel.web.api.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.web.api.service.MeasurementFileSchemaService;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementCheckItemVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementPreflightVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MeasurementFileSchemaServiceImpl implements MeasurementFileSchemaService {

    private static final String APPLY_SQL_MESSAGE =
            "Please apply seatunnel_sync_control_mysql.sql.";

    private static final Map<String, List<String>> REQUIRED_SCHEMA = Map.of(
            "t_seatunnel_web_measurement_file_task", List.of(
                    "id",
                    "task_name",
                    "task_code",
                    "parser_type",
                    "parser_config_json",
                    "parse_charset",
                    "staging_dir",
                    "target_datasource_id",
                    "target_database",
                    "target_table",
                    "load_mode",
                    "load_batch_mode",
                    "max_files_per_parse_run"),
            "t_seatunnel_web_measurement_file_run", List.of(
                    "id",
                    "run_id",
                    "task_id",
                    "run_phase",
                    "selected_file_count",
                    "parsed_file_count",
                    "loaded_file_count",
                    "parse_failed_count",
                    "load_failed_count",
                    "parsed_row_count",
                    "loaded_row_count",
                    "staging_dir",
                    "target_datasource_id",
                    "target_database",
                    "target_table",
                    "generated_hocon"),
            "t_seatunnel_web_measurement_file", List.of(
                    "id",
                    "task_id",
                    "file_status",
                    "staging_file_path",
                    "parsed_row_count",
                    "loaded_row_count",
                    "parse_error_count",
                    "parser_config_snapshot",
                    "load_job_id",
                    "load_job_name",
                    "error_message")
    );

    private final JdbcTemplate jdbcTemplate;

    public MeasurementFileSchemaServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void checkOrThrow() {
        MeasurementPreflightVO result = check();
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            String message = String.join("; ", result.getErrors());
            throw new ServiceException(message);
        }
    }

    @Override
    public MeasurementPreflightVO check() {
        MeasurementPreflightVO result = new MeasurementPreflightVO();
        for (Map.Entry<String, List<String>> entry : REQUIRED_SCHEMA.entrySet()) {
            String tableName = entry.getKey();
            if (!tableExists(tableName)) {
                add(result, "schema_" + tableName, "ERROR",
                        "Measurement File Sync tables are missing. " + APPLY_SQL_MESSAGE + " Missing table: " + tableName,
                        null);
                continue;
            }
            List<String> missingColumns = missingColumns(tableName, entry.getValue());
            if (!missingColumns.isEmpty()) {
                add(result, "schema_" + tableName, "ERROR",
                        "Measurement File Sync schema is incomplete. " + APPLY_SQL_MESSAGE
                                + " Missing columns in " + tableName + ": " + String.join(", ", missingColumns),
                        null);
            } else {
                add(result, "schema_" + tableName, "PASS", "Table and required columns exist: " + tableName, null);
            }
        }
        result.setSuccess(result.getErrors().isEmpty());
        return result;
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables where table_schema = database() and table_name = ?",
                    Integer.class,
                    tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Measurement schema table check failed, table={}", tableName, e);
            return false;
        }
    }

    private List<String> missingColumns(String tableName, List<String> requiredColumns) {
        List<String> missing = new ArrayList<>();
        for (String column : requiredColumns) {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.columns "
                            + "where table_schema = database() and table_name = ? and column_name = ?",
                    Integer.class,
                    tableName,
                    column);
            if (count == null || count == 0) {
                missing.add(column);
            }
        }
        return missing;
    }

    private void add(
            MeasurementPreflightVO result,
            String name,
            String status,
            String message,
            String suggestedDdl
    ) {
        MeasurementCheckItemVO item = new MeasurementCheckItemVO();
        item.setName(name);
        item.setStatus(status);
        item.setMessage(message);
        item.setSuggestedDdl(suggestedDdl);
        result.getChecks().add(item);
        if ("ERROR".equals(status)) {
            result.getErrors().add(message);
        } else if ("WARN".equals(status)) {
            result.getWarnings().add(message);
        }
    }
}
