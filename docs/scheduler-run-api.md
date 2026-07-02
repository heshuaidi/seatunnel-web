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
