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
