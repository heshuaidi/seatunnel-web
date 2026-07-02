-- Measurement File Sync incremental upgrade script for existing deployments.
-- Fresh environments can import seatunnel_sync_control_mysql.sql directly.

DROP PROCEDURE IF EXISTS seatunnel_web_add_column_if_missing;

DELIMITER $$
CREATE PROCEDURE seatunnel_web_add_column_if_missing(
    IN p_table_name varchar(128),
    IN p_column_name varchar(128),
    IN p_column_definition text
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'parser_config_json',
    '`parser_config_json` mediumtext DEFAULT NULL COMMENT ''解析器参数 JSON'' AFTER `parser_type`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'parse_charset',
    '`parse_charset` varchar(50) NOT NULL DEFAULT ''UTF-8'' COMMENT ''解析字符集'' AFTER `parser_config_json`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'parse_max_error_rows',
    '`parse_max_error_rows` int NOT NULL DEFAULT 100 COMMENT ''最多保留解析错误行数'' AFTER `parse_charset`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'parse_fail_fast',
    '`parse_fail_fast` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''解析遇错是否快速失败'' AFTER `parse_max_error_rows`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'staging_dir',
    '`staging_dir` varchar(1000) DEFAULT NULL COMMENT ''staging 目录，需 SeaTunnel worker 可访问'' AFTER `schedule_cron`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'staging_format',
    '`staging_format` varchar(30) NOT NULL DEFAULT ''JSONL'' COMMENT ''staging 格式'' AFTER `staging_dir`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'staging_retention_days',
    '`staging_retention_days` int NOT NULL DEFAULT 7 COMMENT ''staging 文件保留天数'' AFTER `staging_format`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'keep_staging_file',
    '`keep_staging_file` tinyint(1) NOT NULL DEFAULT 1 COMMENT ''是否保留 staging 文件'' AFTER `staging_retention_days`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'target_datasource_id',
    '`target_datasource_id` bigint DEFAULT NULL COMMENT ''StarRocks 目标数据源ID'' AFTER `keep_staging_file`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'target_database',
    '`target_database` varchar(200) DEFAULT NULL COMMENT ''StarRocks 目标库'' AFTER `target_datasource_id`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'target_table',
    '`target_table` varchar(200) DEFAULT NULL COMMENT ''StarRocks 目标表'' AFTER `target_database`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'load_mode',
    '`load_mode` varchar(30) NOT NULL DEFAULT ''APPEND'' COMMENT ''装载模式：APPEND / UPSERT'' AFTER `target_table`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'load_batch_mode',
    '`load_batch_mode` varchar(30) NOT NULL DEFAULT ''ONE_FILE_ONE_JOB'' COMMENT ''装载批模式'' AFTER `load_mode`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'starrocks_node_urls',
    '`starrocks_node_urls` varchar(1000) DEFAULT NULL COMMENT ''StarRocks FE/HTTP nodeUrls 覆盖'' AFTER `load_batch_mode`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'starrocks_base_url',
    '`starrocks_base_url` varchar(1000) DEFAULT NULL COMMENT ''StarRocks JDBC base-url 覆盖'' AFTER `starrocks_node_urls`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'max_files_per_parse_run',
    '`max_files_per_parse_run` int NOT NULL DEFAULT 100 COMMENT ''单次最大解析文件数'' AFTER `starrocks_base_url`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'retry_parse_failed',
    '`retry_parse_failed` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否重试解析失败文件'' AFTER `max_files_per_parse_run`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'retry_load_failed',
    '`retry_load_failed` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否重试装载失败文件'' AFTER `retry_parse_failed`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'cleanup_before_reload',
    '`cleanup_before_reload` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''强制重跑前是否清理目标表数据，预留'' AFTER `retry_load_failed`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_task',
    'seatunnel_client_id',
    '`seatunnel_client_id` bigint DEFAULT NULL COMMENT ''SeaTunnel Client ID，空则使用默认客户端'' AFTER `cleanup_before_reload`'
);

CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'run_phase',
    '`run_phase` varchar(30) NOT NULL DEFAULT ''DISCOVER'' COMMENT ''运行阶段：DISCOVER / PARSE / LOAD / PARSE_LOAD'' AFTER `status`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'selected_file_count',
    '`selected_file_count` int NOT NULL DEFAULT 0 COMMENT ''选择处理文件数'' AFTER `failed_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'parsed_file_count',
    '`parsed_file_count` int NOT NULL DEFAULT 0 COMMENT ''解析成功文件数'' AFTER `selected_file_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'loaded_file_count',
    '`loaded_file_count` int NOT NULL DEFAULT 0 COMMENT ''装载成功文件数'' AFTER `parsed_file_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'parse_failed_count',
    '`parse_failed_count` int NOT NULL DEFAULT 0 COMMENT ''解析失败文件数'' AFTER `loaded_file_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'load_failed_count',
    '`load_failed_count` int NOT NULL DEFAULT 0 COMMENT ''装载失败文件数'' AFTER `parse_failed_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'parsed_row_count',
    '`parsed_row_count` bigint NOT NULL DEFAULT 0 COMMENT ''解析行数'' AFTER `load_failed_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'loaded_row_count',
    '`loaded_row_count` bigint NOT NULL DEFAULT 0 COMMENT ''装载行数'' AFTER `parsed_row_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'staging_dir',
    '`staging_dir` varchar(1000) DEFAULT NULL COMMENT ''本次 staging 目录'' AFTER `loaded_row_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'target_datasource_id',
    '`target_datasource_id` bigint DEFAULT NULL COMMENT ''目标数据源ID'' AFTER `staging_dir`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'target_database',
    '`target_database` varchar(200) DEFAULT NULL COMMENT ''目标库'' AFTER `target_datasource_id`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'target_table',
    '`target_table` varchar(200) DEFAULT NULL COMMENT ''目标表'' AFTER `target_database`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file_run',
    'generated_hocon',
    '`generated_hocon` mediumtext COMMENT ''脱敏后的 SeaTunnel HOCON'' AFTER `target_table`'
);

CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file',
    'parse_time',
    '`parse_time` datetime DEFAULT NULL COMMENT ''解析时间'' AFTER `discover_time`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file',
    'load_time',
    '`load_time` datetime DEFAULT NULL COMMENT ''入库时间'' AFTER `parse_time`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file',
    'staging_file_path',
    '`staging_file_path` varchar(2000) DEFAULT NULL COMMENT ''staging JSONL 文件路径'' AFTER `load_time`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file',
    'parsed_row_count',
    '`parsed_row_count` bigint DEFAULT NULL COMMENT ''解析行数'' AFTER `staging_file_path`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file',
    'loaded_row_count',
    '`loaded_row_count` bigint DEFAULT NULL COMMENT ''装载行数'' AFTER `parsed_row_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file',
    'parse_error_count',
    '`parse_error_count` int DEFAULT NULL COMMENT ''解析错误行数'' AFTER `loaded_row_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file',
    'parser_config_snapshot',
    '`parser_config_snapshot` mediumtext COMMENT ''解析配置快照'' AFTER `parse_error_count`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file',
    'load_job_id',
    '`load_job_id` varchar(100) DEFAULT NULL COMMENT ''SeaTunnel 装载 Job ID'' AFTER `parser_config_snapshot`'
);
CALL seatunnel_web_add_column_if_missing(
    't_seatunnel_web_measurement_file',
    'load_job_name',
    '`load_job_name` varchar(200) DEFAULT NULL COMMENT ''SeaTunnel 装载 Job 名称'' AFTER `load_job_id`'
);

DROP PROCEDURE IF EXISTS seatunnel_web_add_column_if_missing;
