import { Alert, Button, Card, Descriptions, Modal, Space, Table, Tag, message } from "antd";
import React, { useState } from "react";
import { batchLinkUpIncrementalApi } from "../api";

interface IncrementalRunTabProps {
  item: any;
  loading?: boolean;
}

const formatCount = (value: any) =>
  value === undefined || value === null || value === "" ? "未获取" : value;

const tryParseJson = (value: any) => {
  if (!value || typeof value !== "string") {
    return value;
  }
  try {
    return JSON.parse(value);
  } catch (_error) {
    return value;
  }
};

const jsonBlock = (value: any) => (
  <pre className="max-h-[360px] overflow-auto rounded-md border border-slate-200 bg-slate-950 p-3 text-xs leading-5 text-slate-100">
    {JSON.stringify(value ?? {}, null, 2)}
  </pre>
);

const IncrementalRunTab: React.FC<IncrementalRunTabProps> = ({
  item,
  loading,
}) => {
  const checkResults = item?.checkResults || [];
  const [cleanuping, setCleanuping] = useState(false);

  const copyCleanupSql = async () => {
    if (!item?.taskId || !item?.runId) return;
    const res = (await batchLinkUpIncrementalApi.previewCleanupSql(
      item.taskId,
      item.runId,
    )) as any;
    if (res?.code !== 0 || res.data?.success === false) {
      message.error(res?.message || res.data?.errorMessage || "cleanup_sql 预览失败");
      return;
    }
    await navigator.clipboard?.writeText(res.data?.renderedSql || "");
    message.success("cleanup_sql 已复制");
  };

  const executeCleanupSql = () => {
    if (!item?.taskId || !item?.runId) return;
    Modal.confirm({
      title: "确认执行 cleanup_sql？",
      content: "请确认 SQL 只清理本次失败批次数据。",
      okText: "执行 cleanup_sql",
      cancelText: "取消",
      onOk: async () => {
        setCleanuping(true);
        try {
          const res = (await batchLinkUpIncrementalApi.executeCleanupSql(
            item.taskId,
            item.runId,
          )) as any;
          if (res?.code !== 0 || res.data?.success === false) {
            message.error(
              res?.message || res.data?.errorMessage || "cleanup_sql 执行失败",
            );
            return;
          }
          message.success("cleanup_sql 执行成功");
        } finally {
          setCleanuping(false);
        }
      },
    });
  };

  const cleanupAndRerun = () => {
    if (!item?.taskId || !item?.runId) return;
    Modal.confirm({
      title: "确认清理并重跑？",
      content: "cleanup_sql 执行失败时不会继续重跑，watermark 也不会推进。",
      okText: "清理并重跑",
      cancelText: "取消",
      onOk: async () => {
        setCleanuping(true);
        try {
          const res = (await batchLinkUpIncrementalApi.cleanupAndRerun(
            item.taskId,
            item.runId,
            { waitForFinish: true, runMode: "RERUN" },
          )) as any;
          if (res?.code !== 0 || res.data?.errorMessage) {
            message.error(res?.message || res.data?.errorMessage || "清理并重跑失败");
            return;
          }
          message.success("清理并重跑完成");
        } finally {
          setCleanuping(false);
        }
      },
    });
  };

  return (
    <Card
      size="small"
      loading={loading}
      className="mt-2 !rounded-2xl !border-slate-200 !shadow-[0_1px_3px_rgba(15,23,42,0.04)]"
      bodyStyle={{ padding: 16, marginBottom: 116 }}
    >
      {item?.errorMessage ? (
        <Alert
          type="error"
          showIcon
          className="mb-4"
          message={item.errorMessage}
        />
      ) : null}
      {item?.retryRequiresCleanup ? (
        <Alert
          type="warning"
          showIcon
          className="mb-4"
          message="目标端可能已经写入，但 watermark 未推进"
          description={
            item.cleanupHint ||
            "重跑前请清理目标端本批次数据，或使用幂等 sink 表。"
          }
          action={
            <Space>
              <Button size="small" loading={cleanuping} onClick={copyCleanupSql}>
                复制 cleanup_sql
              </Button>
              <Button size="small" loading={cleanuping} onClick={executeCleanupSql}>
                执行 cleanup_sql
              </Button>
              <Button size="small" type="primary" loading={cleanuping} onClick={cleanupAndRerun}>
                清理并重跑
              </Button>
            </Space>
          }
        />
      ) : null}

      <Descriptions bordered size="small" column={2}>
        <Descriptions.Item label="run_type">
          <Tag>{item?.runType || "INCREMENTAL"}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="trigger_type">
          <Tag color="blue">{item?.triggerType || "-"}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="run_id">{item?.runId || "-"}</Descriptions.Item>
        <Descriptions.Item label="batch_id">
          {item?.batchId || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="scheduler_run_id">
          {item?.schedulerRunId || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="seatunnel_job_id">
          {item?.seatunnelJobId || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="run_status">
          {item?.runStatus || item?.status || item?.jobStatus || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="batch_status">
          {item?.batchStatus || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="batch_start_value">
          {item?.batchStartValue || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="batch_end_value">
          {item?.batchEndValue || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="batch_start_time">
          {item?.batchStartTime || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="batch_end_time">
          {item?.batchEndTime || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="source_count">
          {formatCount(item?.sourceCount)}
        </Descriptions.Item>
        <Descriptions.Item label="sink_count">
          {formatCount(item?.sinkCount)}
        </Descriptions.Item>
        <Descriptions.Item label="error_count">
          {formatCount(item?.errorCount)}
        </Descriptions.Item>
        <Descriptions.Item label="watermark">
          {item?.watermarkValue || "-"}
        </Descriptions.Item>
        <Descriptions.Item label="target_may_have_written">
          {item?.targetMayHaveWritten ? (
            <Tag color="orange">可能已写入</Tag>
          ) : (
            "-"
          )}
        </Descriptions.Item>
        <Descriptions.Item label="retry_requires_cleanup">
          {item?.retryRequiresCleanup ? (
            <Tag color="warning">需要清理或幂等重跑</Tag>
          ) : (
            "-"
          )}
        </Descriptions.Item>
      </Descriptions>

      <div className="mt-5">
        <div className="mb-2 text-sm font-semibold text-slate-900">
          check_sql
        </div>
        <Table
          size="small"
          rowKey={(record: any) => record.id || record.checkCode}
          dataSource={checkResults}
          pagination={false}
          columns={[
            { title: "checkCode", dataIndex: "checkCode" },
            {
              title: "passed",
              dataIndex: "passed",
              render: (value: boolean) => (
                <Tag color={value ? "success" : "error"}>{String(value)}</Tag>
              ),
            },
            { title: "actualValue", dataIndex: "actualValue" },
            { title: "message", dataIndex: "errorMessage" },
            { title: "endTime", dataIndex: "endTime" },
          ]}
          expandable={{
            expandedRowRender: (record: any) => (
              <div className="space-y-3">
                <pre className="max-h-[240px] overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 text-xs">
                  {record.renderedSql || "-"}
                </pre>
                {jsonBlock(tryParseJson(record.actualValue))}
              </div>
            ),
          }}
        />
      </div>
    </Card>
  );
};

export default IncrementalRunTab;
