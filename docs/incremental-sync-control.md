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

第三阶段新增表：

- `t_seatunnel_web_sync_check_config`
- `t_seatunnel_web_sync_check_result`

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

## 第三阶段范围

第三阶段实现 JDBC/SQL 增量任务的后置校验能力。SeaTunnel job 成功后不再直接认为同步成功，而是在 watermark 推进前进入 `VERIFYING` 阶段，按任务配置执行 source/sink/error/custom 校验 SQL。

第三阶段新增能力：

- 配置化 `SOURCE_COUNT`、`SINK_COUNT`、`ERROR_COUNT`、`CUSTOM_COUNT`、`CUSTOM_BOOLEAN` 校验。
- 校验 SQL 复用 HOCON 变量渲染规则。
- 校验结果保存到 `t_seatunnel_web_sync_check_result`。
- source/sink/error 指标回写到 batch/run。
- 校验通过后才推进 watermark。
- 校验失败或 SQL 执行异常时 batch/run 标记失败，watermark 不推进。

第三阶段仍不包含：

- 前端页面。
- LocalFile/FtpFile manifest 扫描。
- XXL-JOB 调度入口。
- 复杂 DAG。

## 第四阶段范围

第四阶段实现 LocalFile/FtpFile 文件类增量 source 的 manifest 控制面第一版。

新增能力：

- `source_type = LOCAL_FILE / FTP_FILE` 且 `strategy = FILE_MANIFEST / FILE_MTIME` 的任务进入文件类运行路径。
- LocalFile 使用 Java NIO 扫描本地路径，NAS 挂载目录按本地目录处理。
- 扫描结果写入 `t_seatunnel_web_sync_file_item`，状态初始为 `DISCOVERED`。
- 根据 `file_cursor_mode` 判断文件版本是否已存在：
  - `MTIME`：`file_path + last_modified_time`
  - `PATH_MTIME_SIZE`：`file_path + file_size + last_modified_time`
  - `MANIFEST`：同 `PATH_MTIME_SIZE`，并以 manifest 状态作为控制面依据
  - `CHECKSUM`：枚举保留，本阶段抛出 `Unsupported file cursor mode: CHECKSUM`
- 运行时先 discover，再创建 batch，并将本批文件 claim 为 `CLAIMED`。
- 提交 SeaTunnel 成功后将本批文件标记为 `PROCESSING`。
- SeaTunnel job success 且 verification passed 后将本批文件标记为 `SUCCESS`。
- SeaTunnel job failed、verification failed 或运行异常时将本批文件标记为 `FAILED`，且不推进任何文件 cursor。
- 提供 manifest 查询、发现和 batch 级 failed retry API。

第四阶段仍不包含：

- 前端页面。
- XXL-JOB 调度入口。
- 复杂 DAG。
- 自定义 SeaTunnel Source。
- 精确文件列表传入 SeaTunnel 的自定义插件。
- WAT/CP 业务专用文件解析。

FTP_FILE 当前只提供 scanner 接口和明确失败的骨架实现。仓库当前没有统一 FTP/SFTP datasource client 可复用，因此 `FtpSyncRemoteFileScanner` 会抛出：

```text
FTP file discovery is not implemented because no reusable FTP datasource client was found
```

## 第五阶段范围

第五阶段新增 Fab MES/SPC JDBC translator 内置模板第一版，把通用增量控制模块应用到旧 translator 改造场景：

```text
MES / SPC Oracle
  -> SeaTunnel JDBC/SQL 增量 source
  -> Sql transform
  -> StarRocks xchg_meas_header / xchg_meas_site / xchg_meas_error
```

新增内容：

- 内置模板 `FAB_MES_SPC_JDBC_TRANSLATOR`。
- StarRocks xchg 层 DDL 示例：`docs/sql/fab_mes_spc_xchg_starrocks.sql`。
- 通用 HOCON 模板：`docs/templates/fab_mes_spc_jdbc_translator.conf`。
- Lab Oracle/StarRocks HOCON 示例：`docs/templates/fab_mes_spc_jdbc_translator_lab_oracle_starrocks.conf`。
- 模板创建 API，用现有 sync metadata 表初始化 task/version/incremental_config/watermark/check_config。
- 业务说明文档：`docs/fab-mes-spc-translator-template.md`。

旧链路与新链路映射：

- `translator.exe` 的抽取与标准化逻辑由 SeaTunnel JDBC source + Sql transform 承接。
- 专有父子文本文件的 header/site/error 结构映射到 StarRocks xchg 表。
- `loader.exe` publish 到分析主库的阶段本轮不做，后续可扩展为 xchg -> eda_stg -> dwd/ads。

StarRocks xchg 表：

- `xchg_batch`：批次摘要。
- `xchg_meas_header`：量测 header。
- `xchg_meas_site`：site/die 明细。
- `xchg_meas_error`：translator 校验错误。

模板变量包括：

- `${batch_id}`、`${run_id}`、`${task_code}`
- `${source_system}`
- `${source_jdbc_url}`、`${source_jdbc_driver}`、`${source_username}`、`${source_password}`
- `${header_source_sql}`、`${site_source_sql}`
- `${batch_start_time}`、`${batch_end_time}`、`${batch_start_value}`、`${batch_end_value}`
- `${starrocks_node_urls}`、`${starrocks_base_url}`、`${starrocks_username}`、`${starrocks_password}`、`${starrocks_database}`

默认 check_config 规则：

- `source_count`：基于 header SQL 统计源端 header 数。
- `sink_header_count`：统计 `xchg_meas_header`，并与 `source_count` 比较。
- `sink_site_count`：统计 `xchg_meas_site`，只作为独立指标。
- `error_count`：统计 `xchg_meas_error`，期望为 0。
- 如果 `sourceDatasourceId` 或 `sinkDatasourceId` 为空，不创建默认 check，并在响应 warnings 中说明。

当前限制：

- 不做 loader publish 到 dwd/ads。
- 不做 WAT/CP 文件 parser。
- header/site SQL 需要用户按真实 MES/SPC 表结构提供。
- 默认 check 只提供基础 count 校验。

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
- `${file_path}`
- `${file_pattern}`
- `${file_recursive}`
- `${file_filter_pattern}`
- `${batch_file_count}`
- `${batch_file_relative_paths}`
- `${batch_file_names}`

接口请求里的 `params` 会合并进模板变量，可用于传入 `${batchEndValue}`、`${tenant}` 等业务变量。

文件类任务推荐使用标准 LocalFile/FtpFile 的 path 和 pattern 变量，例如：

```hocon
source {
  LocalFile {
    path = "${file_path}"
    file_filter_pattern = "${file_filter_pattern}"
    file_format_type = "json"
  }
}
```

当前 manifest 是 seatunnel-web 的控制面记录。SeaTunnel 标准 LocalFile/FtpFile 仍通过 HOCON 中的 `path`、`file_filter_pattern` 等参数读取文件，本阶段不保证将 batch 的精确文件列表传入 SeaTunnel。生产要做到精确单文件或精确 batch 文件处理，后续可以扩展为为每个 batch 生成临时目录或 symlink 目录、生成 batch file list，或实现自定义 `FileManifestSource`。

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

JDBC/SQL 任务运行流程：

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
12. 查询并执行启用的 check 配置；如果没有配置 check，兼容第二阶段行为，跳过校验。
13. 校验通过后回写 `source_count`、`sink_count`、`error_count`。
14. 标记 batch/run 成功。
15. 推进 watermark。

如果任一阻断校验失败或校验 SQL 执行异常：

- `batch.status = FAILED`
- `run.status = FAILED`
- `error_message` 写入校验失败信息
- watermark 不推进

LocalFile/FtpFile 文件类任务运行流程：

1. 根据任务增量配置扫描文件。
2. 按 `file_cursor_mode` 和已有 manifest 状态去重。
3. 新文件写入 `t_seatunnel_web_sync_file_item`，状态为 `DISCOVERED`。
4. 创建 batch，文件类 batch 的 `batch_start_value` 和 `batch_end_value` 为空。
5. 将本批要处理的 `DISCOVERED / FAILED` 文件 claim 为 `CLAIMED`。
6. 创建 run。
7. 渲染 HOCON，写入 `generated_hocon`。
8. 提交 SeaTunnel，保存 job id/name。
9. 将本批文件标记为 `PROCESSING`。
10. 轮询 job 状态。
11. SeaTunnel job 成功后执行 verification。
12. verification passed 后将本批文件标记为 `SUCCESS`，并标记 batch/run 成功。
13. SeaTunnel job failed、verification failed 或异常时将本批文件标记为 `FAILED`，并标记 batch/run 失败。

文件类任务不推进 `t_seatunnel_web_sync_watermark`。本批 `source_count` 第一版使用 claimed file count。

`t_seatunnel_web_sync_file_item` 状态流转：

```text
DISCOVERED
  -> CLAIMED
  -> PROCESSING
  -> SUCCESS

DISCOVERED / CLAIMED / PROCESSING
  -> FAILED
```

重复发现规则：

- 同一 task 下，同一文件 identity 已经 `SUCCESS`，扫描时跳过。
- 已经 `DISCOVERED / CLAIMED / PROCESSING`，扫描时不重复插入。
- 已经 `FAILED`，后续 run 或 batch retry 可以重新 claim。
- 同一路径文件 size/mtime 变化，按新版本文件插入。

LocalFile scanner 规则：

- `file_path` 为空会抛出清晰异常。
- `file_path` 不存在或不是目录会抛出清晰异常。
- `file_recursive = true` 时递归扫描，否则只扫描顶层普通文件。
- 只记录普通文件，忽略目录。
- `file_pattern` 为空时匹配全部普通文件。
- `file_pattern` 有值时按正则优先匹配 relative path，不匹配再匹配 file name。
- `last_modified_time` 按 `file_timezone` 转换；为空时使用系统默认时区。

`FILE_MANIFEST` 和 `FILE_MTIME` 的区别：

- `FILE_MTIME` 强调基于文件修改时间/大小的增量发现。
- `FILE_MANIFEST` 强调以 `t_seatunnel_web_sync_file_item` 作为控制面清单，运行状态、失败重试和审计都以 manifest 为准。
- 当前两者在文件 identity 计算上可使用相同规则，区别主要体现在运维语义和后续精确文件处理扩展。

## 补数流程

补数接口使用 `BACKFILL` trigger 和 `BACKFILL` run mode。

`UPDATE_TIME_RANGE` 补数必须传：

- `startTime`
- `endTime`

`ID_RANGE` 补数必须传：

- `startValue`
- `endValue`

补数默认不推进主 watermark。只有请求传 `advanceWatermark=true`，并且增量配置 `backfill_advance_watermark=1` 时，补数成功后才推进 watermark。

## 后置校验配置

`t_seatunnel_web_sync_check_config` 保存任务级校验 SQL。

常用 check type：

- `SOURCE_COUNT`：源端本批范围内的数量，通常返回一行一列数字。
- `SINK_COUNT`：目标端本批写入数量，可通过 `compare_to_check_code=source_count` 与源端比较。
- `ERROR_COUNT`：错误表或异常记录数量，通常配置 `expected_operator=EQ`、`expected_value=0`。
- `CUSTOM_COUNT`：业务自定义数量校验。
- `CUSTOM_BOOLEAN`：返回 `1/0`、`true/false` 或非零数字，未配置 operator 时按布尔值判断。

`sql_text` 支持与 HOCON 相同的变量渲染规则，例如：

- `${batch_id}`
- `${run_id}`
- `${task_code}`
- `${batch_start_value}`
- `${batch_end_value}`
- `${batch_start_time}`
- `${batch_end_time}`

校验 SQL 只允许 `SELECT` 或 `WITH` 开头的只读 SQL。执行器会拒绝 `INSERT`、`UPDATE`、`DELETE`、`DROP`、`ALTER`、`TRUNCATE`、`CREATE`、`REPLACE`、`MERGE`、`CALL` 等写入或 DDL 关键字。

`expected_operator` 支持：

- `EQ`
- `NE`
- `GT`
- `GE`
- `LT`
- `LE`
- `IS_NULL`
- `IS_NOT_NULL`

数字比较使用 `BigDecimal`；非数字的 `EQ/NE` 使用字符串比较。

`compare_to_check_code` 用于比较已执行的校验结果。典型配置：

```text
source_count actual = 100
sink_count compare_to_check_code = source_count
sink_count expected_operator = EQ
sink_count actual = 100
=> sink_count passed = true
```

被比较的 check 必须通过 `sort_order` 排在当前 check 之前，否则当前 check 会失败。

## 后置校验结果

`t_seatunnel_web_sync_check_result` 保存每次 run 的校验明细：

- 渲染后的 SQL：`rendered_sql`
- 实际值：`actual_value`
- 比较符和值：`expected_operator`、`expected_value`
- 比较目标：`compare_to_check_code`、`compare_to_actual_value`
- 是否通过：`passed`
- 是否阻断：`fail_on_mismatch`
- SQL 执行或比较错误：`error_message`

校验通过后，`SOURCE_COUNT`、`SINK_COUNT`、`ERROR_COUNT` 的实际值会同步回写到 `t_seatunnel_web_sync_batch` 和 `t_seatunnel_web_sync_run` 的统计字段。

## 第六阶段：Fab loader publish 模板

第五阶段的 Fab MES/SPC translator 模板负责：

```text
MES/SPC Oracle -> SeaTunnel -> StarRocks xchg_meas_header / xchg_meas_site / xchg_meas_error
```

第六阶段新增 loader publish 模板，负责旧 `loader.exe` 的第一段迁移：

```text
StarRocks xchg 层 -> SeaTunnel -> eda_stg_measure_header / eda_stg_measure_site / eda_stg_measure_error
```

本阶段不实现 SQL 工作流引擎，也不执行 `eda_stg -> dwd/ads`。DWD/ADS publish 以 StarRocks SQL 模板提供：

- `docs/sql/fab_loader_publish_starrocks.sql`：eda_stg、dwd、ads 表 DDL 示例。
- `docs/sql/fab_loader_publish_dwd_ads.sql`：eda_stg 发布到 dwd/ads 的 SQL 示例。
- `docs/templates/fab_loader_publish_xchg_to_stg.conf`：xchg 到 eda_stg 的 SeaTunnel HOCON。
- `docs/fab-loader-publish-template.md`：业务模板说明。

模板 code：

```text
FAB_LOADER_PUBLISH_XCHG_TO_STG
```

### 非增量任务运行

loader publish 任务不拥有 watermark，创建任务时：

- `task_type = BATCH`
- `source_type = SQL`
- `sink_type = STARROCKS`
- `engine_type = ZETA`
- `incremental_enabled = 0`
- 不创建 `t_seatunnel_web_sync_incremental_config`
- 不创建 `t_seatunnel_web_sync_watermark`

`incremental_enabled = 0` 的任务运行时会：

1. 跳过 incremental_config 和 watermark 读取。
2. 创建系统 sync batch 和 run。
3. 使用系统变量与 run params 渲染 HOCON。
4. 提交 SeaTunnel。
5. SeaTunnel success 后执行 verification。
6. verification passed 后标记 batch/run SUCCESS。
7. verification failed 或 SeaTunnel failed 后标记 batch/run FAILED。
8. 不推进 watermark。

### 系统 batch_id 与业务 xchg_batch_id

loader publish 同时存在两个批次概念：

- `${batch_id}`：seatunnel-web 为本次 loader run 生成的系统 batch id。
- `${xchg_batch_id}`：translator 已生成的业务 batch id，用于过滤 xchg 和 stg 数据。

HOCON 查询 xchg 表时必须使用：

```sql
WHERE batch_id = '${xchg_batch_id}'
```

不要使用系统 `${batch_id}` 过滤 xchg 表。

### 默认 check 规则

如果 `enableDefaultChecks = true` 且 `starrocksDatasourceId` 不为空，create-task 会创建：

- `xchg_header_count >= 0`
- `stg_header_count == xchg_header_count`
- `xchg_site_count >= 0`
- `stg_site_count == xchg_site_count`
- `xchg_error_count >= 0`
- `stg_error_count == xchg_error_count`

loader publish 不要求 error count 等于 0。translator 可能把错误记录写入 `xchg_meas_error`，loader 需要把这些错误记录同步到 `eda_stg_measure_error`。

## 第七阶段：JDBC/SQL/StarRocks 增量测试模板

第七阶段先暂停文件类能力，优先补齐 JDBC/SQL/StarRocks 增量 Batch source 管理功能的可测试模板。目标链路是：

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

新增模板 code：

```text
GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL
```

新增文件：

- `docs/templates/generic_jdbc_sql_to_starrocks_incremental.conf`
- `seatunnel-web-api/src/main/resources/sync/templates/generic_jdbc_sql_to_starrocks_incremental.conf`
- `docs/sql/generic_jdbc_sql_incremental_starrocks_lab.sql`
- `docs/generic-jdbc-starrocks-incremental-test.md`

该模板创建 `incremental_enabled = 1` 的 BATCH 任务，支持：

- `UPDATE_TIME_RANGE`
- `ID_RANGE`
- JDBC source query
- StarRocks sink
- 默认 `source_count / sink_count / error_count` 校验

默认 check 规则：

- `source_count >= 0`
- `sink_count == source_count`
- `error_count == 0`

如果 `sourceDatasourceId` 或 `sinkDatasourceId` 为空，create-task 不创建默认 check，并返回 warning：

```text
sourceDatasourceId or sinkDatasourceId is missing, default checks are skipped.
```

## 后端 API

路径按项目现有规范使用 `/api/v1` 前缀：

```http
POST /api/v1/sync/tasks/{taskCode}/preview-hocon
POST /api/v1/sync/tasks/{taskCode}/run
POST /api/v1/sync/tasks/{taskCode}/backfill
GET  /api/v1/sync/runs/{runId}
GET  /api/v1/sync/tasks/{taskCode}/watermark
POST /api/v1/sync/tasks/{taskCode}/discover-files
GET  /api/v1/sync/tasks/{taskCode}/files
GET  /api/v1/sync/batches/{batchId}/files
POST /api/v1/sync/batches/{batchId}/files/retry
GET  /api/v1/sync/templates
GET  /api/v1/sync/templates/{templateCode}
POST /api/v1/sync/templates/fab-mes-spc-jdbc/create-task
POST /api/v1/sync/templates/fab-loader-publish/create-task
POST /api/v1/sync/templates/generic-jdbc-starrocks/create-task
GET  /api/v1/sync/tasks/{taskCode}/checks
POST /api/v1/sync/tasks/{taskCode}/checks
PUT  /api/v1/sync/tasks/{taskCode}/checks/{checkCode}
GET  /api/v1/sync/runs/{runId}/checks
POST /api/v1/sync/tasks/{taskCode}/diagnose
POST /api/v1/sync/tasks/{taskCode}/preview-range
POST /api/v1/sync/tasks/{taskCode}/diagnose-hocon
POST /api/v1/sync/tasks/{taskCode}/diagnose-checks
GET  /api/v1/sync/tasks/{taskCode}/runs
GET  /api/v1/sync/tasks/{taskCode}/batches
GET  /api/v1/sync/runs/{runId}/audits
GET  /api/v1/sync/batches/{batchId}
GET  /api/v1/sync/batches/{batchId}/audits
PUT  /api/v1/sync/tasks/{taskCode}/watermark
POST /api/v1/sync/runs/{runId}/rerun
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

文件发现请求示例：

```json
{
  "maxFiles": 1000
}
```

文件查询支持以下 query 参数：

- `status`
- `batchId`
- `runId`
- `fileName`
- `filePath`

Fab MES/SPC 模板创建请求示例：

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

Fab loader publish 模板创建请求示例：

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

Fab loader publish 运行请求示例：

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

source_count 配置示例：

```json
{
  "checkCode": "source_count",
  "checkName": "Source count",
  "checkType": "SOURCE_COUNT",
  "datasourceType": "SOURCE",
  "datasourceId": 1,
  "sqlText": "SELECT COUNT(*) FROM ST_ORDER_HEADER WHERE UPDATE_TIME >= TO_TIMESTAMP('${batch_start_time}', 'YYYY-MM-DD HH24:MI:SS') AND UPDATE_TIME < TO_TIMESTAMP('${batch_end_time}', 'YYYY-MM-DD HH24:MI:SS')",
  "expectedOperator": "GE",
  "expectedValue": "0",
  "failOnMismatch": true,
  "enabled": true,
  "sortOrder": 10
}
```

sink_count 配置示例：

```json
{
  "checkCode": "sink_count",
  "checkName": "Sink count",
  "checkType": "SINK_COUNT",
  "datasourceType": "SINK",
  "datasourceId": 2,
  "sqlText": "SELECT COUNT(*) FROM xchg_meas_header WHERE batch_id = '${batch_id}'",
  "expectedOperator": "EQ",
  "compareToCheckCode": "source_count",
  "failOnMismatch": true,
  "enabled": true,
  "sortOrder": 20
}
```

error_count 配置示例：

```json
{
  "checkCode": "error_count",
  "checkName": "Error count",
  "checkType": "ERROR_COUNT",
  "datasourceType": "SINK",
  "datasourceId": 2,
  "sqlText": "SELECT COUNT(*) FROM xchg_meas_error WHERE batch_id = '${batch_id}'",
  "expectedOperator": "EQ",
  "expectedValue": "0",
  "failOnMismatch": true,
  "enabled": true,
  "sortOrder": 30
}
```

## 第八阶段：增量 Batch 诊断与观测

第八阶段继续暂停 FtpFile、LocalFile、WAT/CP 文件 translator、文件 manifest 增强、前端、XXL-JOB 和 DAG。本阶段只增强 JDBC / SQL / Oracle / StarRocks 增量 Batch source 管理功能在真实环境测试时的诊断、排错和只读查询能力。

本阶段不新增表，复用以下已有表：

- `t_seatunnel_web_sync_task`
- `t_seatunnel_web_sync_task_version`
- `t_seatunnel_web_sync_incremental_config`
- `t_seatunnel_web_sync_watermark`
- `t_seatunnel_web_sync_batch`
- `t_seatunnel_web_sync_run`
- `t_seatunnel_web_sync_audit`
- `t_seatunnel_web_sync_check_config`
- `t_seatunnel_web_sync_check_result`

### 诊断 API

`diagnose` 汇总检查 task、version、incremental_config、watermark、range preview、HOCON、check SQL 和 Zeta client 可见性。

```http
POST /api/v1/sync/tasks/{taskCode}/diagnose
```

```json
{
  "params": {
    "batchEndValue": "100",
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

返回内容包括：

- `task`：任务 ID、code、name、状态、类型、source/sink/engine、clientId、增量开关和策略。
- `version`：versionId、versionNo、发布状态、hoconHash、是否存在。
- `incrementalConfig`：策略、watermark 字段、起点、lookback、maxBatchSeconds。
- `watermark`：当前值、前值、valueType、最近成功 run/batch。
- `rangePreview`：下一批 start/end value/time、lookback/maxBatchSeconds 应用情况和 warnings。
- `hocon`：是否可渲染、缺失变量、渲染 hash、脱敏预览。
- `checks`：check 数量、启用数量、缺 datasourceId、是否可渲染、SQL 预览。
- `client`：clientId、是否可见、warning。
- `diagnostics`：`OK / WARN / ERROR` 和消息列表。

`diagnose` 是只读诊断：

- 不创建 batch。
- 不创建 run。
- 不提交 SeaTunnel。
- 不推进 watermark。
- 默认不执行 check SQL。
- 所有返回中的 password、token、secret 等敏感信息会脱敏。

如果任务不存在，返回明确错误。任务状态不是 `PUBLISHED` 时返回 WARN 或 ERROR。`ID_RANGE` 缺少 `batchEndValue` 时返回：

```text
ID_RANGE requires batchEndValue in run params
```

### Range Preview API

只预览下一次 batch range，不落库、不推进 watermark。

```http
POST /api/v1/sync/tasks/{taskCode}/preview-range
```

UPDATE_TIME_RANGE 示例：

```json
{
  "runMode": "NORMAL",
  "params": {}
}
```

ID_RANGE 示例：

```json
{
  "runMode": "NORMAL",
  "params": {
    "batchEndValue": "100"
  }
}
```

BACKFILL 示例：

```json
{
  "runMode": "BACKFILL",
  "params": {
    "startTime": "2026-06-01 00:00:00",
    "endTime": "2026-06-01 01:00:00",
    "advanceWatermark": false
  }
}
```

返回字段：

- `taskCode`
- `strategy`
- `watermarkKey`
- `currentWatermark`
- `startValue`
- `endValue`
- `startTime`
- `endTime`
- `lookbackApplied`
- `maxBatchSecondsApplied`
- `willAdvanceWatermark`
- `warnings`

该接口复用 `SyncWatermarkService.previewRange`，与正式 run 使用同一套 range 计算逻辑。

### HOCON 诊断 API

只检查当前 task version 的 HOCON 模板是否能渲染。

```http
POST /api/v1/sync/tasks/{taskCode}/diagnose-hocon
```

```json
{
  "params": {
    "batchEndValue": "100",
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  },
  "includeRenderedHocon": false
}
```

返回字段：

- `renderable`
- `missingVariables`
- `renderedHash`
- `renderedHocon`
- `renderedHoconPreview`
- `variablesUsed`
- `maskedParams`
- `errorMessage`

`HoconRenderService` 提供：

```java
Set<String> extractVariables(String template);

List<String> findMissingVariables(String template, Map<String, Object> variables);
```

缺变量时一次性返回所有缺失变量，不只返回第一个。`includeRenderedHocon=false` 时不返回完整 HOCON，只返回 hash 和预览。

### Check SQL 诊断 API

检查 `t_seatunnel_web_sync_check_config` 中的 SQL 是否能渲染，必要时执行。

```http
POST /api/v1/sync/tasks/{taskCode}/diagnose-checks
```

```json
{
  "params": {
    "batch_id": "test_batch_001",
    "batchEndValue": "100",
    "source_username": "st_lab",
    "source_password": "st_lab_pass",
    "starrocks_username": "st_lab",
    "starrocks_password": "st_lab_pass"
  },
  "executeSql": false
}
```

每个 check 返回：

- `checkCode`
- `checkName`
- `checkType`
- `datasourceId`
- `enabled`
- `renderable`
- `missingVariables`
- `renderedSqlPreview`
- `executeSql`
- `executed`
- `actualValue`
- `passed`
- `errorMessage`

`executeSql=false` 时只渲染不执行。`executeSql=true` 时复用 `SyncCheckSqlExecutor` 执行只读 SQL。单个 check 的 SQL 执行异常不会影响其他 check 的诊断。

### 只读查询 API

为真实测试补齐运行、批次和 audit 查询能力。

```http
GET /api/v1/sync/tasks/{taskCode}/runs
GET /api/v1/sync/tasks/{taskCode}/batches
GET /api/v1/sync/runs/{runId}/audits
GET /api/v1/sync/batches/{batchId}
GET /api/v1/sync/batches/{batchId}/audits
```

最小分页参数：

- `pageNo`
- `pageSize`
- `status`
- `startTime`
- `endTime`

run 列表返回：

- `runId`
- `batchId`
- `taskCode`
- `status`
- `seatunnelJobId`
- `submitTime`
- `startTime`
- `endTime`
- `sourceCount`
- `sinkCount`
- `errorCount`
- `errorMessage`

batch 列表和详情返回：

- `batchId`
- `taskCode`
- `status`
- `batchStartValue`
- `batchEndValue`
- `batchStartTime`
- `batchEndTime`
- `sourceCount`
- `sinkCount`
- `errorCount`
- `errorMessage`
- `createTime`
- `updateTime`

audit 返回：

- `eventType`
- `eventLevel`
- `eventMessage`
- `detailJson`
- `createTime`

`detailJson` 返回前会脱敏，避免历史 audit 中已有敏感信息直接暴露。

### 手动 Watermark 修正 API

测试阶段可手动修正 watermark。

```http
PUT /api/v1/sync/tasks/{taskCode}/watermark
```

```json
{
  "watermarkKey": "default",
  "currentValue": "2026-06-01 00:00:00",
  "reason": "reset for lab test"
}
```

行为：

- 更新 `t_seatunnel_web_sync_watermark.current_value`。
- 更新前的 `current_value` 写入 `previous_value`。
- `reason` 不允许为空。
- 写 audit：
  - `event_type = MANUAL_UPDATE_WATERMARK`
  - `event_level = WARN`
  - `detail_json` 包含 `oldValue`、`newValue`、`reason`
- 如果 watermark 不存在，但 task 和 incremental_config 存在，则允许创建默认 watermark。
- 如果 task 不存在，直接报错。

### Rerun API 第一版

第一版只支持 JDBC / SQL 增量任务的同范围重跑。

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

规则：

1. 找到原 run 和原 batch。
2. 使用原 batch 的 `batch_start_value`、`batch_end_value`、`batch_start_time`、`batch_end_time`。
3. 新建一个新的系统 batch 和新的 run，避免覆盖原失败状态。
4. 不重新计算 watermark range。
5. 新 run 成功且 verification passed 后，watermark 推进到原 range end。
6. 新 run 失败或 verification failed 时，不推进 watermark。
7. 原 run 没有 range 信息时返回不支持。
8. 文件类任务本阶段不支持 rerun。

### 真实测试排错 SOP

完整 SOP 见：

```text
docs/generic-jdbc-starrocks-incremental-test.md
```

### 前端测试入口

前端第一版提供单页面“增量同步测试台”，用于手动测试 JDBC / SQL / Oracle / StarRocks 增量 Batch source 管理闭环。

完整操作说明见：

```text
docs/sync-control-test-console-user-guide.md
```

菜单：

```text
增量同步 -> 增量同步测试台
```

路由：

```text
/sync-control/test-console
```

页面顶部有全局 `taskCode` 输入框，后续所有 tab 默认使用该任务。

Tab 能力：

- `模板创建`：查询模板；创建 `GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL` 任务；提供 UPDATE_TIME_RANGE 和 ID_RANGE lab 示例填充。
- `任务诊断`：调用 `POST /api/v1/sync/tasks/{taskCode}/diagnose`，展示 task/version/config/watermark/range/HOCON/check/client/diagnostics。
- `HOCON / Range`：预览 range；诊断 HOCON；调用旧 `preview-hocon`；支持复制 rendered HOCON。
- `运行任务`：普通 run、backfill、`RERUN_SAME_RANGE` rerun。
- `Runs`：按 status/time/page 查询 runs；查看详情；跳转 run audits/checks；填充 rerun。
- `Batches`：按 status/time/page 查询 batches；查看 batch 详情和 batch audits。
- `Watermark`：查询 watermark；手动修正 watermark，`reason` 必填，并提示仅用于 lab 或人工修复。
- `Checks`：查询/新增/编辑 check config；诊断 check SQL；按 runId 查询 check results。
- `Audits`：查询 run audits 或 batch audits，支持展开 `detailJson`。

前端请求使用项目现有 `HttpUtils` 封装，不新增 UI 框架。params JSON 输入会先做 `JSON.parse` 校验；错误时提示“JSON 格式错误”。

本轮前端滚动修复：

- `/sync-control/test-console` 参考 `/metrics` 的页面内滚动方式，在测试台根容器内建立独立滚动层。
- 根容器只占满 ProLayout 内容区，不修改全局 `body` 样式，也不影响 `/metrics` 等其他页面。
- 内容超过屏幕高度时由测试台自己的滚动层滚动，底部保留 padding，避免最后一个 Tab 内容被裁剪。
- Watermark、Checks 诊断和 Audits 等宽表补充横向滚动，避免表格撑坏页面。

常见定位路径：

| 问题 | 首选接口 | 关注字段 |
| --- | --- | --- |
| HOCON 缺变量 | `POST /diagnose-hocon` | `missingVariables` |
| `batchEndValue` 缺失 | `POST /preview-range` | `ID_RANGE requires batchEndValue in run params` |
| check SQL 渲染失败 | `POST /diagnose-checks` | `missingVariables`、`renderedSqlPreview` |
| check SQL 执行失败 | `POST /diagnose-checks` 且 `executeSql=true` | `errorMessage`、`datasourceId` |
| Zeta 提交失败 | `GET /runs/{runId}/audits` | `SUBMIT_JOB` audit |
| SeaTunnel job failed | `GET /tasks/{taskCode}/runs` | `seatunnelJobId`、`errorMessage` |
| watermark 没推进 | watermark + run/batch/audit 查询 | run/batch status、`ADVANCE_WATERMARK` audit |

## 后续阶段建议

后续建议按以下方向扩展：

- 前端运行历史和 watermark 页面。
- 前端 check 配置和 check 结果页面。
- 前端 file manifest 页面。
- XXL-JOB 触发入口。
- Fab MES/SPC translator 模板沉淀。
- Fab WAT/CP 文件 translator 模板沉淀。
- source_count_sql/sink_count_sql 的实际配置示例。
