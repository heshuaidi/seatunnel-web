# Scheduler Run API

## Why

External schedulers such as DolphinScheduler, XXL-JOB, and cron-http need a stable way to trigger existing seatunnel-web jobs. Calling the browser endpoint with a login Cookie works in a lab, but the Cookie expires and couples production scheduling to a human browser session.

The scheduler API uses a fixed header token and does not depend on the browser login state.

## Configuration

Default configuration:

```yaml
seatunnel:
  scheduler:
    enabled: true
    token: ${ST_SCHEDULER_TOKEN:}
    operator: ${ST_SCHEDULER_OPERATOR:scheduler}
```

If `seatunnel.scheduler.token` is empty, the scheduler API is disabled and returns a non-200 response. Production deployments must inject the token from an environment variable such as `ST_SCHEDULER_TOKEN`; do not use a browser Cookie or expose the endpoint without token authentication.

The lab profile includes this example:

```yaml
seatunnel:
  scheduler:
    enabled: true
    token: ${ST_SCHEDULER_TOKEN:seatunnel-lab-token}
    operator: ${ST_SCHEDULER_OPERATOR:scheduler}
```

## APIs

### DolphinScheduler Sync Control APIs

These APIs trigger the existing sync-control task by `taskCode`. They generate the
`batchId` inside seatunnel-web, render it into the HOCON context as `batch_id`,
submit the SeaTunnel Zeta job, and return immediately after submit.

```http
POST /api/v1/dolphinscheduler/sync/tasks/{taskCode}/run
GET  /api/v1/dolphinscheduler/sync/runs/{extractRunId}
```

Required token header, using the same `seatunnel.scheduler.token` setting as the
generic scheduler API:

```http
X-ST-SCHEDULER-TOKEN: <configured-token>
```

`Authorization: Bearer <configured-token>` is also accepted for Shell Task usage.

Run request:

```json
{
  "triggerType": "DOLPHINSCHEDULER",
  "runMode": "SCHEDULE",
  "version": "latest",
  "bizDate": "2026-07-02",
  "idempotencyKey": "ds-process-instance-10001",
  "params": {}
}
```

Run response:

```json
{
  "success": true,
  "data": {
    "extractRunId": "oracle_to_starrocks_inline_run_20260702010000_10086",
    "batchId": "oracle_to_starrocks_inline_20260702010000_000001",
    "taskCode": "oracle_to_starrocks_inline",
    "status": "SUBMITTED",
    "seatunnelJobId": "xxx"
  }
}
```

Status response:

```json
{
  "success": true,
  "data": {
    "extractRunId": "oracle_to_starrocks_inline_run_20260702010000_10086",
    "batchId": "oracle_to_starrocks_inline_20260702010000_000001",
    "taskCode": "oracle_to_starrocks_inline",
    "status": "SUCCESS",
    "seatunnelJobId": "xxx",
    "errorMessage": null,
    "startTime": "2026-07-02 01:00:00",
    "endTime": "2026-07-02 01:03:20"
  }
}
```

The status field is normalized to:

```text
SUBMITTED
RUNNING
SUCCESS
FAILED
CANCELED
```

Idempotency:

- `idempotencyKey` is stored in `t_seatunnel_web_sync_run.scheduler_run_id`.
- A duplicate `taskCode + idempotencyKey` returns the existing `extractRunId`
  and `batchId`.
- Duplicate requests do not create a new batch, do not submit SeaTunnel again,
  and do not advance watermark again.

Watermark behavior stays the same as sync-control:

- run creation reads the current watermark.
- HOCON is rendered from the selected range and includes `batch_id`.
- only successful runs advance watermark.
- failed, canceled, timed out, or duplicated idempotent requests do not advance watermark.

Curl:

```bash
curl -sS -X POST \
  "http://seatunnel-web.company.local/api/v1/dolphinscheduler/sync/tasks/oracle_to_starrocks_inline/run" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer xxx" \
  -d '{
    "triggerType": "DOLPHINSCHEDULER",
    "runMode": "SCHEDULE",
    "version": "latest",
    "bizDate": "2026-07-02",
    "idempotencyKey": "ds-process-instance-10001",
    "params": {}
  }'
```

```bash
curl -sS \
  "http://seatunnel-web.company.local/api/v1/dolphinscheduler/sync/runs/10086" \
  -H "Authorization: Bearer xxx"
```

Database upgrade for existing deployments:

```sql
ALTER TABLE `t_seatunnel_web_sync_run`
  ADD UNIQUE KEY `uk_sync_run_task_scheduler` (`task_id`, `scheduler_run_id`);
```

Fresh deployments can import `seatunnel_sync_control_mysql.sql`; existing
deployments can apply `seatunnel_sync_control_dolphinscheduler_upgrade_mysql.sql`.

### Generic Job Definition Scheduler APIs

Async run:

```http
POST /api/v1/scheduler/job-defines/{jobDefineId}/run
GET  /api/v1/scheduler/job-defines/{jobDefineId}/run
```

Sync run:

```http
POST /api/v1/scheduler/job-defines/{jobDefineId}/run-sync
```

Required header:

```http
X-ST-SCHEDULER-TOKEN: <configured-token>
```

Optional async body:

```json
{
  "triggeredBy": "DOLPHINSCHEDULER",
  "externalBatchId": "optional-batch-id",
  "remark": "optional remark"
}
```

Sync body:

```json
{
  "triggeredBy": "DOLPHINSCHEDULER",
  "timeoutSeconds": 600,
  "pollIntervalSeconds": 5
}
```

The async API reuses the existing batch job execution service and returns the existing unified response format:

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "jobDefineId": 22081022522784,
    "jobInstanceId": 22092428298144,
    "status": "SUBMITTED",
    "triggeredBy": "DOLPHINSCHEDULER",
    "externalBatchId": null,
    "operator": "scheduler",
    "remark": null,
    "message": "submitted"
  },
  "msg": null
}
```

`/run-sync` first triggers the same execution path, then polls the job instance status. It returns HTTP 200 only when the final status is `FINISHED`. It returns HTTP 500 for failed or canceled jobs and HTTP 504 for timeout. If the existing execution path does not return a `jobInstanceId`, `/run-sync` returns HTTP 501 instead of using unreliable status logic.

## DolphinScheduler HTTP Task

Async run:

```text
Method:
POST

URL:
http://seatunnel-web-backend:8080/api/v1/scheduler/job-defines/22081022522784/run

Headers:
Content-Type: application/json
X-ST-SCHEDULER-TOKEN: seatunnel-lab-token

Body:
{
  "triggeredBy": "DOLPHINSCHEDULER"
}
```

Sync run:

```text
Method:
POST

URL:
http://seatunnel-web-backend:8080/api/v1/scheduler/job-defines/22081022522784/run-sync

Headers:
Content-Type: application/json
X-ST-SCHEDULER-TOKEN: seatunnel-lab-token

Body:
{
  "triggeredBy": "DOLPHINSCHEDULER",
  "timeoutSeconds": 600,
  "pollIntervalSeconds": 5
}
```

DolphinScheduler success condition:

```text
HTTP Status Code == 200
```

## curl

Local or server:

```bash
curl -s -i -X POST \
  "http://127.0.0.1:18100/api/v1/scheduler/job-defines/22081022522784/run" \
  -H "Content-Type: application/json" \
  -H "X-ST-SCHEDULER-TOKEN: seatunnel-lab-token" \
  -d '{"triggeredBy":"DOLPHINSCHEDULER"}'
```

DolphinScheduler worker container:

```bash
curl -s -i -X POST \
  "http://seatunnel-web-backend:8080/api/v1/scheduler/job-defines/22081022522784/run" \
  -H "Content-Type: application/json" \
  -H "X-ST-SCHEDULER-TOKEN: seatunnel-lab-token" \
  -d '{"triggeredBy":"DOLPHINSCHEDULER"}'
```

Full worker verification:

```bash
DS_WORKER=$(docker ps --format '{{.Names}}' | grep dolphinscheduler-worker | head -1)

docker exec -it "$DS_WORKER" bash -lc '
curl -s -i -X POST \
  "http://seatunnel-web-backend:8080/api/v1/scheduler/job-defines/22081022522784/run" \
  -H "Content-Type: application/json" \
  -H "X-ST-SCHEDULER-TOKEN: seatunnel-lab-token" \
  -d "{\"triggeredBy\":\"DOLPHINSCHEDULER\"}" \
  | head -100
'
```

StarRocks result check:

```bash
docker exec -it st-starrocks \
  mysql -h 127.0.0.1 -P 9030 -u st_lab -pst_lab_pass st_test -e "
SELECT * FROM st_lab_user_sink ORDER BY ID;
"
```

## Security Notes

Do not call `/api/v1/executor/execute` from a scheduler with a browser Cookie. Do not log or expose `X-ST-SCHEDULER-TOKEN`. Restrict network access to the scheduler API where possible, and rotate the token through deployment configuration instead of committing production secrets.
