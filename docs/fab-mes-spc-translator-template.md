# Fab MES/SPC JDBC Translator Template

## Background

Legacy Fab MES/SPC translator and loader flows often look like this:

```text
MES / SPC Oracle
  -> translator.exe
  -> proprietary parent-child text files
  -> loader.exe
  -> engineering data analysis Oracle database
```

The new first-step translator flow is:

```text
MES / SPC Oracle
  -> SeaTunnel JDBC incremental source
  -> Sql transform
  -> StarRocks xchg_meas_header / xchg_meas_site / xchg_meas_error
```

This template covers only the translator part. Publishing from xchg to dwd/ads remains a later loader/publish phase.

## Files

- StarRocks xchg DDL: `docs/sql/fab_mes_spc_xchg_starrocks.sql`
- Built-in HOCON template: `docs/templates/fab_mes_spc_jdbc_translator.conf`
- Lab Oracle to StarRocks example: `docs/templates/fab_mes_spc_jdbc_translator_lab_oracle_starrocks.conf`

## Xchg Tables

- `xchg_batch`: one row per translator batch.
- `xchg_meas_header`: normalized measurement header rows.
- `xchg_meas_site`: normalized site/die measurement rows.
- `xchg_meas_error`: validation errors written by the translator flow.

These are business target tables and intentionally do not use the `t_seatunnel_web_` metadata prefix.

## Template Variables

The HOCON template uses strict `${var}` replacement. Missing variables fail during preview/run.

Core variables:

- `batch_id`
- `run_id`
- `task_code`
- `source_system`
- `source_jdbc_url`
- `source_jdbc_driver`
- `source_username`
- `source_password`
- `header_source_sql`
- `site_source_sql`
- `batch_start_time`
- `batch_end_time`
- `batch_start_value`
- `batch_end_value`
- `starrocks_node_urls`
- `starrocks_base_url`
- `starrocks_username`
- `starrocks_password`
- `starrocks_database`

Passwords can remain as placeholders such as `${source_password}` and be supplied at preview/run time through params.

## Create Task

```http
POST /api/v1/sync/templates/fab-mes-spc-jdbc/create-task
```

Example:

```json
{
  "taskCode": "fab_mes_spc_measure",
  "taskName": "Fab MES/SPC Measure Translator",
  "clientId": 1,
  "description": "MES/SPC Oracle incremental translator to StarRocks xchg",
  "sourceSystem": "SPC",
  "watermarkField": "UPDATE_TIME",
  "watermarkFieldType": "DATETIME",
  "startValue": "2026-01-01 00:00:00",
  "lookbackSeconds": 300,
  "maxBatchSeconds": 3600,
  "sourceJdbcUrl": "${source_jdbc_url}",
  "sourceJdbcDriver": "oracle.jdbc.OracleDriver",
  "sourceUsername": "${source_username}",
  "sourcePassword": "${source_password}",
  "headerSourceSql": "SELECT ... WHERE UPDATE_TIME >= TO_TIMESTAMP('${batch_start_time}', 'YYYY-MM-DD HH24:MI:SS') AND UPDATE_TIME < TO_TIMESTAMP('${batch_end_time}', 'YYYY-MM-DD HH24:MI:SS')",
  "siteSourceSql": "SELECT ... WHERE UPDATE_TIME >= TO_TIMESTAMP('${batch_start_time}', 'YYYY-MM-DD HH24:MI:SS') AND UPDATE_TIME < TO_TIMESTAMP('${batch_end_time}', 'YYYY-MM-DD HH24:MI:SS')",
  "starrocksNodeUrls": "\"starrocks.lab:8030\"",
  "starrocksBaseUrl": "jdbc:mysql://starrocks.lab:9030/",
  "starrocksUsername": "${starrocks_username}",
  "starrocksPassword": "${starrocks_password}",
  "starrocksDatabase": "st_test",
  "sourceDatasourceId": 1,
  "sinkDatasourceId": 2,
  "enableDefaultChecks": true
}
```

The API creates:

- `sync_task`
- `sync_task_version`
- `sync_incremental_config`
- `sync_watermark`
- default `sync_check_config` rows when both datasource IDs are provided

The created task is `DRAFT`. The created version is `PUBLISHED` and assigned as `current_version_id`.

## Preview

```http
POST /api/v1/sync/tasks/fab_mes_spc_measure/preview-hocon
```

When credentials were left as placeholders during task creation, pass them in preview/run params:

```json
{
  "params": {
    "source_jdbc_url": "jdbc:oracle:thin:@//oracle.lab:1521/ORCLPDB1",
    "source_username": "mes_reader",
    "source_password": "******",
    "starrocks_username": "etl",
    "starrocks_password": "******"
  }
}
```

## Run

```http
POST /api/v1/sync/tasks/fab_mes_spc_measure/run
```

```json
{
  "triggerType": "MANUAL",
  "runMode": "NORMAL",
  "params": {
    "source_jdbc_url": "jdbc:oracle:thin:@//oracle.lab:1521/ORCLPDB1",
    "source_username": "mes_reader",
    "source_password": "******",
    "starrocks_username": "etl",
    "starrocks_password": "******"
  },
  "waitForFinish": true
}
```

`UPDATE_TIME_RANGE` uses `sync_watermark.current_value`, subtracts `lookbackSeconds`, and caps the window by `maxBatchSeconds` when configured. Watermark is advanced only after SeaTunnel succeeds and default checks pass.

## Backfill

```http
POST /api/v1/sync/tasks/fab_mes_spc_measure/backfill
```

```json
{
  "startTime": "2026-06-01 00:00:00",
  "endTime": "2026-06-02 00:00:00",
  "advanceWatermark": false,
  "waitForFinish": true
}
```

Backfill does not advance the main watermark unless explicitly allowed by both request and incremental config.

## Default Checks

When `enableDefaultChecks=true` and both datasource IDs are provided, the API creates:

- `source_count`: counts header SQL rows.
- `sink_header_count`: counts `xchg_meas_header` by `batch_id` and compares to `source_count`.
- `sink_site_count`: counts `xchg_meas_site` by `batch_id`.
- `error_count`: expects zero rows in `xchg_meas_error` by `batch_id`.

When either datasource ID is empty, no default checks are created and the response contains a warning.

## Lab Mapping

The lab HOCON maps:

- `ST_ORDER_HEADER` -> `xchg_meas_header`
- `ST_ORDER_ITEM` -> `xchg_meas_site`

Replace those SQL blocks with real MES/SPC SQL before production use.

## Current Limits

- No loader publish flow from xchg to dwd/ads.
- No WAT/CP file parser.
- No CP die/bin/site FlatMap plugin.
- Header/site SQL must be provided for the actual MES/SPC schema.
- Default checks are basic count checks only.
- Sql transform syntax may need minor adjustment if the target SeaTunnel runtime has stricter SQL dialect behavior around `CURRENT_TIMESTAMP`, `CAST(... AS STRING)`, or boolean literals.
