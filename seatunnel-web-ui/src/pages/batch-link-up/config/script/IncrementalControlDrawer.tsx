import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  message,
} from "antd";
import { Braces, DatabaseZap, Eye, PlayCircle, Save, TestTube2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { batchLinkUpIncrementalApi } from "../../api";

const { TextArea } = Input;

const PLACEHOLDERS = [
  "$" + "{batch_id}",
  "$" + "{run_id}",
  "$" + "{task_code}",
  "$" + "{batch_start_value}",
  "$" + "{batch_end_value}",
  "$" + "{batch_start_time}",
  "$" + "{batch_end_time}",
  "$" + "{watermark_value}",
  "$" + "{watermark_time}",
  "$" + "{biz_date}",
];

const sourceOptions = [
  { label: "WATERMARK", value: "WATERMARK" },
  { label: "SQL", value: "SQL" },
  { label: "PARAM", value: "PARAM" },
  { label: "FIXED", value: "FIXED" },
  { label: "NOW", value: "NOW" },
  { label: "NONE", value: "NONE" },
];

const compactInput = "rounded-md";

interface Props {
  taskId?: string | number;
  open: boolean;
  onClose: () => void;
  onInsertPlaceholder: (value: string) => void;
}

const jsonBlock = (value: any) => (
  <pre className="max-h-[420px] overflow-auto rounded-md border border-slate-200 bg-slate-950 p-3 text-xs leading-5 text-slate-100">
    {JSON.stringify(value || {}, null, 2)}
  </pre>
);

export default function IncrementalControlDrawer({
  taskId,
  open,
  onClose,
  onInsertPlaceholder,
}: Props) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [running, setRunning] = useState(false);
  const [contextPreview, setContextPreview] = useState<any>(null);
  const [hoconPreview, setHoconPreview] = useState<any>(null);
  const [runs, setRuns] = useState<any[]>([]);
  const [batches, setBatches] = useState<any[]>([]);
  const [watermarks, setWatermarks] = useState<any[]>([]);
  const [resultOpen, setResultOpen] = useState(false);
  const [resultTitle, setResultTitle] = useState("");
  const [resultValue, setResultValue] = useState<any>(null);

  const normalizedTaskId =
    taskId === undefined || taskId === null || taskId === "" ? undefined : taskId;
  const canCallApi = normalizedTaskId !== undefined;

  const initialValues = useMemo(
    () => ({
      enabled: false,
      rangeType: "ID_RANGE",
      boundaryMode: "SEPARATE_SQL",
      startValueSource: "WATERMARK",
      endValueSource: "SQL",
      startTimeSource: "NONE",
      endTimeSource: "NONE",
      successUpdateWatermark: true,
      checkEnabled: false,
    }),
    []
  );

  const loadData = async () => {
    if (normalizedTaskId === undefined) return;
    const currentTaskId = normalizedTaskId;
    setLoading(true);
    try {
      const [configRes, watermarkRes, runsRes, batchesRes] = (await Promise.all([
        batchLinkUpIncrementalApi.getConfig(currentTaskId),
        batchLinkUpIncrementalApi.getWatermark(currentTaskId),
        batchLinkUpIncrementalApi.listRuns(currentTaskId, { pageNo: 1, pageSize: 20 }),
        batchLinkUpIncrementalApi.listBatches(currentTaskId, { pageNo: 1, pageSize: 20 }),
      ])) as any[];

      if (configRes?.code === 0) {
        form.setFieldsValue({ ...initialValues, ...(configRes.data || {}) });
      }
      if (watermarkRes?.code === 0) {
        setWatermarks((watermarkRes.data as any[]) || []);
      }
      if (runsRes?.code === 0) {
        setRuns(runsRes?.data?.bizData || []);
      }
      if (batchesRes?.code === 0) {
        setBatches(batchesRes?.data?.bizData || []);
      }
    } catch (_error) {
      message.error("加载增量控制配置失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) {
      form.setFieldsValue(initialValues);
      loadData();
    }
  }, [open, taskId]);

  const saveConfig = async () => {
    if (normalizedTaskId === undefined) return;
    const currentTaskId = normalizedTaskId;
    setSaving(true);
    try {
      const values = await form.validateFields();
      const res = (await batchLinkUpIncrementalApi.saveConfig(currentTaskId, values)) as any;
      if (res?.code !== 0) {
        message.error(res?.message || "保存增量配置失败");
        return;
      }
      message.success("增量配置已保存");
      form.setFieldsValue(res.data || {});
      await loadData();
    } catch (error) {
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  const previewContext = async () => {
    if (normalizedTaskId === undefined) return;
    const currentTaskId = normalizedTaskId;
    setPreviewing(true);
    try {
      const res = (await batchLinkUpIncrementalApi.previewContext(currentTaskId, {})) as any;
      if (res?.code !== 0) {
        message.error(res?.message || "预览增量上下文失败");
        return;
      }
      setContextPreview(res.data);
    } finally {
      setPreviewing(false);
    }
  };

  const previewHocon = async () => {
    if (normalizedTaskId === undefined) return;
    const currentTaskId = normalizedTaskId;
    setPreviewing(true);
    try {
      const res = (await batchLinkUpIncrementalApi.previewHocon(currentTaskId, {})) as any;
      if (res?.code !== 0) {
        message.error(res?.message || "预览 rendered HOCON 失败");
        return;
      }
      setHoconPreview(res.data);
    } finally {
      setPreviewing(false);
    }
  };

  const runIncremental = async () => {
    if (normalizedTaskId === undefined) return;
    const currentTaskId = normalizedTaskId;
    setRunning(true);
    try {
      const res = (await batchLinkUpIncrementalApi.run(currentTaskId, {
        waitForFinish: true,
      })) as any;
      if (res?.code !== 0) {
        message.error(res?.message || "增量运行失败");
        return;
      }
      setResultTitle("增量运行结果");
      setResultValue(res.data);
      setResultOpen(true);
      await loadData();
    } finally {
      setRunning(false);
    }
  };

  const testSql = async (fieldName: string, scalar = false) => {
    if (normalizedTaskId === undefined) return;
    const currentTaskId = normalizedTaskId;
    const sql = form.getFieldValue(fieldName);
    if (!sql?.trim()) {
      message.warning("请先填写 SQL");
      return;
    }
    try {
      const res = (await batchLinkUpIncrementalApi.testSql(currentTaskId, {
        sql,
        scalar,
        datasourceId: form.getFieldValue("boundaryDatasourceId"),
      })) as any;
      if (res?.code !== 0) {
        message.error(res?.message || "SQL 测试失败");
        return;
      }
      setResultTitle(`SQL 测试结果：${fieldName}`);
      setResultValue(res.data);
      setResultOpen(true);
    } catch (_error) {
      message.error("SQL 测试失败");
    }
  };

  const renderSqlItem = (name: string, label: string, scalar = false) => (
    <Form.Item name={name} label={label}>
      <TextArea rows={4} className={compactInput} />
      <Button
        size="small"
        className="mt-2"
        icon={<TestTube2 size={14} />}
        onClick={() => testSql(name, scalar)}
      >
        测试 SQL
      </Button>
    </Form.Item>
  );

  return (
    <>
      <Drawer
        title={
          <Space>
            <DatabaseZap size={18} />
            增量控制
          </Space>
        }
        width={860}
        open={open}
        onClose={onClose}
        destroyOnClose={false}
        extra={
          <Space>
            <Button icon={<Eye size={14} />} loading={previewing} onClick={previewContext}>
              预览 context
            </Button>
            <Button icon={<Eye size={14} />} loading={previewing} onClick={previewHocon}>
              预览 HOCON
            </Button>
            <Button icon={<PlayCircle size={14} />} loading={running} onClick={runIncremental}>
              手动增量运行
            </Button>
            <Button type="primary" icon={<Save size={14} />} loading={saving} onClick={saveConfig}>
              保存
            </Button>
          </Space>
        }
      >
        {!canCallApi && (
          <Alert
            type="warning"
            showIcon
            className="mb-4"
            message="当前任务尚未保存，保存 batch-link-up 任务后可配置增量控制。"
          />
        )}

        <Tabs
          items={[
            {
              key: "config",
              label: "配置",
              children: (
                <Form
                  form={form}
                  layout="vertical"
                  initialValues={initialValues}
                  disabled={!canCallApi || loading}
                >
                  <div className="grid grid-cols-2 gap-x-4">
                    <Form.Item name="enabled" label="启用增量" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item name="successUpdateWatermark" label="成功后推进 watermark" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item name="rangeType" label="range_type">
                      <Select
                        options={[
                          { label: "ID_RANGE", value: "ID_RANGE" },
                          { label: "UPDATE_TIME_RANGE", value: "UPDATE_TIME_RANGE" },
                          { label: "CUSTOM", value: "CUSTOM" },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name="boundaryMode" label="boundary_mode">
                      <Select
                        options={[
                          { label: "SEPARATE_SQL", value: "SEPARATE_SQL" },
                          { label: "PREPARE_SQL", value: "PREPARE_SQL" },
                          { label: "SIMPLE_WATERMARK", value: "SIMPLE_WATERMARK" },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name="boundaryDatasourceId" label="boundary_datasource_id">
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item name="checkDatasourceId" label="check_datasource_id">
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item name="startValueSource" label="batch_start_value 来源">
                      <Select options={sourceOptions} />
                    </Form.Item>
                    <Form.Item name="endValueSource" label="batch_end_value 来源">
                      <Select options={sourceOptions.filter((item) => item.value !== "WATERMARK")} />
                    </Form.Item>
                    <Form.Item name="startTimeSource" label="batch_start_time 来源">
                      <Select options={sourceOptions} />
                    </Form.Item>
                    <Form.Item name="endTimeSource" label="batch_end_time 来源">
                      <Select options={sourceOptions.filter((item) => item.value !== "WATERMARK")} />
                    </Form.Item>
                    <Form.Item name="fixedStartValue" label="fixed_start_value">
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item name="fixedEndValue" label="fixed_end_value">
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item name="fixedStartTime" label="fixed_start_time">
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item name="fixedEndTime" label="fixed_end_time">
                      <Input className={compactInput} />
                    </Form.Item>
                  </div>

                  {renderSqlItem("batchPrepareSql", "batch_prepare_sql")}
                  {renderSqlItem("batchStartValueSql", "batch_start_value_sql", true)}
                  {renderSqlItem("batchEndValueSql", "batch_end_value_sql", true)}
                  {renderSqlItem("batchStartTimeSql", "batch_start_time_sql", true)}
                  {renderSqlItem("batchEndTimeSql", "batch_end_time_sql", true)}

                  <div className="grid grid-cols-2 gap-x-4">
                    <Form.Item name="defaultParamsJson" label="default_params_json">
                      <TextArea rows={5} className={compactInput} />
                    </Form.Item>
                    <Form.Item name="customContextJson" label="custom_context_json">
                      <TextArea rows={5} className={compactInput} />
                    </Form.Item>
                  </div>

                  <Form.Item name="checkEnabled" label="开启 check" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="checkSql" label="check SQL">
                    <TextArea rows={5} className={compactInput} />
                  </Form.Item>
                </Form>
              ),
            },
            {
              key: "placeholders",
              label: "占位符",
              children: (
                <Space size={[8, 8]} wrap>
                  {PLACEHOLDERS.map((item) => (
                    <Tag
                      key={item}
                      icon={<Braces size={12} />}
                      className="cursor-pointer px-3 py-1 text-sm"
                      onClick={() => onInsertPlaceholder(item)}
                    >
                      {item}
                    </Tag>
                  ))}
                </Space>
              ),
            },
            {
              key: "preview",
              label: "预览",
              children: (
                <div className="space-y-4">
                  {contextPreview && (
                    <Descriptions bordered size="small" column={2}>
                      <Descriptions.Item label="batch_id">{contextPreview.batchId}</Descriptions.Item>
                      <Descriptions.Item label="run_id">{contextPreview.runId}</Descriptions.Item>
                      <Descriptions.Item label="batch_start_value">{contextPreview.batchStartValue}</Descriptions.Item>
                      <Descriptions.Item label="batch_end_value">{contextPreview.batchEndValue}</Descriptions.Item>
                      <Descriptions.Item label="watermark_value">{contextPreview.watermarkValue}</Descriptions.Item>
                      <Descriptions.Item label="biz_date">{contextPreview.bizDate}</Descriptions.Item>
                    </Descriptions>
                  )}
                  {contextPreview?.missingVariables?.length > 0 && (
                    <Alert
                      type="warning"
                      showIcon
                      message={`缺失变量：${contextPreview.missingVariables.join(", ")}`}
                    />
                  )}
                  {contextPreview && jsonBlock(contextPreview)}
                  {hoconPreview && (
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <div className="mb-2 font-medium text-slate-700">替换前</div>
                        <pre className="max-h-[520px] overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 text-xs">
                          {hoconPreview.originalHocon}
                        </pre>
                      </div>
                      <div>
                        <div className="mb-2 font-medium text-slate-700">替换后</div>
                        <pre className="max-h-[520px] overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 text-xs">
                          {hoconPreview.renderedHocon || "存在缺失变量，无法渲染"}
                        </pre>
                      </div>
                    </div>
                  )}
                </div>
              ),
            },
            {
              key: "history",
              label: "历史",
              children: (
                <div className="space-y-5">
                  <Table
                    size="small"
                    rowKey="watermarkKey"
                    title={() => "Watermark"}
                    dataSource={watermarks}
                    pagination={false}
                    columns={[
                      { title: "key", dataIndex: "watermarkKey" },
                      { title: "current", dataIndex: "currentValue" },
                      { title: "previous", dataIndex: "previousValue" },
                      { title: "updateTime", dataIndex: "updateTime" },
                    ]}
                  />
                  <Table
                    size="small"
                    rowKey="batchId"
                    title={() => "Batches"}
                    dataSource={batches}
                    pagination={false}
                    columns={[
                      { title: "batchId", dataIndex: "batchId" },
                      { title: "status", dataIndex: "status" },
                      { title: "start", dataIndex: "batchStartValue" },
                      { title: "end", dataIndex: "batchEndValue" },
                      { title: "createTime", dataIndex: "createTime" },
                    ]}
                  />
                  <Table
                    size="small"
                    rowKey="runId"
                    title={() => "Runs"}
                    dataSource={runs}
                    pagination={false}
                    columns={[
                      { title: "runId", dataIndex: "runId" },
                      { title: "batchId", dataIndex: "batchId" },
                      { title: "status", dataIndex: "status" },
                      { title: "jobId", dataIndex: "seatunnelJobId" },
                      { title: "endTime", dataIndex: "endTime" },
                    ]}
                  />
                </div>
              ),
            },
          ]}
        />
      </Drawer>

      <Modal
        title={resultTitle}
        open={resultOpen}
        onCancel={() => setResultOpen(false)}
        footer={null}
        width={760}
      >
        {jsonBlock(resultValue)}
      </Modal>
    </>
  );
}
