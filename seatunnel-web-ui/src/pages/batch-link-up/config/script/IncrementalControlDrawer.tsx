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
  Tooltip,
  message,
} from 'antd';
import {
  Braces,
  DatabaseZap,
  Eye,
  PlayCircle,
  RefreshCw,
  Save,
  TestTube2,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { batchLinkUpIncrementalApi } from '../../api';

const { TextArea } = Input;

const PLACEHOLDERS = [
  '$' + '{batch_id}',
  '$' + '{run_id}',
  '$' + '{task_id}',
  '$' + '{task_code}',
  '$' + '{watermark_key}',
  '$' + '{batch_start_value}',
  '$' + '{batch_end_value}',
  '$' + '{batch_start_time}',
  '$' + '{batch_end_time}',
  '$' + '{watermark_value}',
  '$' + '{watermark_time}',
  '$' + '{biz_date}',
];

const SYSTEM_VARIABLES = [
  { name: '$' + '{batch_id}', desc: '本次批次 ID，可用于 sink 批次标记和 cleanup_sql。' },
  { name: '$' + '{run_id}', desc: '本次运行 ID。' },
  { name: '$' + '{batch_start_value}', desc: '本批次起始边界。' },
  { name: '$' + '{batch_end_value}', desc: '本批次结束边界。' },
  { name: '$' + '{task_id}', desc: 'batch-link-up 任务 ID。' },
  { name: '$' + '{task_code}', desc: '系统生成任务编码。' },
  { name: '$' + '{watermark_key}', desc: 'watermark key，默认 default。' },
];

const RESERVED_CONTEXT_KEYS = [
  'batch_id',
  'run_id',
  'task_id',
  'task_code',
  'task_name',
  'batch_start_value',
  'batch_end_value',
  'batch_start_time',
  'batch_end_time',
  'watermark_value',
  'watermark_time',
  'watermark_key',
  'current_watermark',
  'previous_watermark',
  'last_success_batch_id',
  'last_success_run_id',
  'trigger_type',
  'scheduler_run_id',
  'seatunnel_job_id',
  'biz_date',
];

const sourceOptions = [
  { label: 'WATERMARK', value: 'WATERMARK' },
  { label: 'SQL', value: 'SQL' },
  { label: 'PARAM', value: 'PARAM' },
  { label: 'FIXED', value: 'FIXED' },
  { label: 'NOW', value: 'NOW' },
  { label: 'NONE', value: 'NONE' },
];

const compactInput = 'rounded-md';

const FORM_TO_API_FIELD_MAP: Record<string, string> = {
  range_type: 'rangeType',
  boundary_mode: 'boundaryMode',
  start_value_source: 'startValueSource',
  end_value_source: 'endValueSource',
  start_time_source: 'startTimeSource',
  end_time_source: 'endTimeSource',
  boundary_datasource_id: 'boundaryDatasourceId',
  check_datasource_id: 'checkDatasourceId',
  fixed_start_value: 'fixedStartValue',
  fixed_end_value: 'fixedEndValue',
  fixed_start_time: 'fixedStartTime',
  fixed_end_time: 'fixedEndTime',
  batch_prepare_sql: 'batchPrepareSql',
  batch_start_value_sql: 'batchStartValueSql',
  batch_end_value_sql: 'batchEndValueSql',
  batch_start_time_sql: 'batchStartTimeSql',
  batch_end_time_sql: 'batchEndTimeSql',
  default_params_json: 'defaultParamsJson',
  custom_context_json: 'customContextJson',
  cleanup_sql: 'cleanupSql',
  cleanup_datasource_id: 'cleanupDatasourceId',
  cleanup_on_rerun: 'cleanupOnRerun',
  cleanup_before_retry_only: 'cleanupBeforeRetryOnly',
  success_update_watermark: 'successUpdateWatermark',
  check_enabled: 'checkEnabled',
  check_sql: 'checkSql',
};

interface Props {
  taskId?: string | number;
  open: boolean;
  onClose: () => void;
  onInsertPlaceholder: (value: string) => void;
  scene?: string | null;
  releaseState?: string | number | null;
  readOnly?: boolean;
}

const jsonBlock = (value: any) => (
  <pre className="max-h-[420px] overflow-auto rounded-md border border-slate-200 bg-slate-950 p-3 text-xs leading-5 text-slate-100">
    {JSON.stringify(value || {}, null, 2)}
  </pre>
);

const toFormValues = (value: any) => {
  const result = { ...(value || {}) };
  Object.entries(FORM_TO_API_FIELD_MAP).forEach(([formKey, apiKey]) => {
    if (value?.[apiKey] !== undefined) {
      result[formKey] = value[apiKey];
    }
    delete result[apiKey];
  });
  return result;
};

const toApiPayload = (value: any) => {
  const result = { ...(value || {}) };
  Object.entries(FORM_TO_API_FIELD_MAP).forEach(([formKey, apiKey]) => {
    if (value?.[formKey] !== undefined) {
      result[apiKey] = value[formKey];
    }
    delete result[formKey];
  });
  return result;
};

const normalizeDatasourceId = (value: any) => {
  if (value === undefined || value === null) {
    return undefined;
  }
  const text = String(value).trim();
  return text ? text : undefined;
};

const isReleaseOnline = (releaseState?: string | number | null) => {
  if (releaseState === 1) {
    return true;
  }
  return String(releaseState || '').toUpperCase() === 'ONLINE';
};

const mergeUnique = (...values: any[]) => {
  const result: string[] = [];
  values.flat().forEach((item) => {
    if (item !== undefined && item !== null && !result.includes(String(item))) {
      result.push(String(item));
    }
  });
  return result;
};

const formatNullableCount = (value: any) =>
  value === undefined || value === null || value === '' ? '未获取' : value;

const validateJsonObjectField = (fieldName: string) => async (_: any, value: any) => {
  if (value === undefined || value === null || String(value).trim() === '') {
    return Promise.resolve();
  }
  let parsed: any;
  try {
    parsed = JSON.parse(String(value));
  } catch (_error) {
    return Promise.reject(new Error(`${fieldName} JSON 格式错误`));
  }
  if (
    typeof parsed !== 'object' ||
    parsed === null ||
    Array.isArray(parsed)
  ) {
    return Promise.reject(new Error(`${fieldName} 必须是 JSON object`));
  }
  const reservedKey = Object.keys(parsed).find(
    (key) => RESERVED_CONTEXT_KEYS.includes(key) || key.startsWith('custom.'),
  );
  if (reservedKey) {
    return Promise.reject(
      new Error(`${fieldName} contains reserved key: ${reservedKey}`),
    );
  }
  return Promise.resolve();
};

export default function IncrementalControlDrawer({
  taskId,
  open,
  onClose,
  onInsertPlaceholder,
  scene,
  releaseState,
  readOnly = false,
}: Props) {
  const [form] = Form.useForm();
  const enabledValue = Form.useWatch('enabled', form);
  const [activeTab, setActiveTab] = useState('config');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [running, setRunning] = useState(false);
  const [cleanupingRunId, setCleanupingRunId] = useState<string | null>(null);
  const [contextPreview, setContextPreview] = useState<any>(null);
  const [hoconPreview, setHoconPreview] = useState<any>(null);
  const [runs, setRuns] = useState<any[]>([]);
  const [batches, setBatches] = useState<any[]>([]);
  const [watermarks, setWatermarks] = useState<any[]>([]);
  const [resultOpen, setResultOpen] = useState(false);
  const [resultTitle, setResultTitle] = useState('');
  const [resultValue, setResultValue] = useState<any>(null);

  const normalizedTaskId =
    taskId === undefined || taskId === null || taskId === ''
      ? undefined
      : taskId;
  const canCallApi = normalizedTaskId !== undefined;
  const isEditScene = scene === 'edit';
  const isOnline = isReleaseOnline(releaseState);

  const previewMissingVariables = useMemo(
    () =>
      mergeUnique(
        contextPreview?.missingVariables || [],
        hoconPreview?.missingVariables || [],
      ),
    [contextPreview, hoconPreview],
  );
  const previewDiagnostics = useMemo(
    () =>
      mergeUnique(
        contextPreview?.diagnostics || [],
        hoconPreview?.diagnostics || [],
        hoconPreview?.warnings || [],
      ),
    [contextPreview, hoconPreview],
  );
  const previewExecutedSqls =
    contextPreview?.executedSqls || hoconPreview?.executedSqls || [];
  const runDisabledReason = (() => {
    if (isEditScene || !isOnline) {
      return '任务上线后才能手动增量运行。';
    }
    if (previewMissingVariables.length > 0) {
      return `存在未解析变量：${previewMissingVariables.join(
        ', ',
      )}，请配置来源或初始化 watermark 后再运行。`;
    }
    return '';
  })();
  const canRunIncremental =
    canCallApi &&
    !readOnly &&
    !isEditScene &&
    isOnline &&
    previewMissingVariables.length === 0;
  const incrementalEnabled = Boolean(enabledValue);

  const initialValues = useMemo(
    () => ({
      enabled: false,
      range_type: 'ID_RANGE',
      boundary_mode: 'SEPARATE_SQL',
      start_value_source: 'WATERMARK',
      end_value_source: 'SQL',
      start_time_source: 'NONE',
      end_time_source: 'NONE',
      success_update_watermark: true,
      check_enabled: false,
      cleanup_on_rerun: false,
      cleanup_before_retry_only: true,
    }),
    [],
  );

  const loadData = async () => {
    if (normalizedTaskId === undefined) return;
    const currentTaskId = normalizedTaskId;
    setLoading(true);
    try {
      const [configRes, watermarkRes, runsRes, batchesRes] = (await Promise.all(
        [
          batchLinkUpIncrementalApi.getConfig(currentTaskId),
          batchLinkUpIncrementalApi.getWatermark(currentTaskId),
          batchLinkUpIncrementalApi.listRuns(currentTaskId, {
            pageNo: 1,
            pageSize: 20,
          }),
          batchLinkUpIncrementalApi.listBatches(currentTaskId, {
            pageNo: 1,
            pageSize: 20,
          }),
        ],
      )) as any[];

      if (configRes?.code === 0) {
        form.setFieldsValue({
          ...initialValues,
          ...toFormValues(configRes.data),
        });
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
      message.error('加载增量控制配置失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) {
      form.setFieldsValue(initialValues);
      setActiveTab('config');
      loadData();
    }
  }, [open, taskId]);

  const saveConfig = async () => {
    if (normalizedTaskId === undefined) return;
    const currentTaskId = normalizedTaskId;
    setSaving(true);
    try {
      const values = await form.validateFields();
      const res = (await batchLinkUpIncrementalApi.saveConfig(
        currentTaskId,
        toApiPayload(values),
      )) as any;
      if (res?.code !== 0) {
        message.error(res?.message || '保存增量配置失败');
        return;
      }
      message.success('增量配置已保存');
      form.setFieldsValue(toFormValues(res.data));
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
      const res = (await batchLinkUpIncrementalApi.previewContext(
        currentTaskId,
        {},
      )) as any;
      if (res?.code !== 0) {
        message.error(res?.message || '预览增量上下文失败');
        return;
      }
      setContextPreview(res.data);
      setActiveTab('preview');
      message.success('Context 预览已生成');
    } finally {
      setPreviewing(false);
    }
  };

  const previewHocon = async () => {
    if (normalizedTaskId === undefined) return;
    const currentTaskId = normalizedTaskId;
    setPreviewing(true);
    try {
      const res = (await batchLinkUpIncrementalApi.previewHocon(
        currentTaskId,
        {},
      )) as any;
      if (res?.code !== 0) {
        message.error(res?.message || '预览 rendered HOCON 失败');
        return;
      }
      setHoconPreview(res.data);
      if (res.data?.context) {
        setContextPreview(res.data.context);
      }
      setActiveTab('preview');
      message.success('HOCON 预览已生成');
    } finally {
      setPreviewing(false);
    }
  };

  const runIncremental = async () => {
    if (normalizedTaskId === undefined) return;
    if (!canRunIncremental) {
      message.warning(runDisabledReason || '当前任务不能手动增量运行');
      return;
    }
    const currentTaskId = normalizedTaskId;
    setRunning(true);
    try {
      const res = (await batchLinkUpIncrementalApi.run(currentTaskId, {
        waitForFinish: true,
      })) as any;
      if (res?.code !== 0) {
        message.error(res?.message || '增量运行失败');
        return;
      }
      const failed =
        res.data?.errorMessage ||
        res.data?.status === 'FAILED' ||
        res.data?.runStatus === 'FAILED' ||
        res.data?.batchStatus === 'FAILED' ||
        res.data?.jobStatus === 'FAILED';
      if (res.data?.errorMessage) {
        if (res.data?.status === 'SKIPPED' || res.data?.runStatus === 'SKIPPED') {
          message.warning('当前任务已有增量运行正在执行，请稍后再试。');
        } else {
          message.error(res.data.errorMessage);
        }
      }
      setResultTitle(
        res.data?.status === 'SKIPPED' || res.data?.runStatus === 'SKIPPED'
          ? '增量运行已跳过'
          : failed
            ? '增量运行失败'
            : '增量运行结果',
      );
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
    if (!sql || !String(sql).trim()) {
      message.warning('请先填写 SQL');
      return;
    }
    const datasourceId =
      fieldName === 'check_sql'
        ? form.getFieldValue('check_datasource_id') ||
          form.getFieldValue('boundary_datasource_id')
        : form.getFieldValue('boundary_datasource_id');
    try {
      const res = (await batchLinkUpIncrementalApi.testSql(currentTaskId, {
        sql,
        scalar,
        datasourceId: normalizeDatasourceId(datasourceId),
        fieldName,
        sqlType: fieldName,
      })) as any;
      if (res?.code !== 0) {
        message.error(res?.message || 'SQL 测试失败');
        return;
      }
      setResultTitle(`SQL 测试结果：${fieldName}`);
      setResultValue(res.data);
      setResultOpen(true);
    } catch (_error) {
      message.error('SQL 测试失败');
    }
  };

  const copyCleanupSql = async (record: any) => {
    if (normalizedTaskId === undefined || !record?.runId) return;
    const res = (await batchLinkUpIncrementalApi.previewCleanupSql(
      normalizedTaskId,
      record.runId,
    )) as any;
    if (res?.code !== 0 || res.data?.success === false) {
      message.error(res?.message || res.data?.errorMessage || 'cleanup_sql 预览失败');
      return;
    }
    await navigator.clipboard?.writeText(res.data?.renderedSql || '');
    message.success('cleanup_sql 已复制');
  };

  const executeCleanupSql = (record: any) => {
    if (normalizedTaskId === undefined || !record?.runId) return;
    Modal.confirm({
      title: '确认执行 cleanup_sql？',
      content:
        '该操作会直接连接 cleanup_datasource_id 执行清理 SQL。请确认 SQL 只清理本次失败批次数据。',
      okText: '执行 cleanup_sql',
      cancelText: '取消',
      onOk: async () => {
        setCleanupingRunId(record.runId);
        try {
          const res = (await batchLinkUpIncrementalApi.executeCleanupSql(
            normalizedTaskId,
            record.runId,
          )) as any;
          if (res?.code !== 0 || res.data?.success === false) {
            message.error(
              res?.message || res.data?.errorMessage || 'cleanup_sql 执行失败',
            );
            return;
          }
          message.success('cleanup_sql 执行成功');
          setResultTitle('cleanup_sql 执行结果');
          setResultValue(res.data);
          setResultOpen(true);
          await loadData();
        } finally {
          setCleanupingRunId(null);
        }
      },
    });
  };

  const cleanupAndRerun = (record: any) => {
    if (normalizedTaskId === undefined || !record?.runId) return;
    Modal.confirm({
      title: '确认清理并重跑？',
      content:
        '系统会先执行 cleanup_sql。清理失败时不会继续重跑，watermark 也不会推进。',
      okText: '清理并重跑',
      cancelText: '取消',
      onOk: async () => {
        setCleanupingRunId(record.runId);
        try {
          const res = (await batchLinkUpIncrementalApi.cleanupAndRerun(
            normalizedTaskId,
            record.runId,
            { waitForFinish: true, runMode: 'RERUN' },
          )) as any;
          if (res?.code !== 0) {
            message.error(res?.message || '清理并重跑失败');
            return;
          }
          if (res.data?.errorMessage) {
            message.error(res.data.errorMessage);
          } else {
            message.success('清理并重跑完成');
          }
          setResultTitle(res.data?.errorMessage ? '清理并重跑失败' : '清理并重跑结果');
          setResultValue(res.data);
          setResultOpen(true);
          await loadData();
        } finally {
          setCleanupingRunId(null);
        }
      },
    });
  };

  const renderSqlItem = (name: string, label: string, scalar = false) => (
    <Form.Item label={label}>
      <Form.Item name={name} noStyle>
        <TextArea rows={4} className={compactInput} />
      </Form.Item>
      {!readOnly && (
        <Button
          size="small"
          className="mt-2"
          icon={<TestTube2 size={14} />}
          onClick={() => testSql(name, scalar)}
        >
          测试 SQL
        </Button>
      )}
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
            <Button
              icon={<RefreshCw size={14} />}
              loading={loading}
              onClick={loadData}
            >
              刷新
            </Button>
            <Button
              icon={<Eye size={14} />}
              loading={previewing}
              onClick={previewContext}
            >
              预览 Context
            </Button>
            <Button
              icon={<Eye size={14} />}
              loading={previewing}
              onClick={previewHocon}
            >
              预览 HOCON
            </Button>
            {!readOnly && (
              <>
                <Tooltip title={runDisabledReason}>
                  <span>
                    <Button
                      icon={<PlayCircle size={14} />}
                      loading={running}
                      disabled={!canRunIncremental}
                      onClick={runIncremental}
                    >
                      手动增量运行
                    </Button>
                  </span>
                </Tooltip>
                <Button
                  type="primary"
                  icon={<Save size={14} />}
                  loading={saving}
                  onClick={saveConfig}
                >
                  保存
                </Button>
              </>
            )}
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
        {readOnly && canCallApi && !incrementalEnabled && (
          <Alert
            type="info"
            showIcon
            className="mb-4"
            message="未启用增量控制"
          />
        )}

        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'config',
              label: '配置',
              children: (
                <Form
                  form={form}
                  layout="vertical"
                  initialValues={initialValues}
                  disabled={!canCallApi || loading || readOnly}
                >
                  <div className="grid grid-cols-2 gap-x-4">
                    <Form.Item
                      name="enabled"
                      label="启用增量"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="success_update_watermark"
                      label="成功后推进 watermark"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item name="range_type" label="range_type">
                      <Select
                        options={[
                          { label: 'ID_RANGE', value: 'ID_RANGE' },
                          {
                            label: 'UPDATE_TIME_RANGE',
                            value: 'UPDATE_TIME_RANGE',
                          },
                          { label: 'CUSTOM', value: 'CUSTOM' },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name="boundary_mode" label="boundary_mode">
                      <Select
                        options={[
                          { label: 'SEPARATE_SQL', value: 'SEPARATE_SQL' },
                          { label: 'PREPARE_SQL', value: 'PREPARE_SQL' },
                          {
                            label: 'SIMPLE_WATERMARK',
                            value: 'SIMPLE_WATERMARK',
                          },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item
                      name="boundary_datasource_id"
                      label="boundary_datasource_id"
                    >
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item
                      name="check_datasource_id"
                      label="check_datasource_id"
                    >
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item
                      name="start_value_source"
                      label="batch_start_value 来源"
                    >
                      <Select options={sourceOptions} />
                    </Form.Item>
                    <Form.Item
                      name="end_value_source"
                      label="batch_end_value 来源"
                    >
                      <Select
                        options={sourceOptions.filter(
                          (item) => item.value !== 'WATERMARK',
                        )}
                      />
                    </Form.Item>
                    <Form.Item
                      name="start_time_source"
                      label="batch_start_time 来源"
                    >
                      <Select options={sourceOptions} />
                    </Form.Item>
                    <Form.Item
                      name="end_time_source"
                      label="batch_end_time 来源"
                    >
                      <Select
                        options={sourceOptions.filter(
                          (item) => item.value !== 'WATERMARK',
                        )}
                      />
                    </Form.Item>
                    <Form.Item
                      name="fixed_start_value"
                      label="fixed_start_value"
                    >
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item name="fixed_end_value" label="fixed_end_value">
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item name="fixed_start_time" label="fixed_start_time">
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item name="fixed_end_time" label="fixed_end_time">
                      <Input className={compactInput} />
                    </Form.Item>
                  </div>

                  {renderSqlItem('batch_prepare_sql', 'batch_prepare_sql')}
                  {renderSqlItem(
                    'batch_start_value_sql',
                    'batch_start_value_sql',
                    true,
                  )}
                  {renderSqlItem(
                    'batch_end_value_sql',
                    'batch_end_value_sql',
                    true,
                  )}
                  {renderSqlItem(
                    'batch_start_time_sql',
                    'batch_start_time_sql',
                    true,
                  )}
                  {renderSqlItem(
                    'batch_end_time_sql',
                    'batch_end_time_sql',
                    true,
                  )}

                  <div className="grid grid-cols-2 gap-x-4">
                    <Form.Item
                      name="default_params_json"
                      label="默认运行参数 JSON"
                      extra={
                        <div className="text-xs leading-5 text-slate-500">
                          用于给 HOCON、boundary SQL、check_sql、cleanup_sql 中的普通业务占位符提供默认值。
                          示例：WHERE factory = <code>{'$' + '{factory}'}</code> AND biz_type ={' '}
                          <code>{'$' + '{biz_type}'}</code>
                        </div>
                      }
                      rules={[
                        {
                          validator: validateJsonObjectField(
                            'default_params_json',
                          ),
                        },
                      ]}
                    >
                      <TextArea
                        rows={6}
                        className={compactInput}
                        placeholder={'{\n  "factory": "FAB1",\n  "biz_type": "ORDER"\n}'}
                      />
                    </Form.Item>
                    <Form.Item
                      name="custom_context_json"
                      label="自定义占位符上下文 JSON"
                      extra={
                        <div className="text-xs leading-5 text-slate-500">
                          用于补充表名、系统名、目录、业务标签等高级变量。
                          示例：SELECT COUNT(*) FROM <code>{'$' + '{sink_table}'}</code> WHERE batch_id ={' '}
                          <code>{'$' + '{batch_id}'}</code>
                        </div>
                      }
                      rules={[
                        {
                          validator: validateJsonObjectField(
                            'custom_context_json',
                          ),
                        },
                      ]}
                    >
                      <TextArea
                        rows={6}
                        className={compactInput}
                        placeholder={'{\n  "sink_table": "lab_sink_order",\n  "source_system": "oracle"\n}'}
                      />
                    </Form.Item>
                  </div>

                  <Form.Item
                    name="check_enabled"
                    label="开启 check"
                    valuePropName="checked"
                  >
                    <Switch />
                  </Form.Item>
                  {renderSqlItem('check_sql', 'check_sql')}

                  <Alert
                    type="warning"
                    showIcon
                    className="mb-4"
                    message="失败后重跑策略"
                    description="如果 SeaTunnel 已写入目标端但 check_sql 失败，watermark 不会推进。重跑前请清理本批次数据，或确保目标表是 Primary Key / Unique Key / Upsert 幂等表。cleanup_sql 只会在历史详情中手动执行或清理并重跑时执行。"
                  />
                  <div className="grid grid-cols-2 gap-x-4">
                    <Form.Item
                      name="cleanup_datasource_id"
                      label="cleanup_datasource_id"
                    >
                      <Input className={compactInput} />
                    </Form.Item>
                    <Form.Item
                      name="cleanup_on_rerun"
                      label="失败重跑时允许 cleanup_sql"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="cleanup_before_retry_only"
                      label="仅失败批次重跑前清理"
                      valuePropName="checked"
                    >
                      <Switch />
                    </Form.Item>
                  </div>
                  <Form.Item
                    name="cleanup_sql"
                    label="cleanup_sql"
                    extra="示例：DELETE FROM lab_sink_order WHERE batch_id = '${batch_id}'。可用变量包括 batch_id、run_id、batch_start_value、batch_end_value、task_id、task_code、watermark_key 以及 JSON 参数。"
                  >
                    <TextArea
                      rows={4}
                      className={compactInput}
                      placeholder="DELETE FROM lab_sink_order WHERE batch_id = '${batch_id}'"
                    />
                  </Form.Item>
                </Form>
              ),
            },
            {
              key: 'placeholders',
              label: '占位符',
              children: (
                <div className="space-y-4">
                  <Alert
                    type="info"
                    showIcon
                    message="可用系统变量说明"
                    description="系统变量优先级最高，default_params_json 和 custom_context_json 不能覆盖这些 key。"
                  />
                  <Table
                    size="small"
                    rowKey="name"
                    dataSource={SYSTEM_VARIABLES}
                    pagination={false}
                    columns={[
                      { title: '变量', dataIndex: 'name' },
                      { title: '说明', dataIndex: 'desc' },
                    ]}
                  />
                  <Space size={[8, 8]} wrap>
                    {PLACEHOLDERS.map((item) => (
                      <Tag
                        key={item}
                        icon={<Braces size={12} />}
                        className={`px-3 py-1 text-sm ${
                          readOnly ? '' : 'cursor-pointer'
                        }`}
                        onClick={() => {
                          if (!readOnly) {
                            onInsertPlaceholder(item);
                          }
                        }}
                      >
                        {item}
                      </Tag>
                    ))}
                  </Space>
                </div>
              ),
            },
            {
              key: 'preview',
              label: '预览',
              children: (
                <div className="space-y-4">
                  {previewMissingVariables.length > 0 && (
                    <Alert
                      type="warning"
                      showIcon
                      message={`存在未解析变量：${previewMissingVariables.join(
                        ', ',
                      )}，请配置来源或初始化 watermark 后再运行。`}
                    />
                  )}
                  <div>
                    <div className="mb-2 font-medium text-slate-700">
                      Context 结果
                    </div>
                    {contextPreview ? (
                      <>
                        <Descriptions bordered size="small" column={2}>
                          <Descriptions.Item label="batch_id">
                            {contextPreview.batchId}
                          </Descriptions.Item>
                          <Descriptions.Item label="run_id">
                            {contextPreview.runId}
                          </Descriptions.Item>
                          <Descriptions.Item label="batch_start_value">
                            {contextPreview.batchStartValue}
                          </Descriptions.Item>
                          <Descriptions.Item label="batch_end_value">
                            {contextPreview.batchEndValue}
                          </Descriptions.Item>
                          <Descriptions.Item label="batch_start_time">
                            {contextPreview.batchStartTime}
                          </Descriptions.Item>
                          <Descriptions.Item label="batch_end_time">
                            {contextPreview.batchEndTime}
                          </Descriptions.Item>
                          <Descriptions.Item label="watermark_value">
                            {contextPreview.watermarkValue}
                          </Descriptions.Item>
                          <Descriptions.Item label="watermark_time">
                            {contextPreview.watermarkTime}
                          </Descriptions.Item>
                          <Descriptions.Item label="last_success_batch_id">
                            {contextPreview.lastSuccessBatchId}
                          </Descriptions.Item>
                          <Descriptions.Item label="last_success_run_id">
                            {contextPreview.lastSuccessRunId}
                          </Descriptions.Item>
                          <Descriptions.Item label="biz_date">
                            {contextPreview.bizDate}
                          </Descriptions.Item>
                        </Descriptions>
                        <div className="mt-3">
                          <div className="mb-2 text-sm font-medium text-slate-700">
                            systemVariables
                          </div>
                          <Space size={[6, 6]} wrap>
                            {(contextPreview.systemVariables || []).map(
                              (item: string) => (
                                <Tag key={item} color="blue">
                                  {item}
                                </Tag>
                              ),
                            )}
                          </Space>
                        </div>
                        <div className="mt-3">
                          {jsonBlock(
                            contextPreview.variables || contextPreview,
                          )}
                        </div>
                      </>
                    ) : (
                      <Alert
                        type="info"
                        showIcon
                        message="暂无 Context 预览结果"
                      />
                    )}
                  </div>
                  <div>
                    <div className="mb-2 font-medium text-slate-700">
                      Rendered HOCON
                    </div>
                    {hoconPreview ? (
                      <pre className="max-h-[520px] overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 text-xs">
                        {hoconPreview.renderedHocon || '存在缺失变量，无法渲染'}
                      </pre>
                    ) : (
                      <Alert
                        type="info"
                        showIcon
                        message="暂无 HOCON 预览结果"
                      />
                    )}
                  </div>
                  <div>
                    <div className="mb-2 font-medium text-slate-700">
                      executedSqls
                    </div>
                    {jsonBlock(previewExecutedSqls)}
                  </div>
                  <div>
                    <div className="mb-2 font-medium text-slate-700">
                      missingVariables
                    </div>
                    {jsonBlock(previewMissingVariables)}
                  </div>
                  <div>
                    <div className="mb-2 font-medium text-slate-700">
                      diagnostics
                    </div>
                    {jsonBlock(previewDiagnostics)}
                  </div>
                </div>
              ),
            },
            {
              key: 'history',
              label: '历史',
              children: (
                <div className="space-y-5">
                  <Table
                    size="small"
                    rowKey="watermarkKey"
                    title={() => 'Watermark'}
                    dataSource={watermarks}
                    pagination={false}
                    columns={[
                      { title: 'key', dataIndex: 'watermarkKey' },
                      { title: 'current', dataIndex: 'currentValue' },
                      { title: 'previous', dataIndex: 'previousValue' },
                      { title: 'updateTime', dataIndex: 'updateTime' },
                    ]}
                  />
                  <Table
                    size="small"
                    rowKey="batchId"
                    title={() => 'Batches'}
                    dataSource={batches}
                    pagination={false}
                    columns={[
                      { title: 'batchId', dataIndex: 'batchId' },
                      { title: 'status', dataIndex: 'status' },
                      { title: 'start', dataIndex: 'batchStartValue' },
                      { title: 'end', dataIndex: 'batchEndValue' },
                      {
                        title: 'source',
                        dataIndex: 'sourceCount',
                        render: formatNullableCount,
                      },
                      {
                        title: 'sink',
                        dataIndex: 'sinkCount',
                        render: formatNullableCount,
                      },
                      {
                        title: 'error',
                        dataIndex: 'errorCount',
                        render: formatNullableCount,
                      },
                      { title: 'createTime', dataIndex: 'createTime' },
                    ]}
                  />
                  <Table
                    size="small"
                    rowKey="runId"
                    title={() => 'Runs'}
                    dataSource={runs}
                    pagination={false}
                    columns={[
                      { title: 'runId', dataIndex: 'runId' },
                      { title: 'batchId', dataIndex: 'batchId' },
                      { title: 'status', dataIndex: 'status' },
                      { title: 'trigger', dataIndex: 'triggerType' },
                      { title: 'jobId', dataIndex: 'seatunnelJobId' },
                      {
                        title: 'mayWrite',
                        dataIndex: 'targetMayHaveWritten',
                        render: (value: boolean) =>
                          value ? <Tag color="orange">可能已写入</Tag> : '-',
                      },
                      {
                        title: 'source',
                        dataIndex: 'sourceCount',
                        render: formatNullableCount,
                      },
                      {
                        title: 'sink',
                        dataIndex: 'sinkCount',
                        render: formatNullableCount,
                      },
                      {
                        title: 'error',
                        dataIndex: 'errorCount',
                        render: formatNullableCount,
                      },
                      { title: 'endTime', dataIndex: 'endTime' },
                    ]}
                    expandable={{
                      expandedRowRender: (record: any) => (
                        <div className="space-y-3">
                          {record.retryRequiresCleanup ? (
                            <Alert
                              type="warning"
                              showIcon
                              message="目标端可能已经写入，但 watermark 未推进"
                              description={
                                record.cleanupHint ||
                                '重跑前请清理目标端本批次数据，或使用幂等 sink 表。'
                              }
                            />
                          ) : null}
                          {record.errorMessage ? (
                            <Alert
                              type="error"
                              showIcon
                              message={record.errorMessage}
                            />
                          ) : null}
                          <Descriptions bordered size="small" column={2}>
                            <Descriptions.Item label="run_id">
                              {record.runId || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="batch_id">
                              {record.batchId || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="batch_start_value">
                              {record.batchStartValue || '-'}
                            </Descriptions.Item>
                            <Descriptions.Item label="batch_end_value">
                              {record.batchEndValue || '-'}
                            </Descriptions.Item>
                          </Descriptions>
                          {record.retryRequiresCleanup && !readOnly ? (
                            <Space>
                              <Button
                                size="small"
                                loading={cleanupingRunId === record.runId}
                                onClick={() => copyCleanupSql(record)}
                              >
                                复制 cleanup_sql
                              </Button>
                              <Button
                                size="small"
                                loading={cleanupingRunId === record.runId}
                                onClick={() => executeCleanupSql(record)}
                              >
                                执行 cleanup_sql
                              </Button>
                              <Button
                                size="small"
                                type="primary"
                                loading={cleanupingRunId === record.runId}
                                onClick={() => cleanupAndRerun(record)}
                              >
                                清理并重跑
                              </Button>
                            </Space>
                          ) : null}
                          {jsonBlock(record)}
                          <pre className="max-h-[360px] overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 text-xs">
                            {record.generatedHocon || '未生成'}
                          </pre>
                        </div>
                      ),
                    }}
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
        {resultValue?.errorMessage ? (
          <Alert
            type="error"
            showIcon
            className="mb-3"
            message={resultValue.errorMessage}
          />
        ) : null}
        {jsonBlock(resultValue)}
      </Modal>
    </>
  );
}
