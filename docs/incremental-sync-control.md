# 通用增量 Batch 同步控制模块

## 目标

通用增量同步控制模块用于承载后续 Fab MES/EAP/SPC/WAT/CP translator/loader 改造中的批次运行元数据。模块统一管理任务、HOCON 模板版本、增量策略配置、watermark、batch、run、audit 以及文件类 source 的 manifest，为 JDBC/SQL/LocalFile/FtpFile 到 StarRocks/JDBC/LocalFile 的批同步提供一致的控制面。

## 为什么管理 batch、watermark 和 audit

- batch 记录每次增量运行的边界、模式、触发来源、统计数量和最终状态，后续补数、重跑和问题排查都依赖它。
- watermark 记录数据库类 source 的成功同步位置。后续运行成功后推进 watermark，失败不推进，避免重复推进导致数据缺口。
- audit 记录创建批次、读取 watermark、发现文件、渲染 HOCON、提交任务、轮询状态、推进 watermark 等关键事件，用于排障和审计。
- file manifest 记录 LocalFile/FtpFile 的文件发现和处理状态，后续支持基于 mtime、path+mtime+size、checksum 或 manifest 的文件增量。

## 表命名

本模块所有表统一使用 seatunnel-web 现有表名前缀：

```text
t_seatunnel_web_
```

第一阶段新增表：

- `t_seatunnel_web_sync_task`
- `t_seatunnel_web_sync_task_version`
- `t_seatunnel_web_sync_incremental_config`
- `t_seatunnel_web_sync_watermark`
- `t_seatunnel_web_sync_batch`
- `t_seatunnel_web_sync_run`
- `t_seatunnel_web_sync_audit`
- `t_seatunnel_web_sync_file_item`

MySQL 初始化脚本位于：

```text
seatunnel-web-api/src/main/resources/sql/seatunnel_sync_control_mysql.sql
```

## 第一阶段范围

第一阶段只提供元数据和后端骨架：

- 数据库表结构。
- DAO 实体、Mapper、Repository。
- Service 接口和基础实现。
- 同步任务、source/sink、增量策略、运行状态、批次状态、触发类型、审计事件、文件状态等基础枚举。

第一阶段不包含：

- 前端页面。
- SeaTunnel REST 提交。
- XXL-JOB 或调度接入。
- DAG 编排。
- 真实 watermark 推进流程。
- LocalFile/FtpFile 扫描流程。

## 第二阶段范围

第二阶段实现 JDBC/SQL 增量 Batch 任务的核心运行控制闭环第一版：

- HOCON 模板变量渲染。
- JDBC/SQL watermark 读取。
- `UPDATE_TIME_RANGE` 和 `ID_RANGE` 的 batch range 计算。
- 创建 batch 和 run。
- 保存 `generated_hocon`。
- 复用现有 `SeaTunnelRestClient` 提交 SeaTunnel Zeta REST API。
- 轮询 SeaTunnel job 状态。
- 写入 audit。
- 成功后推进 watermark，失败不推进。
- 提供后端 API 用于预览 HOCON、运行任务、补数运行、查询 run 和查询 watermark。

为了复用现有 SeaTunnel Client 表中的 Zeta baseUrl/auth，`t_seatunnel_web_sync_task` 增加 `client_id` 字段，用于关联 `t_seatunnel_web_client.id`。

第二阶段仍不包含：

- 前端页面。
- LocalFile/FtpFile manifest 扫描。
- XXL-JOB 调度入口。
- source/sink 行数审计 SQL 校验。
- 复杂 DAG。

## HOCON 模板变量

模板使用 `${xxx}` 形式引用变量。变量缺失时会抛出异常，不会静默替换为空。时间类型统一格式化为：

```text
yyyy-MM-dd HH:mm:ss
```

当前内置变量包括：

- `${task_code}`
- `${task_id}`
- `${task_name}`
- `${task_version_id}`
- `${run_id}`
- `${batch_id}`
- `${trigger_type}`
- `${run_mode}`
- `${last_watermark}`
- `${previous_watermark}`
- `${batch_start_value}`
- `${batch_end_value}`
- `${batch_start_time}`
- `${batch_end_time}`
- `${lookback_seconds}`
- `${biz_date}`
- `${watermark_key}`
- `${watermark_field}`

接口请求里的 `params` 会合并进模板变量，可用于传入 `${batchEndValue}`、`${tenant}` 等业务变量。

## UPDATE_TIME_RANGE 规则

`UPDATE_TIME_RANGE` 只支持 JDBC/SQL source：

1. 读取 `t_seatunnel_web_sync_incremental_config` 中的 `watermark_key`、`watermark_field`、`watermark_field_type`、`start_value`、`lookback_seconds` 和 `max_batch_seconds`。
2. 读取 `t_seatunnel_web_sync_watermark.current_value`。
3. `current_value` 为空时使用 `start_value`。
4. `batch_start_time = current watermark - lookback_seconds`。
5. `batch_end_time` 默认取当前时间。
6. 配置 `max_batch_seconds` 后，`batch_end_time` 不超过 `batch_start_time + max_batch_seconds`。
7. 成功后将 `watermark.current_value` 推进到 `batch_end_time`。
8. 失败不推进 watermark。

## ID_RANGE 规则

`ID_RANGE` 只支持 JDBC/SQL source：

1. `current_value` 表示已成功同步的最大 ID。
2. `current_value` 为空时使用 `start_value`，再为空时使用 `0`。
3. `batch_start_value = current_value`。
4. `batch_end_value` 从请求参数 `batchEndValue` 或 `batch_end_value` 读取。
5. 当前版本未传 `batchEndValue` 会抛出异常。
6. 成功后将 `watermark.current_value` 推进到 `batch_end_value`。
7. 失败不推进 watermark。

## 正常运行流程

1. 根据 `taskCode` 查询任务。
2. 校验任务状态为 `PUBLISHED`。
3. 读取 `current_version_id` 对应的 HOCON 模板版本。
4. 读取增量配置和 watermark。
5. 计算 batch range。
6. 创建 batch，状态从 `CREATED` 到 `READY`。
7. 创建 run。
8. 渲染 HOCON 并保存到 `t_seatunnel_web_sync_run.generated_hocon`。
9. 调用 SeaTunnel Zeta REST API 提交任务，保存 `seatunnel_job_id` 和 `seatunnel_job_name`。
10. 轮询 job 状态。
11. SeaTunnel job 成功后进入 `VERIFYING`。
12. 当前版本未配置审计 SQL，默认 SeaTunnel job 成功即校验通过。
13. 标记 batch/run 成功。
14. 推进 watermark。

## 补数流程

补数接口使用 `BACKFILL` trigger 和 `BACKFILL` run mode。

`UPDATE_TIME_RANGE` 补数必须传：

- `startTime`
- `endTime`

`ID_RANGE` 补数必须传：

- `startValue`
- `endValue`

补数默认不推进主 watermark。只有请求传 `advanceWatermark=true`，并且增量配置 `backfill_advance_watermark=1` 时，补数成功后才推进 watermark。

## 后端 API

路径按项目现有规范使用 `/api/v1` 前缀：

```http
POST /api/v1/sync/tasks/{taskCode}/preview-hocon
POST /api/v1/sync/tasks/{taskCode}/run
POST /api/v1/sync/tasks/{taskCode}/backfill
GET  /api/v1/sync/runs/{runId}
GET  /api/v1/sync/tasks/{taskCode}/watermark
```

运行请求示例：

```json
{
  "triggerType": "MANUAL",
  "runMode": "NORMAL",
  "params": {
    "batchEndValue": "1000"
  },
  "waitForFinish": true
}
```

补数请求示例：

```json
{
  "startTime": "2026-06-01 00:00:00",
  "endTime": "2026-06-02 00:00:00",
  "startValue": "0",
  "endValue": "1000",
  "advanceWatermark": false,
  "waitForFinish": true
}
```

HOCON 预览请求示例：

```json
{
  "params": {
    "batchEndValue": "1000"
  }
}
```

## 后续阶段建议

第三轮建议按以下方向扩展：

- `source_count_sql` / `sink_count_sql` 审计校验。
- LocalFile/FtpFile manifest 扫描和文件状态流转。
- 前端运行历史和 watermark 页面。
- XXL-JOB 触发入口。
- Fab MES/SPC translator 模板沉淀。
