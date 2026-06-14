# 增量同步测试台操作说明

> 说明：该页面现在只作为开发诊断入口保留，用于验证旧 sync-control 元数据、内置模板和 lab 场景。正式增量同步主流程请使用 `/sync/batch-link-up` 任务详情里的“增量控制”，完整说明见 `docs/batch-link-up-incremental-control.md`。

页面入口：

```text
菜单：增量同步 / 增量同步测试台
路由：http://localhost:8000/sync-control/test-console
```

## 模块用途

增量同步测试台用于手动测试 JDBC / SQL / Oracle / StarRocks 增量 Batch source 管理功能，重点验证数据库类 translator 从创建到运行、校验、watermark 推进和失败恢复的完整闭环。

页面覆盖能力：

- 模板创建
- 任务诊断
- Range 预览
- HOCON 诊断
- HOCON 预览
- 任务运行
- Runs 查询
- Batches 查询
- Audits 查询
- Checks 查询
- Watermark 查询和修正
- Rerun

## 页面区域说明

页面顶部的 `taskCode` 输入框是当前任务上下文。除“模板创建”外，其他 Tab 默认使用这里的任务编码。创建任务成功后，页面会自动把返回的 `taskCode` 填到顶部输入框。

### 模板创建

用途：创建 `GENERIC_JDBC_SQL_TO_STARROCKS_INCREMENTAL` 测试任务，并查询后端内置模板。

需要填写：

- `taskCode`、`taskName`、`clientId`
- `incrementalStrategy`：`UPDATE_TIME_RANGE` 或 `ID_RANGE`
- `watermarkField`、`watermarkFieldType`、`startValue`
- source JDBC URL、driver、用户名、密码、source SQL
- StarRocks node/base URL、用户名、密码、database、table、error table
- `sourceDatasourceId`、`sinkDatasourceId`，用于默认 count checks

操作：

1. 点击“填充 UPDATE_TIME_RANGE 示例”或“填充 ID_RANGE 示例”。
2. 修改 `clientId`、datasourceId、账号密码变量或真实连接地址。
3. 点击“创建任务”。

成功后看哪里：

- 右侧“创建结果”显示 `taskId`、`taskCode`、`versionId`、`createdCheckCount`。
- 页面顶部 `taskCode` 自动切到新任务。

常见错误看哪里：

- 创建接口错误会在页面消息提示。
- 默认 check 未创建时看“创建结果”的 `warnings`。

### 任务诊断

用途：一次性检查 task、version、增量配置、watermark、range、HOCON、checks 和 client 可见性。

需要填写：

- `params JSON`：至少传入 HOCON 中引用的账号密码变量。
- `includeHoconPreview`：是否返回脱敏 HOCON 预览。
- `includeCheckPreview`：是否返回 check SQL 预览。
- `includeDatasourceCheck`：是否检查 datasource 可见性。

操作：点击“诊断”。

成功后看哪里：

- `diagnostics` 等级是否为 `OK`。
- `version` 是否存在。
- `incrementalConfig` 是否存在。
- `watermark.currentValue` 是否符合预期。
- `hocon.renderable` 是否为 `true`。
- `hocon.missingVariables` 是否为空。
- `checks` 是否可渲染。
- `client` 是否能找到 `clientId`。

常见错误看哪里：

- HOCON 缺变量看 `hocon.missingVariables`。
- `ID_RANGE` 缺 `batchEndValue` 看 `rangePreview` 和 diagnostics messages。
- client 配置错误看 `client` 和 diagnostics messages。

### HOCON / Range

用途：在不创建 batch、不提交 SeaTunnel、不推进 watermark 的情况下，预览下一批范围并检查 HOCON 渲染结果。

需要填写：

- Preview Range：`runMode` 和 `params JSON`。
- Diagnose HOCON：`params JSON` 和 `includeRenderedHocon`。
- Preview HOCON：`params JSON`。

操作：

1. 点击“预览 Range”。
2. 点击“诊断 HOCON”。
3. 必要时点击“预览 HOCON”查看完整渲染结果。

成功后看哪里：

- Range 结果中的 `startTime/endTime` 或 `startValue/endValue`。
- `willAdvanceWatermark` 是否符合预期。
- HOCON 诊断中的 `renderable`、`missingVariables`、`renderedHash`。
- Preview HOCON 中的 source SQL 和 StarRocks sink 配置。

常见错误看哪里：

- 变量缺失看 `missingVariables`。
- `ID_RANGE` 没有传 `batchEndValue` 看 Preview Range 的错误提示。
- HOCON 语法或变量渲染错误看 `errorMessage`。

### 运行任务

用途：发起普通 run、backfill 或 failed run 的 `RERUN_SAME_RANGE`。

需要填写：

- 普通运行：`triggerType`、`runMode`、`waitForFinish`、`params JSON`。
- Backfill：时间范围或 ID 范围、`advanceWatermark`、`waitForFinish`、`params JSON`。
- Rerun：原失败 `runId`、`mode`、`waitForFinish`、`params JSON`。

操作：

- 普通同步点击“运行任务”。
- 补数点击“补数运行”。
- 重跑点击“Rerun”。

成功后看哪里：

- 下方结果卡片显示 `runId`、`batchId`、`status`。
- 可点击“填充 run audits”跳到 Audits。
- 可点击“填充 check results”跳到 Checks。

常见错误看哪里：

- 提交失败看 Runs 和 Audits。
- 校验失败看 Checks 和 Audits。
- 参数 JSON 解析失败会在提交前提示。

### Runs

用途：查询任务运行历史，进入详情、审计、校验和 rerun。

需要填写：

- 可选 `status`、`startTime`、`endTime`。
- `pageNo`、`pageSize`。

操作：点击“查询”。

成功后看哪里：

- `status`、`seatunnelJobId`、`sourceCount`、`sinkCount`、`errorCount`、`errorMessage`。
- 点击“详情”查看 run 详情。
- 点击“audits”跳转到 run audits。
- 点击“checks”跳转到 check results。
- 点击“rerun”把 runId 填入 Rerun 表单。

常见错误看哪里：

- SeaTunnel 提交或轮询失败看 `errorMessage` 和 Audits。
- Verification failed 看 Checks。

### Batches

用途：查询每批增量范围和批次状态。

需要填写：

- 可选 `status`、`startTime`、`endTime`。
- `pageNo`、`pageSize`。

操作：点击“查询”。

成功后看哪里：

- `batchStartValue/batchEndValue` 或 `batchStartTime/batchEndTime`。
- `sourceCount`、`sinkCount`、`errorCount`。
- 点击“详情”查看 batch 详情。
- 点击“audits”跳转到 batch audits。

常见错误看哪里：

- batch failed 看 `errorMessage`。
- range 不符合预期时回到 HOCON / Range 做 Preview Range。

### Watermark

用途：查询当前 watermark，并在 lab 测试或人工修复时手动修正。

需要填写：

- 查询不需要额外参数，只需要顶部 `taskCode`。
- 手动修正需要 `watermarkKey`、`currentValue`、`reason`。

操作：

- 点击“刷新”查询 watermark。
- 确认需要人工修正时，填写 reason 后点击“更新 watermark”。

成功后看哪里：

- `currentValue` 是否推进到上一批成功 range end。
- `previousValue`、`lastSuccessRunId`、`lastSuccessBatchId` 是否更新。

常见错误看哪里：

- Watermark 没推进时同时看 Runs、Checks、Audits。
- 手动修正失败通常是 `reason` 为空或任务不存在。

### Checks

用途：管理 check config，诊断 check SQL，并查看某次 run 的 check 结果。

需要填写：

- Check Config：check code、type、datasourceId、SQL、期望值或比较目标。
- Check Diagnose：`params JSON`，可选 `executeSql`。
- Check Result：`runId`。

操作：

1. 点击“刷新配置”查看当前 check。
2. 点击“新增”或“编辑”维护 check config。
3. 点击“诊断 checks”检查 SQL 渲染。
4. 填 `runId` 后点击“查询 check results”。

成功后看哪里：

- Check Diagnose 的 `renderable`、`missingVariables`、`actualValue`、`passed`。
- Check Result 的 `source_count`、`sink_count`、`error_count` 是否符合预期。

常见错误看哪里：

- SQL 缺变量看 `missingVariables`。
- datasource 配置错误看 `missingDatasourceIds` 或 `errorMessage`。
- 阻断校验失败看 `passed=false` 且 `failOnMismatch=true` 的记录。

### Audits

用途：查看 run 或 batch 的关键事件流水。

需要填写：

- `runId` 或 `batchId`。
- 可选 `startTime`、`endTime`、分页参数。

操作：

- 查询 run 事件点击“查询 run audits”。
- 查询 batch 事件点击“查询 batch audits”。
- 展开行查看 `detailJson`。

成功后看哪里：

- `RENDER_HOCON`
- `SUBMIT_JOB`
- `VERIFYING`
- `ADVANCE_WATERMARK`
- `RUN_FAILED`
- `MANUAL_UPDATE_WATERMARK`

常见错误看哪里：

- SeaTunnel 提交失败看 `SUBMIT_JOB` 附近事件。
- run failed 看 `RUN_FAILED` 的 `detailJson`。
- watermark 没推进看是否出现 `ADVANCE_WATERMARK`。

## 通用测试流程

1. 准备 StarRocks lab 表。
2. 进入增量同步测试台。
3. 在“模板创建”填充 `UPDATE_TIME_RANGE` 示例。
4. 创建任务。
5. 在“任务诊断”诊断任务。
6. 在“HOCON / Range”执行 Preview Range。
7. 在“HOCON / Range”执行 Diagnose HOCON。
8. 在“HOCON / Range”执行 Preview HOCON。
9. 在“运行任务”执行 Run。
10. 在 Runs / Batches 查看 run 和 batch。
11. 在 Checks / Audits 查看校验和审计。
12. 在 Watermark 查看 currentValue 是否推进。
13. 插入第二批数据，再次 Run。
14. 故意制造失败，验证 watermark 不推进。
15. 修复参数或配置后执行 rerun。

## 常见问题定位表

| 现象 | 应该看哪个 Tab | 可能原因 |
| --- | --- | --- |
| HOCON 缺变量 | HOCON / Range -> Diagnose HOCON | `params` 缺 `source_password` / `starrocks_password` |
| ID_RANGE 报 batchEndValue 缺失 | HOCON / Range -> Preview Range | `params` 没传 `batchEndValue` |
| SeaTunnel 提交失败 | Runs / Audits | `clientId`、Zeta 地址或认证配置问题 |
| Verification failed | Checks / Audits | `source_count` / `sink_count` / `error_count` 不匹配 |
| Watermark 没推进 | Watermark + Runs + Audits | run failed 或 check failed |
| StarRocks sink 失败 | Runs / Audits | 表不存在、`nodeUrls` 格式错误、账号密码错误 |
| 页面看不到下面内容 | 页面滚动问题 | 本轮修复后不应再出现 |

## 示例：使用 StarRocks lab 表模拟 JDBC/SQL -> StarRocks translator

### 目标链路

```text
lab_src_order
  -> SeaTunnel JDBC source
  -> SQL transform
  -> lab_sink_order / lab_sink_order_error
  -> watermark / batch / audit / check
```

这个例子使用 StarRocks 的 MySQL 协议作为 JDBC source，方便先验证通用增量 translator。真实 Oracle source 的操作流程相同，主要替换 JDBC URL、driver、账号、source SQL 和 datasourceId。

### 1. 在 StarRocks 执行建表和初始化数据

执行仓库中的 SQL 文件：

```text
docs/sql/generic_jdbc_sql_incremental_starrocks_lab.sql
```

该文件会创建：

```sql
CREATE TABLE IF NOT EXISTS lab_src_order (...);
CREATE TABLE IF NOT EXISTS lab_sink_order (...);
CREATE TABLE IF NOT EXISTS lab_sink_order_error (...);
```

初始数据示例：

```sql
INSERT INTO lab_src_order VALUES
(1, 'ORD_001', 10.50, '2026-06-01 00:01:00'),
(2, 'ORD_002', 20.00, '2026-06-01 00:02:00'),
(3, 'ORD_003', 30.00, '2026-06-01 00:03:00');
```

如果需要完全重跑测试，建议先清空 sink 表和相关 sync 元数据，或换新的 `taskCode`。

### 2. 创建 UPDATE_TIME_RANGE translator 任务

页面操作：

```text
模板创建 Tab
  -> 点击“填充 UPDATE_TIME_RANGE 示例”
  -> 修改 clientId / datasourceId / 用户名密码 / StarRocks 地址
  -> 点击“创建任务”
```

关键字段：

```text
taskCode = lab_order_update_time_sync
incrementalStrategy = UPDATE_TIME_RANGE
watermarkField = update_time
watermarkFieldType = DATETIME
startValue = 2026-06-01 00:00:00
maxBatchSeconds = 3600
sourceQuery 使用 batch_start_time / batch_end_time
```

示例 source query：

```sql
SELECT id, biz_no, amount, update_time
FROM lab_src_order
WHERE update_time >= '${batch_start_time}'
  AND update_time < '${batch_end_time}'
```

示例 StarRocks sink：

```text
starrocksDatabase = st_test
starrocksTable = lab_sink_order
starrocksErrorTable = lab_sink_order_error
```

### 3. 诊断任务

页面操作：

```text
任务诊断 Tab
  -> params 填入 source_username/source_password/starrocks_username/starrocks_password
  -> 点击“诊断”
```

params 示例：

```json
{
  "source_username": "st_lab",
  "source_password": "st_lab_pass",
  "starrocks_username": "st_lab",
  "starrocks_password": "st_lab_pass"
}
```

检查项：

```text
diagnostics 是否 OK
version 是否存在
incrementalConfig 是否存在
watermark.currentValue 是否等于 startValue
hocon 是否 renderable
missingVariables 是否为空
```

### 4. Preview Range

页面操作：

```text
HOCON / Range Tab
  -> Preview Range
```

预期：

```text
batch_start_time 从 watermark/startValue 来
batch_end_time 不超过 start + maxBatchSeconds
willAdvanceWatermark = true
```

使用示例参数时，第一批通常从 `2026-06-01 00:00:00` 开始，且 `maxBatchSeconds = 3600` 会把窗口限制在 1 小时内。

### 5. Diagnose HOCON / Preview HOCON

页面操作：

```text
HOCON / Range Tab
  -> Diagnose HOCON
  -> Preview HOCON
```

检查：

```text
source_query 中 batch_start_time / batch_end_time 已替换
StarRocks sink 表是 lab_sink_order
error 表是 lab_sink_order_error
```

如果 Diagnose HOCON 显示 `missingVariables`，先补齐 `params JSON` 后再运行。

### 6. Run

页面操作：

```text
运行任务 Tab
  -> 普通运行
  -> waitForFinish = true
  -> params 填 source/starrocks 用户名密码
  -> 点击运行任务
```

成功后看：

```text
返回 runId / batchId / status
Runs Tab 可查看 run
Batches Tab 可查看 batch
Checks Tab 可查看 source_count / sink_count / error_count
Audits Tab 可查看 RENDER_HOCON / SUBMIT_JOB / VERIFYING / ADVANCE_WATERMARK
Watermark Tab 可确认 currentValue 已推进
```

如果 check 配置启用，预期：

```text
source_count >= 0
sink_count == source_count
error_count == 0
```

### 7. 插入第二批数据再次运行

如果 lab SQL 已完整执行，第二批数据已经存在；也可以手动插入：

```sql
INSERT INTO lab_src_order VALUES
(4, 'ORD_004', 40.00, '2026-06-01 01:01:00'),
(5, 'ORD_005', 50.00, '2026-06-01 01:02:00');
```

再次 Run 后验证：

```text
新 batch 只同步第二批窗口内数据
watermark 再次推进
Runs / Batches 中出现新的 runId 和 batchId
Checks 仍然 passed
```

### 8. ID_RANGE 示例

页面操作：

```text
模板创建 Tab
  -> 点击“填充 ID_RANGE 示例”
  -> 创建任务 lab_order_id_range_sync
  -> 运行时 params 必须包含 batchEndValue
```

run params 示例：

```json
{
  "batchEndValue": "3",
  "source_username": "st_lab",
  "source_password": "st_lab_pass",
  "starrocks_username": "st_lab",
  "starrocks_password": "st_lab_pass"
}
```

预期：

```text
第一次同步 id > 0 and id <= 3
成功后 watermark.currentValue = 3
第二次传 batchEndValue = 5
同步 id > 3 and id <= 5
成功后 watermark.currentValue = 5
```

如果 Preview Range 或 Run 报 `batchEndValue` 缺失，说明 `params JSON` 没传 `batchEndValue` 或字段名拼错。

### 9. 失败不推进 watermark 示例

简单制造失败的方法：

```text
把 starrocksTable 临时改成不存在的表，或者运行 params 中故意不传 starrocks_password。
```

预期：

```text
run FAILED
batch FAILED
Audits 中有 RUN_FAILED
Watermark currentValue 不变
修正参数后执行 rerun
rerun 成功后 watermark 才推进
```

Rerun 操作：

```text
Runs Tab
  -> 找到失败 run
  -> 点击 rerun
运行任务 Tab
  -> 确认 mode = RERUN_SAME_RANGE
  -> 填入修正后的 params
  -> 点击 Rerun
```

Rerun 会复用原失败 batch 的范围，新建 run 和 batch 记录，不重新计算下一批范围。
