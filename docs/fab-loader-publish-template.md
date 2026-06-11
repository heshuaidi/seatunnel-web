# Fab Loader Publish Template

## Background

The old Fab data path used a dedicated loader executable after the translator step:

```text
MES/SPC Oracle
  -> translator.exe
  -> proprietary parent/child intermediate files
  -> loader.exe
  -> engineering analytics Oracle master tables
```

The new control-plane path splits the migration into two business templates:

```text
MES/SPC Oracle
  -> Fab MES/SPC JDBC translator template
  -> StarRocks xchg tables
  -> Fab loader publish template
  -> StarRocks eda_stg tables
```

This template implements only the `xchg -> eda_stg` step through SeaTunnel. The later
`eda_stg -> dwd/ads` publish step is provided as StarRocks SQL in
`docs/sql/fab_loader_publish_dwd_ads.sql`.

## Template Code

```text
FAB_LOADER_PUBLISH_XCHG_TO_STG
```

The create-task API creates a non-incremental sync task. The task does not own a
watermark; the caller passes the business translator batch id with `params.xchg_batch_id`
when running the task.

## Files

- DDL: `docs/sql/fab_loader_publish_starrocks.sql`
- SeaTunnel HOCON template: `docs/templates/fab_loader_publish_xchg_to_stg.conf`
- Runtime classpath template: `seatunnel-web-api/src/main/resources/sync/templates/fab_loader_publish_xchg_to_stg.conf`
- DWD/ADS SQL template: `docs/sql/fab_loader_publish_dwd_ads.sql`

## Layers

The template reads:

- `xchg_meas_header`
- `xchg_meas_site`
- `xchg_meas_error`

The template writes:

- `eda_stg_measure_header`
- `eda_stg_measure_site`
- `eda_stg_measure_error`

The DWD/ADS SQL template then publishes:

- `eda_stg_measure_header -> dwd_measure_header`
- `eda_stg_measure_site + header dimensions -> dwd_measure_site`
- `dwd_measure_site -> ads_measure_summary_daily`

## Batch Ids

There are two batch ids in this template:

- `batch_id`: seatunnel-web system batch id for this loader run.
- `xchg_batch_id`: business translator batch id used to filter xchg/stg records.

The HOCON source SQL filters by `xchg_batch_id`:

```sql
WHERE batch_id = '${xchg_batch_id}'
```

The generated system `batch_id` remains available for audit and run tracking, but it
is not used to filter xchg rows.

## Create Task

```http
POST /api/v1/sync/templates/fab-loader-publish/create-task
```

```json
{
  "taskCode": "fab_loader_publish_measure",
  "taskName": "Fab Loader Publish Measure",
  "clientId": 1,
  "description": "Publish xchg measure tables to eda_stg tables",
  "sourceTaskCode": "fab_mes_spc_measure",
  "sourceSystem": "SPC",
  "batchIdMode": "MANUAL_PARAM",
  "starrocksJdbcUrl": "${starrocks_jdbc_url}",
  "starrocksJdbcDriver": "com.mysql.cj.jdbc.Driver",
  "starrocksNodeUrls": "\"starrocks.lab:8030\"",
  "starrocksBaseUrl": "jdbc:mysql://starrocks.lab:9030/",
  "starrocksUsername": "${starrocks_username}",
  "starrocksPassword": "${starrocks_password}",
  "starrocksDatabase": "st_test",
  "xchgHeaderTable": "xchg_meas_header",
  "xchgSiteTable": "xchg_meas_site",
  "xchgErrorTable": "xchg_meas_error",
  "stgHeaderTable": "eda_stg_measure_header",
  "stgSiteTable": "eda_stg_measure_site",
  "stgErrorTable": "eda_stg_measure_error",
  "starrocksDatasourceId": 2,
  "enableDefaultChecks": true
}
```

The created task has:

- `task_type = BATCH`
- `source_type = SQL`
- `sink_type = STARROCKS`
- `engine_type = ZETA`
- `incremental_enabled = 0`
- no incremental config
- no watermark

Only `MANUAL_PARAM` batch id mode is implemented in this version.

## Run Task

```http
POST /api/v1/sync/tasks/fab_loader_publish_measure/run
```

```json
{
  "triggerType": "MANUAL",
  "runMode": "NORMAL",
  "waitForFinish": true,
  "params": {
    "xchg_batch_id": "fab_mes_spc_measure_20260612153022_482913",
    "starrocks_jdbc_url": "jdbc:mysql://starrocks.lab:9030/st_test",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  }
}
```

If credentials were saved as template placeholders, the run request must provide them.
Missing variables are rejected by strict HOCON rendering before job submission.

## Checks

When `enableDefaultChecks = true` and `starrocksDatasourceId` is provided, the service
creates six checks:

- `xchg_header_count >= 0`
- `stg_header_count == xchg_header_count`
- `xchg_site_count >= 0`
- `stg_site_count == xchg_site_count`
- `xchg_error_count >= 0`
- `stg_error_count == xchg_error_count`

The loader does not require error count to be zero. Translator error records are valid
business rows that must be copied from xchg to eda_stg.

## Backfill And Rerun

To rerun a loader publish, call the run API again with the same `xchg_batch_id`.
The target eda_stg tables use primary keys that include the business batch id, so rerun
behavior depends on StarRocks primary-key update semantics and table definitions.

## Limits

- This version does not execute DWD/ADS publish SQL.
- This version does not introduce a SQL workflow engine.
- This version does not implement `LATEST_SUCCESS_TRANSLATOR_BATCH`; callers must pass `xchg_batch_id`.
- WAT/CP file parsing remains a separate translator template.
