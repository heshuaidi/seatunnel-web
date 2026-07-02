-- DolphinScheduler Run API idempotency upgrade for existing deployments.
-- Fresh environments can import seatunnel_sync_control_mysql.sql directly.

DROP PROCEDURE IF EXISTS seatunnel_web_add_index_if_missing;

DELIMITER $$
CREATE PROCEDURE seatunnel_web_add_index_if_missing(
    IN p_table_name varchar(128),
    IN p_index_name varchar(128),
    IN p_index_definition text
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND index_name = p_index_name
    ) THEN
        SET @ddl = p_index_definition;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL seatunnel_web_add_index_if_missing(
    't_seatunnel_web_sync_run',
    'uk_sync_run_task_scheduler',
    'ALTER TABLE `t_seatunnel_web_sync_run` ADD UNIQUE KEY `uk_sync_run_task_scheduler` (`task_id`, `scheduler_run_id`)'
);

DROP PROCEDURE IF EXISTS seatunnel_web_add_index_if_missing;
