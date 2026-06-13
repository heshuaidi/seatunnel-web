# Batch Link Up 增量控制使用说明

## 定位

正式增量同步入口是 `/sync/batch-link-up`。用户仍然在 batch-link-up 任务中维护完整 SeaTunnel HOCON，包括 source、transform、sink、Sql 等复杂逻辑。

增量控制只负责运行控制：

- 读取 batch-link-up 任务的原始 HOCON。
- 每次运行生成 `batch_id` 和 `run_id`。
- 通过 watermark、SQL、运行参数、固定值或当前时间计算边界变量。
- 替换 HOCON 中的占位符，保存本次 `rendered_hocon` 快照。
- 提交 SeaTunnel Zeta job 并轮询状态。
- 写入 batch、run、audit。
- job 成功后执行可选 check SQL。
- check 通过后推进 watermark。
- job 失败或 check 失败时不推进 watermark。

`/sync-control/test-console` 只作为开发诊断页面保留，内置模板 API 只用于 demo/dev-only 测试，不是正式主流程。

## 页面使用

1. 进入 `/sync/batch-link-up` 创建或编辑批量同步任务。
2. 在原 HOCON 编辑框中编写完整 SeaTunnel HOCON。
3. 打开任务编辑页的“增量控制”区域。
4. 配置是否启用增量、range 类型、边界值来源、边界 SQL、watermark 和可选 check SQL。
5. 使用“预览 context”查看本次变量最终值和 SQL 执行结果。
6. 使用“预览 HOCON”查看替换前和替换后的 HOCON。
7. 确认后执行“手动增量运行”。
8. 在历史页查看 batches、runs、watermark。

未启用增量控制的 batch-link-up 任务仍按原普通批量同步能力运行。

## 支持变量

HOCON 和边界 SQL 支持以下变量：

- `${batch_id}`
- `${run_id}`
- `${task_code}`
- `${batch_start_value}`
- `${batch_end_value}`
- `${batch_start_time}`
- `${batch_end_time}`
- `${watermark_value}`
- `${watermark_time}`
- `${last_success_batch_id}`
- `${last_success_run_id}`
- `${biz_date}`
- `${custom.xxx}`

前端“占位符”区域提供常用变量的一键插入。

## 边界值来源

每个边界变量可以独立配置来源：

- `WATERMARK`：从上次成功 watermark 获取。
- `SQL`：执行用户配置的查询 SQL。
- `PARAM`：由运行时参数传入。
- `FIXED`：使用配置里的固定值。
- `NOW`：使用当前时间。
- `NONE`：不使用该变量。

常用模式：

- `PREPARE_SQL`：使用 `batch_prepare_sql` 一次返回多个边界字段。
- `SEPARATE_SQL`：分别配置 `batch_start_value_sql`、`batch_end_value_sql`、`batch_start_time_sql`、`batch_end_time_sql`。
- `SIMPLE_WATERMARK`：起点优先来自 watermark，终点可来自 SQL、参数、固定值或当前时间。

当同时配置 `batch_prepare_sql` 和单独 SQL 时，`batch_prepare_sql` 返回的字段优先。

## batch_prepare_sql

`batch_prepare_sql` 要求返回一行，可以返回以下固定别名：

- `batch_start_value`
- `batch_end_value`
- `batch_start_time`
- `batch_end_time`
- `biz_date`
- `custom_json`

示例：

```sql
select
  coalesce('${watermark_value}', '0') as batch_start_value,
  cast(max(id) as char) as batch_end_value,
  null as batch_start_time,
  null as batch_end_time,
  current_date() as biz_date,
  null as custom_json
from lab_src_order
```

`custom_json` 可以返回 JSON 对象字符串，系统会展开为 `${custom.xxx}`。

## 单独边界 SQL

单独 SQL 每条必须返回一行一列。

示例：

```sql
select coalesce(max_synced_id, 0)
from sync_watermark_ext
where task_code = '${task_code}'
```

```sql
select max(id)
from lab_src_order
```

SQL 执行数据源选择顺序：

1. `boundary_datasource_id`
2. batch-link-up 任务关联的 source datasource
3. batch-link-up 任务关联的 sink datasource

如果仍无法确定数据源，预览和运行接口会返回诊断信息，不会盲目执行。

## HOCON 示例

用户在 `/sync/batch-link-up` 中直接维护完整 HOCON：

```hocon
env {
  job.mode = "BATCH"
  parallelism = 1
}

source {
  Jdbc {
    plugin_output = "src"
    url = "jdbc:mysql://starrocks.lab:9030/st_test"
    driver = "com.mysql.cj.jdbc.Driver"
    username = "st_lab"
    password = "st_lab_pass"
    query = """
      SELECT id, biz_no, amount, update_time
      FROM lab_src_order
      WHERE id > ${batch_start_value}
        AND id <= ${batch_end_value}
    """
  }
}

transform {
  Sql {
    plugin_input = "src"
    plugin_output = "valid_rows"
    query = """
      SELECT
        '${batch_id}' AS batch_id,
        id,
        biz_no,
        amount,
        update_time,
        '${run_id}' AS run_id,
        CURRENT_TIMESTAMP AS ingest_time
      FROM src
    """
  }
}

sink {
  StarRocks {
    plugin_input = "valid_rows"
    nodeUrls = ["starrocks.lab:8030"]
    base-url = "jdbc:mysql://starrocks.lab:9030/"
    username = "st_lab"
    password = "st_lab_pass"
    database = "st_test"
    table = "lab_sink_order"
    batch_max_rows = 5000
    starrocks.config = {
      format = "JSON"
      strip_outer_array = true
    }
  }
}
```

系统不会通过模板重新生成这段 HOCON，只在运行前替换变量。

## StarRocks lab 示例

目标：`lab_src_order -> lab_sink_order`，按 ID 范围增量同步。

增量控制建议配置：

- `enabled = true`
- `range_type = ID_RANGE`
- `boundary_mode = PREPARE_SQL`
- `start_value_source = WATERMARK`
- `end_value_source = SQL`
- `success_update_watermark = true`

`batch_prepare_sql`：

```sql
select
  coalesce('${watermark_value}', '0') as batch_start_value,
  cast(max(id) as char) as batch_end_value,
  null as batch_start_time,
  null as batch_end_time,
  current_date() as biz_date,
  null as custom_json
from lab_src_order
```

可选 check SQL：

```sql
select count(1)
from lab_sink_order
where batch_id = '${batch_id}'
```

运行成功且 check 通过后，`default` watermark 推进到 `${batch_end_value}`。SeaTunnel job 失败、HOCON 缺变量、边界 SQL 失败或 check 失败时，batch/run 标记失败，watermark 不推进。

## SQL 安全限制

边界 SQL 和 check SQL 当前只允许查询类 SQL：

- SQL 必须以 `select` 或 `with` 开头。
- 基础拦截 `insert`、`update`、`delete`、`drop`、`truncate`、`alter`、`create`、`replace`、`merge`、`call`、`grant`、`revoke` 等危险关键字。
- 边界 SQL 最多返回一行。
- 单独边界 SQL 必须只返回一列。
- `batch_prepare_sql` 可以返回多列，但只读取约定字段。

这不是完整 SQL parser。复杂 SQL 需要在预览和测试 SQL 中先确认结果。

## 正式 API

正式 API 围绕 batch-link-up 任务展开：

```text
GET  /api/v1/batch-link-up/tasks/{taskId}/incremental-config
PUT  /api/v1/batch-link-up/tasks/{taskId}/incremental-config
POST /api/v1/batch-link-up/tasks/{taskId}/preview-incremental-context
POST /api/v1/batch-link-up/tasks/{taskId}/preview-incremental-hocon
POST /api/v1/batch-link-up/tasks/{taskId}/run-incremental
GET  /api/v1/batch-link-up/tasks/{taskId}/incremental-runs
GET  /api/v1/batch-link-up/tasks/{taskId}/incremental-batches
GET  /api/v1/batch-link-up/tasks/{taskId}/watermark
PUT  /api/v1/batch-link-up/tasks/{taskId}/watermark
POST /api/v1/batch-link-up/tasks/{taskId}/test-incremental-sql
```

`preview-incremental-context` 返回本次 `batch_id`、`run_id`、边界值、watermark、biz_date、custom context、执行过的 SQL、SQL 结果、缺失变量和诊断信息。

`preview-incremental-hocon` 返回 `original_hocon`、`rendered_hocon`、variables、missing_variables 和 warnings。

## 数据库变更

初始化脚本：

```text
seatunnel-web-api/src/main/resources/sql/seatunnel_sync_control_mysql.sql
```

已为 `t_seatunnel_web_sync_incremental_config` 增加 batch-link-up 关联和边界 SQL 配置字段。已有环境需要按脚本中的新增字段执行相应 `ALTER TABLE`，因为 `CREATE TABLE IF NOT EXISTS` 不会自动补齐旧表字段。
