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

## 后续阶段

后续阶段建议按以下顺序扩展：

- `HoconRenderService`：根据任务版本、运行参数和增量边界渲染 HOCON。
- `WatermarkService`：读取 watermark，计算 batch range，处理 lookback 和 max batch 约束。
- `SyncRunService`：提交 SeaTunnel Zeta，记录 engine job id，并轮询运行状态。
- 成功后推进 watermark，失败不推进。
- LocalFile/FtpFile manifest 扫描和文件状态流转。
- 补数和重跑语义，包括补数是否推进 watermark。
