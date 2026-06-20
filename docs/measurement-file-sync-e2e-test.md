# Measurement File Sync End-to-End Test

This guide validates the SIMPLE_CSV path:

LOCAL_FILE/NAS -> file discovery -> manifest -> parser -> JSONL staging -> SeaTunnel LocalFile source -> StarRocks sink.

## 1. Apply DB Migration

For a fresh database, import the main control SQL:

```bash
mysql -h 127.0.0.1 -P 3306 -u seatunnel -p seatunnel_web \
  < seatunnel-web-api/src/main/resources/sql/seatunnel_sync_control_mysql.sql
```

For an environment that already imported an earlier Measurement File Sync DDL, apply the incremental upgrade:

```bash
mysql -h 127.0.0.1 -P 3306 -u seatunnel -p seatunnel_web \
  < seatunnel-web-api/src/main/resources/sql/seatunnel_measurement_file_sync_upgrade_mysql.sql
```

The backend schema check endpoint should pass:

```bash
curl -s http://localhost:8801/api/v1/measurement-file-sync/schema-check
```

If tables or columns are missing, the UI shows:

```text
请先执行 Measurement File Sync 数据库初始化脚本。
```

## 2. Prepare Test Files

Use a path visible from the seatunnel-web container. For NAS, mount the NAS path into the container first.

```bash
mkdir -p /mnt/nas/wat
cat > /mnt/nas/wat/simple_wat.csv <<'EOF'
LOT_ID,WAFER_ID,ITEM,VALUE,UNIT
LOT001,W01,VTH,1.23,V
LOT001,W01,IDSAT,33.1,uA
EOF
```

Optional parse-failure sample:

```bash
cat > /mnt/nas/wat/bad_wat.csv <<'EOF'
LOT_ID,WAFER_ID,ITEM,VALUE,UNIT
LOT001,W01,VTH,not_a_number,V
EOF
```

## 3. Create LOCAL_FILE Data Source

Open:

```text
http://localhost:8000/data-source
```

Create a `LOCAL_FILE` or `NAS` datasource:

```text
datasource_name = local_wat
root_path = /mnt/nas/wat
enabled = true
```

Run connection test. It should verify the path exists, is a directory, and is readable.

## 4. Create StarRocks Target Table

In StarRocks:

```sql
CREATE DATABASE IF NOT EXISTS st_test;

CREATE TABLE IF NOT EXISTS `st_test`.`measurement_item_result` (
  file_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  batch_id VARCHAR(100),
  run_id VARCHAR(100),
  source_file_name VARCHAR(500),
  source_relative_path VARCHAR(1000),
  parser_type VARCHAR(30),
  row_no BIGINT,
  lot_id VARCHAR(100),
  wafer_id VARCHAR(100),
  item_name VARCHAR(200),
  item_value DOUBLE,
  item_unit VARCHAR(50),
  raw_line VARCHAR(65533),
  parse_time DATETIME,
  ingest_time DATETIME
)
ENGINE=OLAP
DUPLICATE KEY(file_id, row_no)
DISTRIBUTED BY HASH(file_id) BUCKETS 8
PROPERTIES (
  "replication_num" = "1"
);
```

The UI also exposes `推荐建表 SQL` for the selected task. If preflight reports the target table is missing, copy the suggested DDL from the preflight result.

## 5. Create Measurement File Sync Task

Open:

```text
http://localhost:8000/measurement-file-sync
```

Create a task:

```text
task_name = simple csv wat
task_code = simple_csv_wat
parser_type = SIMPLE_CSV
source_datasource_id = local_wat
include_patterns = *.csv
recursive = false
dedup_strategy = PATH_SIZE_MTIME
staging_dir = /opt/seatunnel-web/staging/measurement
target_datasource_id = test StarRocks datasource
target_database = st_test
target_table = measurement_item_result
load_mode = APPEND
load_batch_mode = ONE_FILE_ONE_JOB
```

Parser config:

```json
{
  "header": true,
  "delimiter": ",",
  "columns": {
    "lot_id": "LOT_ID",
    "wafer_id": "WAFER_ID",
    "item_name": "ITEM",
    "item_value": "VALUE",
    "item_unit": "UNIT"
  }
}
```

Run `端到端预检查`.

Warnings about worker staging visibility are expected unless the environment can prove that the SeaTunnel worker sees the same path. Confirm that `/opt/seatunnel-web/staging/measurement` is shared with the SeaTunnel worker container before loading.

## 6. Discover Files

Click `发现文件`.

Expected:

```text
simple_wat.csv appears in 文件清单
file_status = PARSE_PENDING
Run History phase = DISCOVER
discovered_count >= 1
```

If `bad_wat.csv` is present and `include_patterns=*.csv`, it will also be discovered. Remove it for the success path or use `include_patterns=simple_*.csv`.

## 7. Preview Parse

Click `预览解析` on `simple_wat.csv`.

Expected rows:

```text
row_no=1 lot_id=LOT001 wafer_id=W01 item_name=VTH item_value=1.23 item_unit=V
row_no=2 lot_id=LOT001 wafer_id=W01 item_name=IDSAT item_value=33.1 item_unit=uA
```

Preview does not change `file_status`.

## 8. Parse and Load

Click `解析并装载`.

Expected:

```text
file_status = LOADED
parsed_row_count = 2
loaded_row_count = 2
staging_file_path is populated
Run History phase = PARSE_LOAD
parsed_file_count = 1
loaded_file_count = 1
parsed_row_count = 2
```

The generated SeaTunnel HOCON can be opened from Run History. Passwords are masked.

Query StarRocks:

```sql
SELECT *
FROM st_test.measurement_item_result
ORDER BY file_id, row_no;
```

Expected: 2 rows for `simple_wat.csv`.

## 9. Repeat Run Safety

Click `解析并装载` again at task level.

Expected:

```text
LOADED files are skipped by default
No duplicate StarRocks rows are inserted
Run History skipped_count increases when no eligible files are selected
```

For a file-level forced reload, the UI requires confirmation. In APPEND mode with a DUPLICATE KEY table, forced reload can insert duplicate rows. Use a Primary Key table or cleanup first.

Cleanup templates are available from the file row:

```sql
DELETE FROM `st_test`.`measurement_item_result`
WHERE file_id = ${file_id};

DELETE FROM `st_test`.`measurement_item_result`
WHERE batch_id = '${batch_id}';
```

## 10. Parse Failure

Use `bad_wat.csv` with SIMPLE_CSV and numeric item values expected. Run discovery and then parse/load.

Expected:

```text
file_status = PARSE_FAILED
Run History parse_failed_count >= 1
error_message contains the parser root cause
```

Click `重试失败` after fixing the source file or parser config.

## 11. Load Failure

Change `target_table` to a non-existing table and run `端到端预检查`.

Expected:

```text
preflight target_table = ERROR
recommended DDL is displayed
解析并装载 is blocked
```

To test runtime load failure, create an incompatible target table or remove StarRocks permissions after preflight:

```text
file_status = LOAD_FAILED
staging_file_path remains
Run History load_failed_count >= 1
error_message shows the SeaTunnel/StarRocks root cause
```

## 12. Concurrent Lock

Trigger `解析并装载` twice quickly for the same task.

Expected:

```text
one run processes files
the other run is SKIPPED
Run History error_message = Another measurement file PARSE_LOAD run is active
lock table has no stale residual lock after completion
```

## 13. FTP/SFTP Path

Create an FTP or SFTP datasource with:

```text
host
port
username
password
root_path
passive_mode for FTP if needed
```

Run connection test, discovery, preview, then parse/load. The parser reads through the file source stream and writes the same JSONL staging format.

## 14. Common Issues

`Measurement File Sync tables are missing`

Apply `seatunnel_sync_control_mysql.sql` for fresh environments or `seatunnel_measurement_file_sync_upgrade_mysql.sql` for old Measurement File Sync deployments.

`staging_dir does not exist`

Use preflight with directory creation enabled, or create it manually:

```bash
mkdir -p /opt/seatunnel-web/staging/measurement
chmod 775 /opt/seatunnel-web/staging/measurement
```

`SeaTunnel worker cannot read staging file`

Mount the same host/NAS directory into both seatunnel-web and SeaTunnel worker containers at the same path.

`StarRocks target table does not exist`

Copy the recommended DDL from the UI and create the table before loading.

`nodeUrls port looks wrong`

Use the StarRocks FE HTTP port, normally `8030`.

`base-url port looks wrong`

Use the StarRocks MySQL protocol port, normally `9030`, for example:

```text
jdbc:mysql://starrocks.lab:9030/
```

`JSONL format mismatch`

Each staging line must be one JSON object. The generated HOCON uses `LocalFile` with `file_format_type = "json"` and StarRocks `format = "JSON"`, `strip_outer_array = true`, matching the repo's existing StarRocks JSON load templates.
