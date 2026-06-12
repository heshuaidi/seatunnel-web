# Generic JDBC/SQL To StarRocks Incremental Test Template

## Goal

This template is for testing the generic incremental batch control loop:

```text
JDBC / SQL source
  -> batch range / watermark
  -> HOCON render
  -> SeaTunnel Zeta submit
  -> SQL Transform
  -> StarRocks sink
  -> check verification
  -> success advance watermark
  -> failure keep watermark
  -> backfill / rerun
```

Template code:

```text
GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL
```

## Scope

Supported:

- JDBC source connection
- SQL source query
- StarRocks sink
- `UPDATE_TIME_RANGE`
- `ID_RANGE`
- default count checks
- preview, run, watermark query, check result query, backfill

Not covered in this template:

- FtpFile
- LocalFile
- WAT/CP file parser
- custom source connector changes
- frontend pages

## Files

- HOCON template: `docs/templates/generic_jdbc_sql_to_starrocks_incremental.conf`
- Runtime template: `seatunnel-web-api/src/main/resources/sync/templates/generic_jdbc_sql_to_starrocks_incremental.conf`
- StarRocks lab SQL: `docs/sql/generic_jdbc_sql_incremental_starrocks_lab.sql`

## Prepare Lab Tables

Run the SQL in StarRocks:

```text
docs/sql/generic_jdbc_sql_incremental_starrocks_lab.sql
```

It creates:

- `lab_src_order`
- `lab_sink_order`
- `lab_sink_order_error`

It also inserts two test batches:

- ids `1,2,3` around `2026-06-01 00:01:00`
- ids `4,5` around `2026-06-01 01:01:00`

## Create UPDATE_TIME_RANGE Task

```http
POST /api/v1/sync/templates/generic-jdbc-starrocks/create-task
```

```json
{
  "taskCode": "lab_order_update_time_sync",
  "taskName": "Lab Order Update Time Sync",
  "clientId": 1,
  "description": "Test UPDATE_TIME_RANGE incremental sync from JDBC to StarRocks",
  "incrementalStrategy": "UPDATE_TIME_RANGE",
  "watermarkField": "update_time",
  "watermarkFieldType": "DATETIME",
  "startValue": "2026-06-01 00:00:00",
  "lookbackSeconds": 0,
  "maxBatchSeconds": 3600,
  "sourceJdbcUrl": "jdbc:mysql://starrocks.lab:9030/st_test",
  "sourceJdbcDriver": "com.mysql.cj.jdbc.Driver",
  "sourceUsername": "${source_username}",
  "sourcePassword": "${source_password}",
  "sourceQuery": "SELECT id, biz_no, amount, update_time FROM lab_src_order WHERE update_time >= '${batch_start_time}' AND update_time < '${batch_end_time}'",
  "starrocksNodeUrls": "\"starrocks.lab:8030\"",
  "starrocksBaseUrl": "jdbc:mysql://starrocks.lab:9030/",
  "starrocksUsername": "${starrocks_username}",
  "starrocksPassword": "${starrocks_password}",
  "starrocksDatabase": "st_test",
  "starrocksTable": "lab_sink_order",
  "starrocksErrorTable": "lab_sink_order_error",
  "sourceDatasourceId": 1,
  "sinkDatasourceId": 1,
  "enableDefaultChecks": true
}
```

The task creates:

- `sync_task`
- `sync_task_version`
- `sync_incremental_config`
- `sync_watermark`
- default `sync_check_config` when datasource ids are present

## Preview HOCON

```http
POST /api/v1/sync/tasks/lab_order_update_time_sync/preview-hocon
```

```json
{
  "params": {
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  }
}
```

Confirm that the rendered query contains the expected `batch_start_time` and `batch_end_time`.

## Run UPDATE_TIME_RANGE

```http
POST /api/v1/sync/tasks/lab_order_update_time_sync/run
```

```json
{
  "triggerType": "MANUAL",
  "runMode": "NORMAL",
  "waitForFinish": true,
  "params": {
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  }
}
```

After success, query:

```http
GET /api/v1/sync/runs/{runId}
GET /api/v1/sync/tasks/lab_order_update_time_sync/watermark
GET /api/v1/sync/runs/{runId}/checks
```

Expected behavior:

- SeaTunnel job success.
- verification passed.
- `lab_sink_order` contains rows for the generated `batch_id`.
- watermark advances to the rendered `batch_end_time`.

## Insert More Data And Run Again

The lab SQL already includes ids `4,5`. If you ran the inserts separately, run the second insert block, then call the same run API again. With `maxBatchSeconds = 3600`, each run advances by at most one hour from the current watermark.

## Backfill UPDATE_TIME_RANGE

```http
POST /api/v1/sync/tasks/lab_order_update_time_sync/backfill
```

```json
{
  "startTime": "2026-06-01 00:00:00",
  "endTime": "2026-06-01 02:00:00",
  "advanceWatermark": false,
  "waitForFinish": true,
  "params": {
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  }
}
```

With `advanceWatermark = false`, a successful backfill does not move the current watermark.

## Create ID_RANGE Task

```http
POST /api/v1/sync/templates/generic-jdbc-starrocks/create-task
```

```json
{
  "taskCode": "lab_order_id_range_sync",
  "taskName": "Lab Order ID Range Sync",
  "clientId": 1,
  "description": "Test ID_RANGE incremental sync from JDBC to StarRocks",
  "incrementalStrategy": "ID_RANGE",
  "watermarkField": "id",
  "watermarkFieldType": "LONG",
  "startValue": "0",
  "lookbackSeconds": 0,
  "maxBatchSeconds": null,
  "sourceJdbcUrl": "jdbc:mysql://starrocks.lab:9030/st_test",
  "sourceJdbcDriver": "com.mysql.cj.jdbc.Driver",
  "sourceUsername": "${source_username}",
  "sourcePassword": "${source_password}",
  "sourceQuery": "SELECT id, biz_no, amount, update_time FROM lab_src_order WHERE id > ${batch_start_value} AND id <= ${batch_end_value}",
  "starrocksNodeUrls": "\"starrocks.lab:8030\"",
  "starrocksBaseUrl": "jdbc:mysql://starrocks.lab:9030/",
  "starrocksUsername": "${starrocks_username}",
  "starrocksPassword": "${starrocks_password}",
  "starrocksDatabase": "st_test",
  "starrocksTable": "lab_sink_order",
  "starrocksErrorTable": "lab_sink_order_error",
  "sourceDatasourceId": 1,
  "sinkDatasourceId": 1,
  "enableDefaultChecks": true
}
```

## Run ID_RANGE

ID range requires `batchEndValue` in run params.

```http
POST /api/v1/sync/tasks/lab_order_id_range_sync/run
```

```json
{
  "triggerType": "MANUAL",
  "runMode": "NORMAL",
  "waitForFinish": true,
  "params": {
    "batchEndValue": "3",
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  }
}
```

Expected behavior:

- `batch_start_value` is current watermark, initially `0`.
- `batch_end_value` is `3`.
- source query uses `id > 0 AND id <= 3`.
- success advances watermark to `3`.

Run again with `batchEndValue = 5` to copy ids `4,5`.

## Verify Failure Keeps Watermark

Use one of these failure drills:

- Temporarily set `starrocksTable` in a task version to a non-existing table.
- Temporarily stop or block the StarRocks sink endpoint.
- Configure a check mismatch, for example point `sink_count` at an empty table.

Then run the task and query:

```http
GET /api/v1/sync/tasks/{taskCode}/watermark
```

The current watermark must remain unchanged because watermark advancement happens only after SeaTunnel success and verification passed.

## Common Issues

- `sourceDatasourceId` and `sinkDatasourceId` are required for default checks. If either is missing, create-task skips default checks and returns a warning.
- Missing HOCON variables fail fast during preview/run. Pass credential placeholders through run params or save real lab credentials in the created task template.
- `ID_RANGE` requires `batchEndValue`; otherwise range calculation fails before job submission.
- `starrocksNodeUrls` must render as a HOCON array value, for example `"\"starrocks.lab:8030\""`.

## Real Test Troubleshooting SOP

This SOP is for JDBC / SQL / Oracle / StarRocks incremental batch source testing. It intentionally does not cover FtpFile, LocalFile, WAT, or CP file translator flows.

## Frontend Test Console

After starting `seatunnel-web-ui`, open the left menu:

```text
增量同步 -> 增量同步测试台
```

Route:

```text
/sync-control/test-console
```

The page is a single test console for JDBC / SQL / Oracle / StarRocks incremental Batch tasks. It contains these tabs:

- `模板创建`
- `任务诊断`
- `HOCON / Range`
- `运行任务`
- `Runs`
- `Batches`
- `Watermark`
- `Checks`
- `Audits`

Recommended frontend test flow:

1. Enter or create a `taskCode` in the top task bar.
2. In `模板创建`, click `填充 UPDATE_TIME_RANGE 示例`, adjust `clientId` and datasource ids, then click `创建任务`.
3. For ID range testing, click `填充 ID_RANGE 示例`, adjust values, then click `创建任务`.
4. In `任务诊断`, pass credential params and click `诊断`.
5. In `HOCON / Range`, run `预览 Range` and `诊断 HOCON`.
6. In `HOCON / Range`, use `预览 HOCON` if you need the older full HOCON preview.
7. In `运行任务`, run normal sync or backfill. For ID range, pass `batchEndValue` in params JSON.
8. In `Runs`, query run history, open run detail, jump to run audits/checks, or fill rerun.
9. In `Batches`, query batch ranges and batch audits.
10. In `Watermark`, query current watermark and manually reset it when lab testing needs a controlled baseline.
11. In `Checks`, list or edit check config, diagnose check SQL, and query check results by `runId`.
12. In `Audits`, query run audits or batch audits and expand `detailJson`.
13. For failure recovery, select a failed run from `Runs`, click `rerun`, then execute `RERUN_SAME_RANGE` in `运行任务`.

The frontend validates every params JSON input before sending requests. Passwords can be entered in params JSON, but the page does not intentionally log or echo request params. Diagnosis and audit responses rely on backend masking.

### 1. Create Lab Tables

Run:

```text
docs/sql/generic_jdbc_sql_incremental_starrocks_lab.sql
```

Confirm the source and sink tables exist:

- `lab_src_order`
- `lab_sink_order`
- `lab_sink_order_error`

### 2. Create UPDATE_TIME_RANGE Task

Use the `Create UPDATE_TIME_RANGE Task` request above. Keep credentials as variables in the task template:

```text
${source_username}
${source_password}
${starrocks_username}
${starrocks_password}
```

### 3. Diagnose Task

```http
POST /api/v1/sync/tasks/lab_order_update_time_sync/diagnose
```

```json
{
  "params": {
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  },
  "includeHoconPreview": true,
  "includeCheckPreview": true,
  "includeDatasourceCheck": false
}
```

Use this first when a task cannot run. It verifies task metadata, current version, incremental config, watermark, range preview, HOCON rendering, check SQL rendering, and client visibility without creating a batch/run or submitting SeaTunnel.

### 4. Preview Range

```http
POST /api/v1/sync/tasks/lab_order_update_time_sync/preview-range
```

```json
{
  "runMode": "NORMAL",
  "params": {}
}
```

Confirm:

- `startTime` equals the current watermark minus `lookbackSeconds`.
- `endTime` respects `maxBatchSeconds`.
- `willAdvanceWatermark = true` for normal incremental runs.

### 5. Diagnose HOCON

```http
POST /api/v1/sync/tasks/lab_order_update_time_sync/diagnose-hocon
```

```json
{
  "params": {
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  },
  "includeRenderedHocon": false
}
```

Check:

- `renderable`
- `missingVariables`
- `variablesUsed`
- `renderedHash`
- `renderedHoconPreview`

Passwords, tokens, and secrets are masked in the returned params and HOCON preview.

### 6. Preview HOCON

The older preview endpoint is still useful for checking the final rendered HOCON shape:

```http
POST /api/v1/sync/tasks/lab_order_update_time_sync/preview-hocon
```

```json
{
  "params": {
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  }
}
```

Prefer `diagnose-hocon` when you need all missing variables returned at once.

### 7. Run

```http
POST /api/v1/sync/tasks/lab_order_update_time_sync/run
```

```json
{
  "triggerType": "MANUAL",
  "runMode": "NORMAL",
  "waitForFinish": true,
  "params": {
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  }
}
```

### 8. Query Runs

```http
GET /api/v1/sync/tasks/lab_order_update_time_sync/runs?pageNo=1&pageSize=20
```

Optional filters:

- `status`
- `startTime`
- `endTime`

Use this to find `runId`, `batchId`, SeaTunnel job id, counts, and the final error message.

### 9. Query Batches

```http
GET /api/v1/sync/tasks/lab_order_update_time_sync/batches?pageNo=1&pageSize=20
```

```http
GET /api/v1/sync/batches/{batchId}
```

Use this to confirm the calculated batch range, status, metrics, and failure reason.

### 10. Query Audits

```http
GET /api/v1/sync/runs/{runId}/audits
```

```http
GET /api/v1/sync/batches/{batchId}/audits
```

Audit detail JSON is masked before return. Use audits to locate failures around range calculation, HOCON render, SeaTunnel submit, polling, verification, or watermark advancement.

### 11. Query Checks

```http
GET /api/v1/sync/runs/{runId}/checks
```

For pre-run check SQL diagnostics:

```http
POST /api/v1/sync/tasks/lab_order_update_time_sync/diagnose-checks
```

```json
{
  "params": {
    "batch_id": "test_batch_001",
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  },
  "executeSql": false
}
```

Set `executeSql = true` only when you want to validate datasource connectivity and SQL execution.

### 12. Query Watermark

```http
GET /api/v1/sync/tasks/lab_order_update_time_sync/watermark
```

Watermark advances only after SeaTunnel success and verification passed.

### 13. Insert Second Batch And Run Again

Insert the second lab data block or use the data already inserted by the lab SQL. Run the same task again and verify:

- new run uses the previous `currentValue` as range start.
- `maxBatchSeconds` caps the next end time.
- watermark advances only after the second run succeeds.

### 14. Manually Reset Watermark

```http
PUT /api/v1/sync/tasks/lab_order_update_time_sync/watermark
```

```json
{
  "watermarkKey": "default",
  "currentValue": "2026-06-01 00:00:00",
  "reason": "reset for lab test"
}
```

Behavior:

- old `current_value` is moved to `previous_value`.
- new `current_value` is saved.
- audit event `MANUAL_UPDATE_WATERMARK` is written with WARN level.
- `reason` is required.

### 15. Create ID_RANGE Task

Use the `Create ID_RANGE Task` request above.

### 16. Run ID_RANGE With batchEndValue

Always pass `batchEndValue` for normal ID range runs:

```http
POST /api/v1/sync/tasks/lab_order_id_range_sync/preview-range
```

```json
{
  "runMode": "NORMAL",
  "params": {
    "batchEndValue": "100"
  }
}
```

Then run:

```http
POST /api/v1/sync/tasks/lab_order_id_range_sync/run
```

```json
{
  "triggerType": "MANUAL",
  "runMode": "NORMAL",
  "waitForFinish": true,
  "params": {
    "batchEndValue": "100",
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  }
}
```

If `batchEndValue` is missing, `preview-range` and `diagnose` return:

```text
ID_RANGE requires batchEndValue in run params
```

### 17. Intentionally Create A Failure

Use one failure at a time:

- Change `starrocks_table` or `starrocksTable` to a non-existing table.
- Omit `starrocks_password` from run params.
- Configure a check SQL that points at a wrong table.

### 18. Verify Watermark Did Not Advance

After the failed run:

```http
GET /api/v1/sync/tasks/{taskCode}/watermark
GET /api/v1/sync/tasks/{taskCode}/runs?pageNo=1&pageSize=5
GET /api/v1/sync/runs/{runId}/audits
```

Confirm:

- run or batch status is `FAILED`.
- `currentValue` is unchanged.
- there is no successful `ADVANCE_WATERMARK` audit for the failed run.

### 19. Fix And Rerun Same Range

Fix the task version, credentials, or check SQL. Then rerun the failed range:

```http
POST /api/v1/sync/runs/{runId}/rerun
```

```json
{
  "mode": "RERUN_SAME_RANGE",
  "waitForFinish": true,
  "params": {
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  }
}
```

The first rerun version supports JDBC / SQL incremental tasks and reuses the original range by creating a new system batch/run. Watermark advances only after the rerun succeeds and verification passes.

### 20. Common Error Location Table

| Symptom | Use | What To Check |
| --- | --- | --- |
| HOCON missing variables | `POST /diagnose-hocon` | `missingVariables`, `variablesUsed`, masked params |
| Missing `batchEndValue` | `POST /preview-range` | ID_RANGE requires `batchEndValue` in run params |
| Check SQL render failed | `POST /diagnose-checks` | per-check `missingVariables` and `renderedSqlPreview` |
| Check SQL execution failed | `POST /diagnose-checks` with `executeSql=true` | per-check `errorMessage`, datasource id, SQL permissions |
| Zeta submit failed | `GET /sync/runs/{runId}/audits` | `SUBMIT_JOB` audit detail |
| SeaTunnel job failed | `GET /sync/tasks/{taskCode}/runs` | run `errorMessage` and `seatunnelJobId` |
| Watermark did not advance | watermark + run/batch/audit queries | run/batch status and `ADVANCE_WATERMARK` audit |
