CREATE DATABASE IF NOT EXISTS seatunnel_web;

USE seatunnel_web;

-- =========================================
-- 通用增量同步任务主表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_task`
(
    `id`                      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_code`               varchar(100) NOT NULL COMMENT '任务编码',
    `task_name`               varchar(200) NOT NULL COMMENT '任务名称',
    `task_type`               varchar(30)  NOT NULL COMMENT '任务类型：BATCH / STREAM',
    `source_type`             varchar(30)  NOT NULL COMMENT '源类型：JDBC / SQL / LOCAL_FILE / FTP_FILE',
    `sink_type`               varchar(30)  NOT NULL COMMENT '目标类型：STARROCKS / JDBC / LOCAL_FILE',
    `engine_type`             varchar(30)  NOT NULL DEFAULT 'ZETA' COMMENT '执行引擎类型',
    `client_id`               bigint                DEFAULT NULL COMMENT 'SeaTunnel Client ID',
    `incremental_enabled`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否启用增量：0否 1是',
    `incremental_strategy`    varchar(50)           DEFAULT NULL COMMENT '增量策略',
    `status`                  varchar(30)  NOT NULL COMMENT '任务状态：DRAFT / PUBLISHED / OFFLINE',
    `current_version_id`      bigint                DEFAULT NULL COMMENT '当前发布版本ID',
    `description`             varchar(1000)         DEFAULT NULL COMMENT '描述',
    `create_user`             varchar(100)          DEFAULT NULL COMMENT '创建人',
    `create_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user`             varchar(100)          DEFAULT NULL COMMENT '更新人',
    `update_time`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sync_task_code` (`task_code`),
    KEY                       `idx_sync_task_client` (`client_id`),
    KEY                       `idx_sync_task_status` (`status`),
    KEY                       `idx_sync_task_source_sink` (`source_type`, `sink_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步任务主表';

-- =========================================
-- 通用增量同步任务版本表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_task_version`
(
    `id`                bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`           bigint      NOT NULL COMMENT '任务ID',
    `version_no`        int         NOT NULL COMMENT '版本号',
    `hocon_template`    mediumtext  NOT NULL COMMENT 'HOCON 模板',
    `hocon_hash`        varchar(100)         DEFAULT NULL COMMENT 'HOCON 内容哈希',
    `param_schema_json` mediumtext COMMENT '模板参数 schema JSON',
    `publish_status`   varchar(30) NOT NULL COMMENT '发布状态：DRAFT / PUBLISHED / ARCHIVED',
    `create_user`      varchar(100)         DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sync_task_version` (`task_id`, `version_no`),
    KEY                 `idx_sync_task_version_status` (`publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步任务版本表';

-- =========================================
-- 通用增量同步配置表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_incremental_config`
(
    `id`                         bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`                    bigint       NOT NULL COMMENT '任务ID',
    `batch_link_up_task_id`       bigint                DEFAULT NULL COMMENT 'batch-link-up 任务定义 ID',
    `enabled`                    tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用批任务增量控制',
    `range_type`                 varchar(50)           DEFAULT 'ID_RANGE' COMMENT '边界类型：ID_RANGE / UPDATE_TIME_RANGE / CUSTOM',
    `boundary_mode`              varchar(50)           DEFAULT 'SEPARATE_SQL' COMMENT '边界获取模式：SEPARATE_SQL / PREPARE_SQL / SIMPLE_WATERMARK',
    `start_value_source`         varchar(30)           DEFAULT 'WATERMARK' COMMENT 'batch_start_value 来源',
    `end_value_source`           varchar(30)           DEFAULT 'SQL' COMMENT 'batch_end_value 来源',
    `start_time_source`          varchar(30)           DEFAULT 'NONE' COMMENT 'batch_start_time 来源',
    `end_time_source`            varchar(30)           DEFAULT 'NONE' COMMENT 'batch_end_time 来源',
    `boundary_datasource_id`     bigint                DEFAULT NULL COMMENT '边界 SQL 执行数据源 ID',
    `batch_prepare_sql`          mediumtext COMMENT '一次性获取边界上下文的 SQL',
    `batch_start_value_sql`      mediumtext COMMENT '单独获取 batch_start_value 的 SQL',
    `batch_end_value_sql`        mediumtext COMMENT '单独获取 batch_end_value 的 SQL',
    `batch_start_time_sql`       mediumtext COMMENT '单独获取 batch_start_time 的 SQL',
    `batch_end_time_sql`         mediumtext COMMENT '单独获取 batch_end_time 的 SQL',
    `fixed_start_value`          varchar(500)          DEFAULT NULL COMMENT '固定 batch_start_value',
    `fixed_end_value`            varchar(500)          DEFAULT NULL COMMENT '固定 batch_end_value',
    `fixed_start_time`           varchar(100)          DEFAULT NULL COMMENT '固定 batch_start_time',
    `fixed_end_time`             varchar(100)          DEFAULT NULL COMMENT '固定 batch_end_time',
    `default_params_json`        mediumtext COMMENT '默认运行参数 JSON',
    `custom_context_json`        mediumtext COMMENT '自定义上下文 JSON，会展开为 custom.xxx',
    `success_update_watermark`   tinyint(1)   NOT NULL DEFAULT 1 COMMENT '成功后是否推进 watermark',
    `check_enabled`              tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否启用后置校验',
    `check_datasource_id`        bigint                DEFAULT NULL COMMENT '默认后置校验数据源 ID',
    `check_sql`                  mediumtext COMMENT '默认后置校验 SQL',
    `cleanup_sql`                mediumtext COMMENT '失败批次清理 SQL 模板',
    `cleanup_datasource_id`      bigint                DEFAULT NULL COMMENT '清理 SQL 执行数据源 ID',
    `cleanup_on_rerun`           tinyint(1)   NOT NULL DEFAULT 0 COMMENT '失败批次重跑前是否执行 cleanup_sql',
    `cleanup_before_retry_only`  tinyint(1)   NOT NULL DEFAULT 1 COMMENT 'cleanup_sql 是否仅用于失败批次重跑',
    `source_type`                varchar(30)  NOT NULL COMMENT '源类型：JDBC / SQL / LOCAL_FILE / FTP_FILE',
    `strategy`                   varchar(50)  NOT NULL COMMENT '增量策略',
    `watermark_key`              varchar(100) NOT NULL DEFAULT 'default' COMMENT 'watermark key',
    `watermark_field`            varchar(100)          DEFAULT NULL COMMENT 'watermark 字段',
    `watermark_field_type`       varchar(30)           DEFAULT NULL COMMENT 'watermark 字段类型：DATETIME / LONG / STRING / JSON',
    `start_value`                varchar(500)          DEFAULT NULL COMMENT '初始值',
    `lookback_seconds`           int          NOT NULL DEFAULT 0 COMMENT '回看秒数',
    `max_batch_seconds`          int                   DEFAULT NULL COMMENT '单批最大时间跨度秒数',
    `max_batch_rows`             bigint                DEFAULT NULL COMMENT '单批最大行数',
    `file_path`                  varchar(1000)         DEFAULT NULL COMMENT '文件目录',
    `file_pattern`               varchar(500)          DEFAULT NULL COMMENT '文件匹配模式',
    `file_recursive`             tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否递归扫描文件',
    `file_timezone`              varchar(100)          DEFAULT NULL COMMENT '文件时间时区',
    `file_cursor_mode`           varchar(50)           DEFAULT NULL COMMENT '文件游标模式：MTIME / PATH_MTIME_SIZE / CHECKSUM / MANIFEST',
    `backfill_advance_watermark` tinyint(1)   NOT NULL DEFAULT 0 COMMENT '补数是否推进 watermark',
    `create_time`                datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY                          `idx_sync_inc_config_task` (`task_id`),
    KEY                          `idx_sync_inc_config_batch_link_up` (`batch_link_up_task_id`),
    KEY                          `idx_sync_inc_config_strategy` (`strategy`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步配置表';

-- =========================================
-- 通用增量同步 watermark 表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_watermark`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`               bigint       NOT NULL COMMENT '任务ID',
    `watermark_key`         varchar(100) NOT NULL COMMENT 'watermark key',
    `current_value`         varchar(500)          DEFAULT NULL COMMENT '当前 watermark 值',
    `previous_value`        varchar(500)          DEFAULT NULL COMMENT '上一个 watermark 值',
    `current_value_type`    varchar(30)           DEFAULT NULL COMMENT 'watermark 值类型：DATETIME / LONG / JSON',
    `last_success_run_id`   bigint                DEFAULT NULL COMMENT '最后成功运行记录ID',
    `last_success_batch_id` varchar(100)          DEFAULT NULL COMMENT '最后成功批次ID',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sync_task_wm` (`task_id`, `watermark_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步 watermark 表';

-- =========================================
-- 通用增量同步 task 分布式锁表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_task_lock`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`       bigint       NOT NULL COMMENT '任务ID',
    `watermark_key` varchar(128) NOT NULL DEFAULT 'default' COMMENT 'watermark key',
    `lock_token`    varchar(128) NOT NULL COMMENT '锁 token',
    `lock_owner`    varchar(128)          DEFAULT NULL COMMENT '锁 owner',
    `run_id`        varchar(128)          DEFAULT NULL COMMENT '运行ID',
    `batch_id`      varchar(128)          DEFAULT NULL COMMENT '批次ID',
    `locked_at`     datetime     NOT NULL COMMENT '加锁时间',
    `expires_at`    datetime     NOT NULL COMMENT '过期时间',
    `status`        varchar(32)           DEFAULT NULL COMMENT '锁状态',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_watermark` (`task_id`, `watermark_key`),
    KEY             `idx_sync_task_lock_expire` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步任务锁表';

-- =========================================
-- 通用增量同步 batch 表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_batch`
(
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `batch_id`          varchar(100) NOT NULL COMMENT '批次ID',
    `task_id`           bigint       NOT NULL COMMENT '任务ID',
    `task_code`         varchar(100) NOT NULL COMMENT '任务编码',
    `trigger_type`      varchar(30)  NOT NULL COMMENT '触发类型：MANUAL / SCHEDULE / BACKFILL / RETRY',
    `run_mode`          varchar(30)  NOT NULL COMMENT '运行模式：NORMAL / BACKFILL / RERUN',
    `batch_start_value` varchar(500)          DEFAULT NULL COMMENT '批次开始值',
    `batch_end_value`   varchar(500)          DEFAULT NULL COMMENT '批次结束值',
    `batch_start_time`  datetime              DEFAULT NULL COMMENT '批次开始时间',
    `batch_end_time`    datetime              DEFAULT NULL COMMENT '批次结束时间',
    `status`            varchar(30)  NOT NULL COMMENT '批次状态',
    `expected_count`    bigint                DEFAULT NULL COMMENT '预期数量',
    `source_count`      bigint                DEFAULT NULL COMMENT '源端数量',
    `sink_count`        bigint                DEFAULT NULL COMMENT '目标端数量',
    `error_count`       bigint                DEFAULT NULL COMMENT '错误数量',
    `error_message`     mediumtext COMMENT '错误信息',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sync_batch_id` (`batch_id`),
    KEY                 `idx_sync_batch_task` (`task_id`),
    KEY                 `idx_sync_batch_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步批次表';

-- =========================================
-- 通用增量同步 run 表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_run`
(
    `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `run_id`             varchar(100) NOT NULL COMMENT '运行ID',
    `task_id`            bigint       NOT NULL COMMENT '任务ID',
    `task_version_id`    bigint       NOT NULL COMMENT '任务版本ID',
    `batch_id`           varchar(100)          DEFAULT NULL COMMENT '批次ID',
    `trigger_type`       varchar(30)  NOT NULL COMMENT '触发类型',
    `scheduler_run_id`   varchar(100)          DEFAULT NULL COMMENT '调度侧运行ID',
    `run_param_json`     mediumtext COMMENT '运行参数 JSON',
    `generated_hocon`    mediumtext COMMENT '渲染后的 HOCON',
    `seatunnel_job_id`   varchar(100)          DEFAULT NULL COMMENT 'SeaTunnel Job ID',
    `seatunnel_job_name` varchar(200)          DEFAULT NULL COMMENT 'SeaTunnel Job 名称',
    `status`             varchar(30)  NOT NULL COMMENT '运行状态',
    `error_message`      mediumtext COMMENT '错误信息',
    `submit_time`        datetime              DEFAULT NULL COMMENT '提交时间',
    `start_time`         datetime              DEFAULT NULL COMMENT '开始时间',
    `end_time`           datetime              DEFAULT NULL COMMENT '结束时间',
    `source_count`       bigint                DEFAULT NULL COMMENT '源端数量',
    `sink_count`         bigint                DEFAULT NULL COMMENT '目标端数量',
    `error_count`        bigint                DEFAULT NULL COMMENT '错误数量',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sync_run_id` (`run_id`),
    KEY                  `idx_sync_run_task` (`task_id`),
    KEY                  `idx_sync_run_batch` (`batch_id`),
    KEY                  `idx_sync_run_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步运行记录表';

-- =========================================
-- 通用增量同步 audit 表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_audit`
(
    `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `run_id`        varchar(100)         DEFAULT NULL COMMENT '运行ID',
    `batch_id`      varchar(100)         DEFAULT NULL COMMENT '批次ID',
    `task_id`       bigint               DEFAULT NULL COMMENT '任务ID',
    `task_code`     varchar(100)         DEFAULT NULL COMMENT '任务编码',
    `event_type`    varchar(50) NOT NULL COMMENT '事件类型',
    `event_level`   varchar(20) NOT NULL COMMENT '事件级别：INFO / WARN / ERROR',
    `event_message` varchar(1000)        DEFAULT NULL COMMENT '事件消息',
    `detail_json`   mediumtext COMMENT '事件详情 JSON',
    `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY             `idx_sync_audit_task` (`task_id`),
    KEY             `idx_sync_audit_run` (`run_id`),
    KEY             `idx_sync_audit_batch` (`batch_id`),
    KEY             `idx_sync_audit_event` (`event_type`, `event_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步审计表';

-- =========================================
-- 通用增量同步后置校验配置表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_check_config`
(
    `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`               bigint       NOT NULL COMMENT '任务ID',
    `check_code`            varchar(100) NOT NULL COMMENT '校验编码',
    `check_name`            varchar(200)          DEFAULT NULL COMMENT '校验名称',
    `check_type`            varchar(50)  NOT NULL COMMENT '校验类型：SOURCE_COUNT / SINK_COUNT / ERROR_COUNT / CUSTOM_COUNT / CUSTOM_BOOLEAN',
    `datasource_type`       varchar(30)           DEFAULT NULL COMMENT '数据源类型：SOURCE / SINK / CUSTOM',
    `datasource_id`         bigint                DEFAULT NULL COMMENT '数据源ID',
    `sql_text`              mediumtext   NOT NULL COMMENT '校验 SQL 模板',
    `expected_operator`     varchar(20)           DEFAULT NULL COMMENT '期望比较符：EQ / NE / GT / GE / LT / LE / IS_NULL / IS_NOT_NULL',
    `expected_value`        varchar(200)          DEFAULT NULL COMMENT '期望值',
    `compare_to_check_code` varchar(100)          DEFAULT NULL COMMENT '用于比较的校验编码',
    `fail_on_mismatch`      tinyint(1)   NOT NULL DEFAULT 1 COMMENT '不匹配时是否阻断',
    `enabled`               tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `sort_order`            int                   DEFAULT 0 COMMENT '排序',
    `description`           varchar(1000)         DEFAULT NULL COMMENT '描述',
    `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sync_check_code` (`task_id`, `check_code`),
    KEY                     `idx_sync_check_task_enabled` (`task_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步后置校验配置表';

-- =========================================
-- 通用增量同步后置校验结果表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_check_result`
(
    `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `run_id`                 varchar(100) NOT NULL COMMENT '运行ID',
    `batch_id`               varchar(100)          DEFAULT NULL COMMENT '批次ID',
    `task_id`                bigint       NOT NULL COMMENT '任务ID',
    `task_code`              varchar(100)          DEFAULT NULL COMMENT '任务编码',
    `check_code`             varchar(100) NOT NULL COMMENT '校验编码',
    `check_name`             varchar(200)          DEFAULT NULL COMMENT '校验名称',
    `check_type`             varchar(50)  NOT NULL COMMENT '校验类型',
    `rendered_sql`           mediumtext COMMENT '渲染后的 SQL',
    `actual_value`           varchar(200)          DEFAULT NULL COMMENT '实际值',
    `expected_operator`      varchar(20)           DEFAULT NULL COMMENT '期望比较符',
    `expected_value`         varchar(200)          DEFAULT NULL COMMENT '期望值',
    `compare_to_check_code`  varchar(100)          DEFAULT NULL COMMENT '用于比较的校验编码',
    `compare_to_actual_value` varchar(200)         DEFAULT NULL COMMENT '用于比较的实际值',
    `passed`                 tinyint(1)   NOT NULL COMMENT '是否通过',
    `fail_on_mismatch`       tinyint(1)   NOT NULL DEFAULT 1 COMMENT '不匹配时是否阻断',
    `error_message`          mediumtext COMMENT '错误信息',
    `start_time`             datetime              DEFAULT NULL COMMENT '开始时间',
    `end_time`               datetime              DEFAULT NULL COMMENT '结束时间',
    `create_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY                      `idx_sync_check_result_run` (`run_id`),
    KEY                      `idx_sync_check_result_batch` (`batch_id`),
    KEY                      `idx_sync_check_result_task` (`task_id`),
    KEY                      `idx_sync_check_result_passed` (`passed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步后置校验结果表';

-- =========================================
-- 通用增量同步文件清单表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_sync_file_item`
(
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`            bigint        NOT NULL COMMENT '任务ID',
    `batch_id`           varchar(100)           DEFAULT NULL COMMENT '批次ID',
    `run_id`             varchar(100)           DEFAULT NULL COMMENT '运行ID',
    `source_type`        varchar(30)   NOT NULL COMMENT '源类型：LOCAL_FILE / FTP_FILE',
    `file_system`        varchar(30)            DEFAULT NULL COMMENT '文件系统：LOCAL / FTP / SFTP / NAS',
    `file_path`          varchar(1000) NOT NULL COMMENT '文件路径',
    `file_name`          varchar(500)           DEFAULT NULL COMMENT '文件名',
    `relative_path`      varchar(1000)          DEFAULT NULL COMMENT '相对路径',
    `file_size`          bigint                 DEFAULT NULL COMMENT '文件大小',
    `last_modified_time` datetime               DEFAULT NULL COMMENT '最后修改时间',
    `checksum`           varchar(200)           DEFAULT NULL COMMENT '校验和',
    `discovered_time`    datetime      NOT NULL COMMENT '发现时间',
    `status`             varchar(30)   NOT NULL COMMENT '文件状态',
    `error_message`      mediumtext COMMENT '错误信息',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY                  `idx_sync_file_task_status` (`task_id`, `status`),
    KEY                  `idx_sync_file_batch` (`batch_id`),
    KEY                  `idx_sync_file` (`task_id`, `file_path`(255), `file_size`, `last_modified_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用增量同步文件清单表';

-- =========================================
-- 量测文件同步任务表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_measurement_file_task`
(
    `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_name`              varchar(200) NOT NULL COMMENT '任务名称',
    `task_code`              varchar(100) NOT NULL COMMENT '任务编码',
    `parser_type`            varchar(30)  NOT NULL COMMENT '解析器类型：WAT / CP / CUSTOM',
    `source_datasource_id`   bigint       NOT NULL COMMENT '文件源数据源ID',
    `source_root_path`       varchar(1000)         DEFAULT NULL COMMENT '任务级源根路径，空则使用数据源 rootPath',
    `include_patterns`       varchar(1000)         DEFAULT NULL COMMENT '包含文件 glob，逗号或换行分隔',
    `exclude_patterns`       varchar(1000)         DEFAULT NULL COMMENT '排除文件 glob，逗号或换行分隔',
    `recursive`              tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否递归扫描',
    `max_depth`              int                   DEFAULT NULL COMMENT '递归最大深度',
    `min_last_modified_time` datetime              DEFAULT NULL COMMENT '最小文件最后修改时间',
    `file_stable_seconds`    int          NOT NULL DEFAULT 0 COMMENT '文件稳定秒数',
    `enabled`                tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `discovery_mode`         varchar(30)  NOT NULL DEFAULT 'FULL_SCAN' COMMENT '发现模式',
    `watermark_key`          varchar(100) NOT NULL DEFAULT 'default' COMMENT 'watermark key',
    `current_watermark`      varchar(500)          DEFAULT NULL COMMENT '当前 watermark',
    `dedup_strategy`         varchar(30)  NOT NULL DEFAULT 'PATH_SIZE_MTIME' COMMENT '去重策略',
    `checksum_enabled`       tinyint(1)   NOT NULL DEFAULT 0 COMMENT '是否启用 checksum',
    `max_files_per_run`      int          NOT NULL DEFAULT 1000 COMMENT '单次最大处理文件数',
    `lock_ttl_minutes`       int          NOT NULL DEFAULT 60 COMMENT '锁 TTL 分钟',
    `schedule_cron`          varchar(100)          DEFAULT NULL COMMENT '调度 cron',
    `description`            varchar(1000)         DEFAULT NULL COMMENT '描述',
    `create_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_measurement_file_task_code` (`task_code`),
    KEY                      `idx_measurement_file_task_ds` (`source_datasource_id`),
    KEY                      `idx_measurement_file_task_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量测文件同步任务表';

-- =========================================
-- 量测文件同步 Run History 表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_measurement_file_run`
(
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `run_id`               varchar(100) NOT NULL COMMENT '运行ID',
    `batch_id`             varchar(100)          DEFAULT NULL COMMENT '批次ID',
    `task_id`              bigint       NOT NULL COMMENT '任务ID',
    `trigger_type`         varchar(30)  NOT NULL COMMENT '触发类型：MANUAL / SCHEDULED',
    `status`               varchar(30)  NOT NULL COMMENT '运行状态：SUCCESS / FAILED / SKIPPED / RUNNING',
    `source_datasource_id` bigint       NOT NULL COMMENT '源数据源ID',
    `scanned_count`        int          NOT NULL DEFAULT 0 COMMENT '扫描文件数',
    `discovered_count`     int          NOT NULL DEFAULT 0 COMMENT '新发现文件数',
    `skipped_count`        int          NOT NULL DEFAULT 0 COMMENT '跳过文件数',
    `failed_count`         int          NOT NULL DEFAULT 0 COMMENT '失败文件数',
    `error_message`        mediumtext COMMENT '错误信息',
    `start_time`           datetime     NOT NULL COMMENT '开始时间',
    `end_time`             datetime              DEFAULT NULL COMMENT '结束时间',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_measurement_file_run_id` (`run_id`),
    KEY                    `idx_measurement_file_run_task` (`task_id`),
    KEY                    `idx_measurement_file_run_batch` (`batch_id`),
    KEY                    `idx_measurement_file_run_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量测文件同步运行历史表';

-- =========================================
-- 量测文件清单表
-- =========================================
CREATE TABLE IF NOT EXISTS `t_seatunnel_web_measurement_file`
(
    `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`              bigint        NOT NULL COMMENT '任务ID',
    `batch_id`             varchar(100)           DEFAULT NULL COMMENT '批次ID',
    `run_id`               varchar(100)           DEFAULT NULL COMMENT '运行ID',
    `source_datasource_id` bigint        NOT NULL COMMENT '源数据源ID',
    `source_type`          varchar(30)   NOT NULL COMMENT '源类型：LOCAL_FILE / NAS / FTP / SFTP',
    `parser_type`          varchar(30)   NOT NULL COMMENT '解析器类型',
    `root_path`            varchar(1000)          DEFAULT NULL COMMENT '扫描根路径',
    `relative_path`        varchar(1000)          DEFAULT NULL COMMENT '相对路径',
    `file_name`            varchar(500)           DEFAULT NULL COMMENT '文件名',
    `full_path`            varchar(2000) NOT NULL COMMENT '完整路径',
    `file_size`            bigint                 DEFAULT NULL COMMENT '文件大小',
    `last_modified_time`   datetime               DEFAULT NULL COMMENT '最后修改时间',
    `checksum`             varchar(128)           DEFAULT NULL COMMENT '校验和',
    `checksum_type`        varchar(30)            DEFAULT NULL COMMENT '校验和类型',
    `file_status`          varchar(30)   NOT NULL COMMENT '文件状态',
    `discover_time`        datetime      NOT NULL COMMENT '发现时间',
    `parse_time`           datetime               DEFAULT NULL COMMENT '解析时间',
    `load_time`            datetime               DEFAULT NULL COMMENT '入库时间',
    `error_message`        mediumtext COMMENT '错误信息',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY                    `idx_task_status` (`task_id`, `file_status`),
    KEY                    `idx_task_mtime` (`task_id`, `last_modified_time`),
    KEY                    `idx_task_batch` (`task_id`, `batch_id`),
    UNIQUE KEY             `uk_task_file_identity` (`task_id`, `full_path`(512), `file_size`, `last_modified_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='量测文件清单表';
