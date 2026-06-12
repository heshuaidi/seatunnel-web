import {
  CopyOutlined,
  DatabaseOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Collapse,
  Descriptions,
  Divider,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import React, { useState } from 'react';
import { syncControlApi } from './api';
import './index.less';
import type {
  CreateGenericJdbcStarRocksTaskRequest,
  CreateTaskFromTemplateResult,
  HoconPreviewVO,
  RunResultVO,
  SyncAuditVO,
  SyncBatchVO,
  SyncCheckConfigVO,
  SyncCheckDiagnosticItem,
  SyncCheckDiagnosticVO,
  SyncCheckResultVO,
  SyncHoconDiagnosticVO,
  SyncRangePreviewVO,
  SyncRunVO,
  SyncTaskDiagnosticVO,
  SyncTemplate,
  SyncWatermarkVO,
} from './types';

const { TextArea } = Input;
const { Text } = Typography;

const DEFAULT_PARAMS = `{
  "source_username": "st_lab",
  "source_password": "st_lab_pass",
  "starrocks_username": "st_lab",
  "starrocks_password": "st_lab_pass"
}`;

const ID_RANGE_PARAMS = `{
  "batchEndValue": "100",
  "source_username": "st_lab",
  "source_password": "st_lab_pass",
  "starrocks_username": "st_lab",
  "starrocks_password": "st_lab_pass"
}`;

const PAGE_SIZE = 10;
const variable = (name: string) => `\${${name}}`;

const compact = (value?: string | number | boolean | null) => {
  if (value === undefined || value === null || value === '') return '-';
  return String(value);
};

const parseJsonObject = (text?: string): Record<string, any> | undefined => {
  if (!text?.trim()) return {};
  try {
    const parsed = JSON.parse(text);
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      message.error('JSON 格式错误：必须是对象');
      return undefined;
    }
    return parsed;
  } catch (error: any) {
    message.error(`JSON 格式错误：${error?.message || '无法解析'}`);
    return undefined;
  }
};

const stringify = (value: any) => JSON.stringify(value ?? {}, null, 2);

const getErrorMessage = (error: any) => {
  return error?.message || error?.response?.msg || error?.response?.message || '请求失败';
};

const statusTag = (status?: string) => {
  const value = status || '-';
  const colorMap: Record<string, string> = {
    OK: 'success',
    SUCCESS: 'success',
    PASSED: 'success',
    WARN: 'warning',
    WARNING: 'warning',
    ERROR: 'error',
    FAILED: 'error',
    FAIL: 'error',
    RUNNING: 'processing',
    SUBMITTED: 'processing',
    VERIFYING: 'processing',
    CREATED: 'default',
  };
  return <Tag color={colorMap[value] || 'default'}>{value}</Tag>;
};

const boolTag = (value?: boolean) => {
  if (value === undefined || value === null) return <Tag>-</Tag>;
  return <Tag color={value ? 'success' : 'default'}>{value ? 'true' : 'false'}</Tag>;
};

const ellipsisText = (value?: string | number | null, width = 260) => {
  return (
    <Tooltip title={compact(value)}>
      <Text style={{ maxWidth: width }} ellipsis>
        {compact(value)}
      </Text>
    </Tooltip>
  );
};

const CodeBlock: React.FC<{ value?: string; emptyText?: string }> = ({
  value,
  emptyText = '-',
}) => {
  return <pre className="code-block">{value || emptyText}</pre>;
};

const JsonBlock: React.FC<{ value?: any }> = ({ value }) => {
  return <CodeBlock value={typeof value === 'string' ? value : stringify(value)} />;
};

const ResultGrid: React.FC<{ data?: Record<string, any> }> = ({ data }) => {
  const entries = Object.entries(data || {});
  if (!entries.length) return <Alert type="info" showIcon message="暂无数据" />;
  return (
    <div className="result-grid">
      {entries.map(([key, value]) => (
        <div className="result-card" key={key}>
          <div className="label">{key}</div>
          <div className="value">
            {typeof value === 'boolean'
              ? boolTag(value)
              : Array.isArray(value)
                ? value.join(', ') || '-'
                : compact(value)}
          </div>
        </div>
      ))}
    </div>
  );
};

const copyText = async (text?: string) => {
  if (!text) {
    message.warning('没有可复制内容');
    return;
  }
  if (!navigator?.clipboard) {
    message.error('当前浏览器不支持复制 API');
    return;
  }
  await navigator.clipboard.writeText(text);
  message.success('已复制');
};

const getPagination = <T,>(response: any) => {
  const data = response?.data;
  return {
    rows: (data?.bizData || []) as T[],
    total: Number(data?.pagination?.total || 0),
    pageNo: Number(data?.pagination?.pageNo || 1),
    pageSize: Number(data?.pagination?.pageSize || PAGE_SIZE),
  };
};

const updateTimeExample: CreateGenericJdbcStarRocksTaskRequest = {
  taskCode: 'lab_order_update_time_sync',
  taskName: 'Lab Order Update Time Sync',
  clientId: 1,
  description: 'Test UPDATE_TIME_RANGE incremental sync from JDBC to StarRocks',
  incrementalStrategy: 'UPDATE_TIME_RANGE',
  watermarkField: 'update_time',
  watermarkFieldType: 'DATETIME',
  startValue: '2026-06-01 00:00:00',
  lookbackSeconds: 0,
  maxBatchSeconds: 3600,
  sourceJdbcUrl: 'jdbc:mysql://starrocks.lab:9030/st_test',
  sourceJdbcDriver: 'com.mysql.cj.jdbc.Driver',
  sourceUsername: variable('source_username'),
  sourcePassword: variable('source_password'),
  sourceQuery:
    "SELECT id, biz_no, amount, update_time FROM lab_src_order WHERE update_time >= '"
    + variable('batch_start_time')
    + "' AND update_time < '"
    + variable('batch_end_time')
    + "'",
  starrocksNodeUrls: '"starrocks.lab:8030"',
  starrocksBaseUrl: 'jdbc:mysql://starrocks.lab:9030/',
  starrocksUsername: variable('starrocks_username'),
  starrocksPassword: variable('starrocks_password'),
  starrocksDatabase: 'st_test',
  starrocksTable: 'lab_sink_order',
  starrocksErrorTable: 'lab_sink_order_error',
  sourceDatasourceId: 1,
  sinkDatasourceId: 1,
  enableDefaultChecks: true,
};

const idRangeExample: CreateGenericJdbcStarRocksTaskRequest = {
  ...updateTimeExample,
  taskCode: 'lab_order_id_range_sync',
  taskName: 'Lab Order ID Range Sync',
  description: 'Test ID_RANGE incremental sync from JDBC to StarRocks',
  incrementalStrategy: 'ID_RANGE',
  watermarkField: 'id',
  watermarkFieldType: 'LONG',
  startValue: '0',
  maxBatchSeconds: undefined,
  sourceQuery:
    'SELECT id, biz_no, amount, update_time FROM lab_src_order WHERE id > '
    + variable('batch_start_value')
    + ' AND id <= '
    + variable('batch_end_value'),
};

const SyncTestConsole: React.FC = () => {
  const [activeKey, setActiveKey] = useState('template');
  const [taskCode, setTaskCode] = useState('');
  const [loading, setLoading] = useState<Record<string, boolean>>({});
  const [templates, setTemplates] = useState<SyncTemplate[]>([]);
  const [selectedTemplate, setSelectedTemplate] = useState<SyncTemplate>();
  const [createResult, setCreateResult] = useState<CreateTaskFromTemplateResult>();
  const [diagnostic, setDiagnostic] = useState<SyncTaskDiagnosticVO>();
  const [rangePreview, setRangePreview] = useState<SyncRangePreviewVO>();
  const [hoconDiagnostic, setHoconDiagnostic] = useState<SyncHoconDiagnosticVO>();
  const [hoconPreview, setHoconPreview] = useState<HoconPreviewVO>();
  const [runResult, setRunResult] = useState<RunResultVO>();
  const [backfillResult, setBackfillResult] = useState<RunResultVO>();
  const [rerunResult, setRerunResult] = useState<RunResultVO>();
  const [runs, setRuns] = useState<SyncRunVO[]>([]);
  const [runsTotal, setRunsTotal] = useState(0);
  const [batches, setBatches] = useState<SyncBatchVO[]>([]);
  const [batchesTotal, setBatchesTotal] = useState(0);
  const [watermarks, setWatermarks] = useState<SyncWatermarkVO[]>([]);
  const [checkConfigs, setCheckConfigs] = useState<SyncCheckConfigVO[]>([]);
  const [checkDiagnostic, setCheckDiagnostic] = useState<SyncCheckDiagnosticVO>();
  const [checkResults, setCheckResults] = useState<SyncCheckResultVO[]>([]);
  const [audits, setAudits] = useState<SyncAuditVO[]>([]);
  const [auditsTotal, setAuditsTotal] = useState(0);
  const [detailDrawer, setDetailDrawer] = useState<{
    open: boolean;
    title: string;
    data?: any;
  }>({ open: false, title: '' });
  const [checkModalOpen, setCheckModalOpen] = useState(false);
  const [editingCheck, setEditingCheck] = useState<SyncCheckConfigVO>();

  const [createForm] = Form.useForm<CreateGenericJdbcStarRocksTaskRequest>();
  const [diagnoseForm] = Form.useForm();
  const [rangeForm] = Form.useForm();
  const [hoconForm] = Form.useForm();
  const [previewHoconForm] = Form.useForm();
  const [runForm] = Form.useForm();
  const [backfillForm] = Form.useForm();
  const [rerunForm] = Form.useForm();
  const [runsForm] = Form.useForm();
  const [batchesForm] = Form.useForm();
  const [watermarkForm] = Form.useForm();
  const [checkDiagnoseForm] = Form.useForm();
  const [checkResultForm] = Form.useForm();
  const [auditForm] = Form.useForm();
  const [checkConfigForm] = Form.useForm<SyncCheckConfigVO>();

  const setBusy = (key: string, value: boolean) => {
    setLoading((prev) => ({ ...prev, [key]: value }));
  };

  const requireTaskCode = () => {
    const code = taskCode.trim();
    if (!code) {
      message.warning('请先填写 taskCode');
      return undefined;
    }
    return code;
  };

  const runAction = async (key: string, action: () => Promise<void>) => {
    try {
      setBusy(key, true);
      await action();
    } catch (error: any) {
      message.error(getErrorMessage(error));
    } finally {
      setBusy(key, false);
    }
  };

  const loadTemplates = () =>
    runAction('templates', async () => {
      const response = await syncControlApi.listTemplates();
      setTemplates(response.data || []);
      message.success('模板列表已刷新');
    });

  const loadTemplateDetail = (templateCode: string) =>
    runAction('templateDetail', async () => {
      const response = await syncControlApi.getTemplate(templateCode);
      setSelectedTemplate(response.data);
    });

  const createTask = (values: CreateGenericJdbcStarRocksTaskRequest) =>
    runAction('create', async () => {
      const response = await syncControlApi.createGenericJdbcStarRocksTask(values);
      setCreateResult(response.data);
      if (response.data?.taskCode) {
        setTaskCode(response.data.taskCode);
      }
      message.success('任务创建成功');
    });

  const refreshCurrentTask = () =>
    runAction('refreshTask', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const response = await syncControlApi.diagnoseTask(code, {
        params: {},
        includeHoconPreview: false,
        includeCheckPreview: false,
        includeDatasourceCheck: false,
      });
      setDiagnostic(response.data);
      message.success('当前任务状态已刷新');
    });

  const diagnoseTask = (values: any) =>
    runAction('diagnose', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const params = parseJsonObject(values.paramsJson);
      if (params === undefined) return;
      const response = await syncControlApi.diagnoseTask(code, {
        params,
        includeHoconPreview: values.includeHoconPreview,
        includeCheckPreview: values.includeCheckPreview,
        includeDatasourceCheck: values.includeDatasourceCheck,
      });
      setDiagnostic(response.data);
    });

  const previewRange = (values: any) =>
    runAction('range', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const params = parseJsonObject(values.paramsJson);
      if (params === undefined) return;
      const response = await syncControlApi.previewRange(code, {
        runMode: values.runMode,
        params,
      });
      setRangePreview(response.data);
    });

  const diagnoseHocon = (values: any) =>
    runAction('hocon', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const params = parseJsonObject(values.paramsJson);
      if (params === undefined) return;
      const response = await syncControlApi.diagnoseHocon(code, {
        params,
        includeRenderedHocon: values.includeRenderedHocon,
      });
      setHoconDiagnostic(response.data);
    });

  const previewHocon = (values: any) =>
    runAction('previewHocon', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const params = parseJsonObject(values.paramsJson);
      if (params === undefined) return;
      const response = await syncControlApi.previewHocon(code, { params });
      setHoconPreview(response.data);
    });

  const runTask = (values: any) =>
    runAction('runTask', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const params = parseJsonObject(values.paramsJson);
      if (params === undefined) return;
      const response = await syncControlApi.runTask(code, {
        triggerType: values.triggerType,
        runMode: values.runMode,
        waitForFinish: values.waitForFinish,
        params,
      });
      setRunResult(response.data);
      if (response.data?.runId) {
        runsForm.setFieldValue('pageNo', 1);
      }
      message.success('运行请求已完成');
    });

  const backfillTask = (values: any) =>
    runAction('backfill', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const params = parseJsonObject(values.paramsJson);
      if (params === undefined) return;
      const response = await syncControlApi.backfillTask(code, {
        startTime: values.startTime,
        endTime: values.endTime,
        startValue: values.startValue,
        endValue: values.endValue,
        advanceWatermark: values.advanceWatermark,
        waitForFinish: values.waitForFinish,
        params,
      });
      setBackfillResult(response.data);
      message.success('补数请求已完成');
    });

  const rerun = (values: any) =>
    runAction('rerun', async () => {
      if (!values.runId) {
        message.warning('请填写 runId');
        return;
      }
      const params = parseJsonObject(values.paramsJson);
      if (params === undefined) return;
      const response = await syncControlApi.rerun(values.runId, {
        mode: values.mode || 'RERUN_SAME_RANGE',
        waitForFinish: values.waitForFinish,
        params,
      });
      setRerunResult(response.data);
      message.success('Rerun 请求已完成');
    });

  const loadRuns = (pageNo?: number, pageSize?: number) =>
    runAction('runs', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const values = runsForm.getFieldsValue();
      const response = await syncControlApi.listRuns(code, {
        pageNo: pageNo || values.pageNo || 1,
        pageSize: pageSize || values.pageSize || PAGE_SIZE,
        status: values.status,
        startTime: values.startTime,
        endTime: values.endTime,
      });
      const page = getPagination<SyncRunVO>(response);
      setRuns(page.rows);
      setRunsTotal(page.total);
      runsForm.setFieldsValue({ pageNo: page.pageNo, pageSize: page.pageSize });
    });

  const loadBatches = (pageNo?: number, pageSize?: number) =>
    runAction('batches', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const values = batchesForm.getFieldsValue();
      const response = await syncControlApi.listBatches(code, {
        pageNo: pageNo || values.pageNo || 1,
        pageSize: pageSize || values.pageSize || PAGE_SIZE,
        status: values.status,
        startTime: values.startTime,
        endTime: values.endTime,
      });
      const page = getPagination<SyncBatchVO>(response);
      setBatches(page.rows);
      setBatchesTotal(page.total);
      batchesForm.setFieldsValue({ pageNo: page.pageNo, pageSize: page.pageSize });
    });

  const openRunDetail = (runId?: string) =>
    runAction('runDetail', async () => {
      if (!runId) return;
      const response = await syncControlApi.getRun(runId);
      setDetailDrawer({ open: true, title: `Run Detail: ${runId}`, data: response.data });
    });

  const openBatchDetail = (batchId?: string) =>
    runAction('batchDetail', async () => {
      if (!batchId) return;
      const response = await syncControlApi.getBatch(batchId);
      setDetailDrawer({ open: true, title: `Batch Detail: ${batchId}`, data: response.data });
    });

  const loadWatermark = () =>
    runAction('watermark', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const response = await syncControlApi.getWatermark(code);
      setWatermarks(response.data || []);
    });

  const updateWatermark = (values: any) =>
    runAction('updateWatermark', async () => {
      const code = requireTaskCode();
      if (!code) return;
      if (!values.reason?.trim()) {
        message.warning('reason 必填');
        return;
      }
      await syncControlApi.updateWatermark(code, {
        watermarkKey: values.watermarkKey || 'default',
        currentValue: values.currentValue,
        reason: values.reason,
      });
      message.success('watermark 已更新');
      await loadWatermark();
    });

  const loadCheckConfigs = () =>
    runAction('checkConfigs', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const response = await syncControlApi.listCheckConfigs(code);
      setCheckConfigs(response.data || []);
    });

  const saveCheckConfig = (values: SyncCheckConfigVO) =>
    runAction('saveCheck', async () => {
      const code = requireTaskCode();
      if (!code) return;
      if (editingCheck?.checkCode) {
        await syncControlApi.updateCheckConfig(code, editingCheck.checkCode, values);
      } else {
        await syncControlApi.createCheckConfig(code, values);
      }
      message.success('check 配置已保存');
      setCheckModalOpen(false);
      setEditingCheck(undefined);
      checkConfigForm.resetFields();
      await loadCheckConfigs();
    });

  const diagnoseChecks = (values: any) =>
    runAction('diagnoseChecks', async () => {
      const code = requireTaskCode();
      if (!code) return;
      const params = parseJsonObject(values.paramsJson);
      if (params === undefined) return;
      const response = await syncControlApi.diagnoseChecks(code, {
        params,
        executeSql: values.executeSql,
      });
      setCheckDiagnostic(response.data);
    });

  const loadCheckResults = (runIdOverride?: string) =>
    runAction('checkResults', async () => {
      const values = checkResultForm.getFieldsValue();
      const runId = runIdOverride || values.runId;
      if (!runId) {
        message.warning('请填写 runId');
        return;
      }
      const response = await syncControlApi.listRunChecks(runId);
      setCheckResults(response.data || []);
      checkResultForm.setFieldValue('runId', runId);
    });

  const loadRunAudits = (runIdOverride?: string) =>
    runAction('audits', async () => {
      const values = auditForm.getFieldsValue();
      const runId = runIdOverride || values.runId;
      if (!runId) {
        message.warning('请填写 runId');
        return;
      }
      const response = await syncControlApi.listRunAudits(runId, {
        pageNo: values.pageNo || 1,
        pageSize: values.pageSize || PAGE_SIZE,
        startTime: values.startTime,
        endTime: values.endTime,
      });
      const page = getPagination<SyncAuditVO>(response);
      setAudits(page.rows);
      setAuditsTotal(page.total);
      auditForm.setFieldsValue({ runId, batchId: undefined, pageNo: page.pageNo, pageSize: page.pageSize });
    });

  const loadBatchAudits = (batchIdOverride?: string) =>
    runAction('audits', async () => {
      const values = auditForm.getFieldsValue();
      const batchId = batchIdOverride || values.batchId;
      if (!batchId) {
        message.warning('请填写 batchId');
        return;
      }
      const response = await syncControlApi.listBatchAudits(batchId, {
        pageNo: values.pageNo || 1,
        pageSize: values.pageSize || PAGE_SIZE,
        startTime: values.startTime,
        endTime: values.endTime,
      });
      const page = getPagination<SyncAuditVO>(response);
      setAudits(page.rows);
      setAuditsTotal(page.total);
      auditForm.setFieldsValue({ batchId, runId: undefined, pageNo: page.pageNo, pageSize: page.pageSize });
    });

  const commonJsonFormItem = (name = 'paramsJson') => (
    <Form.Item name={name} label="params JSON">
      <TextArea className="json-editor" rows={7} />
    </Form.Item>
  );

  const renderCreateTab = () => (
    <Row gutter={16}>
      <Col xs={24} xl={16}>
        <Card
          className="section-card"
          title="创建 Generic JDBC/SQL -> StarRocks 增量任务"
          extra={
            <Space wrap>
              <Button onClick={() => createForm.setFieldsValue(updateTimeExample)}>
                填充 UPDATE_TIME_RANGE 示例
              </Button>
              <Button onClick={() => createForm.setFieldsValue(idRangeExample)}>
                填充 ID_RANGE 示例
              </Button>
              <Button onClick={() => createForm.resetFields()}>清空</Button>
            </Space>
          }
        >
          <Form
            form={createForm}
            layout="vertical"
            initialValues={updateTimeExample}
            onFinish={createTask}
          >
            <Divider orientation="left">基础信息</Divider>
            <Row gutter={12}>
              <Col xs={24} md={8}>
                <Form.Item name="taskCode" label="taskCode" rules={[{ required: true }]}>
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="taskName" label="taskName" rules={[{ required: true }]}>
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="clientId" label="clientId" rules={[{ required: true }]}>
                  <InputNumber style={{ width: '100%' }} min={1} />
                </Form.Item>
              </Col>
              <Col span={24}>
                <Form.Item name="description" label="description">
                  <Input />
                </Form.Item>
              </Col>
            </Row>

            <Divider orientation="left">增量策略</Divider>
            <Row gutter={12}>
              <Col xs={24} md={8}>
                <Form.Item name="incrementalStrategy" label="incrementalStrategy">
                  <Select options={[{ value: 'UPDATE_TIME_RANGE' }, { value: 'ID_RANGE' }]} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="watermarkField" label="watermarkField">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="watermarkFieldType" label="watermarkFieldType">
                  <Select options={[{ value: 'DATETIME' }, { value: 'LONG' }]} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="startValue" label="startValue">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="lookbackSeconds" label="lookbackSeconds">
                  <InputNumber style={{ width: '100%' }} min={0} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="maxBatchSeconds" label="maxBatchSeconds">
                  <InputNumber style={{ width: '100%' }} min={0} />
                </Form.Item>
              </Col>
            </Row>

            <Divider orientation="left">Source JDBC</Divider>
            <Row gutter={12}>
              <Col xs={24} md={12}>
                <Form.Item name="sourceJdbcUrl" label="sourceJdbcUrl">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="sourceJdbcDriver" label="sourceJdbcDriver">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="sourceUsername" label="sourceUsername">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="sourcePassword" label="sourcePassword">
                  <Input.Password />
                </Form.Item>
              </Col>
              <Col span={24}>
                <Form.Item name="sourceQuery" label="sourceQuery">
                  <TextArea className="json-editor" rows={4} />
                </Form.Item>
              </Col>
            </Row>

            <Divider orientation="left">StarRocks Sink</Divider>
            <Row gutter={12}>
              <Col xs={24} md={12}>
                <Form.Item name="starrocksNodeUrls" label="starrocksNodeUrls">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="starrocksBaseUrl" label="starrocksBaseUrl">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="starrocksUsername" label="starrocksUsername">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="starrocksPassword" label="starrocksPassword">
                  <Input.Password />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="starrocksDatabase" label="starrocksDatabase">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="starrocksTable" label="starrocksTable">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="starrocksErrorTable" label="starrocksErrorTable">
                  <Input />
                </Form.Item>
              </Col>
            </Row>

            <Divider orientation="left">Check</Divider>
            <Row gutter={12}>
              <Col xs={24} md={8}>
                <Form.Item name="sourceDatasourceId" label="sourceDatasourceId">
                  <InputNumber style={{ width: '100%' }} min={1} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="sinkDatasourceId" label="sinkDatasourceId">
                  <InputNumber style={{ width: '100%' }} min={1} />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item
                  name="enableDefaultChecks"
                  label="enableDefaultChecks"
                  valuePropName="checked"
                >
                  <Checkbox>创建默认 count checks</Checkbox>
                </Form.Item>
              </Col>
            </Row>
            <Button type="primary" htmlType="submit" loading={loading.create}>
              创建任务
            </Button>
          </Form>
        </Card>
      </Col>
      <Col xs={24} xl={8}>
        <Card
          className="section-card"
          title="模板查询"
          extra={<Button onClick={loadTemplates} loading={loading.templates}>刷新模板</Button>}
        >
          <Select
            style={{ width: '100%', marginBottom: 12 }}
            placeholder="选择模板"
            options={templates.map((item) => ({
              label: item.templateName || item.templateCode,
              value: item.templateCode,
            }))}
            onChange={loadTemplateDetail}
          />
          {selectedTemplate ? <JsonBlock value={selectedTemplate} /> : <Alert showIcon message="先刷新并选择模板" />}
        </Card>
        <Card className="section-card" title="创建结果">
          {createResult ? (
            <>
              <Descriptions column={1} size="small">
                <Descriptions.Item label="taskId">{compact(createResult.taskId)}</Descriptions.Item>
                <Descriptions.Item label="taskCode">{compact(createResult.taskCode)}</Descriptions.Item>
                <Descriptions.Item label="versionId">{compact(createResult.versionId)}</Descriptions.Item>
                <Descriptions.Item label="createdCheckCount">
                  {compact(createResult.createdCheckCount)}
                </Descriptions.Item>
              </Descriptions>
              <Divider />
              <Text strong>warnings</Text>
              <JsonBlock value={createResult.warnings || []} />
              <Text strong>nextActions</Text>
              <JsonBlock value={createResult.nextActions || []} />
            </>
          ) : (
            <Alert type="info" showIcon message="暂无创建结果" />
          )}
        </Card>
      </Col>
    </Row>
  );

  const renderDiagnosticTab = () => (
    <Row gutter={16}>
      <Col xs={24} lg={8}>
        <Card className="section-card" title="诊断请求">
          <Form
            form={diagnoseForm}
            layout="vertical"
            initialValues={{
              paramsJson: ID_RANGE_PARAMS,
              includeHoconPreview: true,
              includeCheckPreview: true,
              includeDatasourceCheck: false,
            }}
            onFinish={diagnoseTask}
          >
            {commonJsonFormItem()}
            <Form.Item name="includeHoconPreview" valuePropName="checked">
              <Checkbox>includeHoconPreview</Checkbox>
            </Form.Item>
            <Form.Item name="includeCheckPreview" valuePropName="checked">
              <Checkbox>includeCheckPreview</Checkbox>
            </Form.Item>
            <Form.Item name="includeDatasourceCheck" valuePropName="checked">
              <Checkbox>includeDatasourceCheck</Checkbox>
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={loading.diagnose}>
              诊断
            </Button>
          </Form>
        </Card>
      </Col>
      <Col xs={24} lg={16}>
        <Card
          className="section-card"
          title="诊断结果"
          extra={statusTag(diagnostic?.diagnostics?.level)}
        >
          {diagnostic ? (
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              <ResultGrid data={diagnostic.task} />
              <Collapse
                items={[
                  { key: 'version', label: 'version', children: <ResultGrid data={diagnostic.version} /> },
                  {
                    key: 'incrementalConfig',
                    label: 'incrementalConfig',
                    children: <ResultGrid data={diagnostic.incrementalConfig} />,
                  },
                  { key: 'watermark', label: 'watermark', children: <ResultGrid data={diagnostic.watermark} /> },
                  { key: 'rangePreview', label: 'rangePreview', children: <JsonBlock value={diagnostic.rangePreview} /> },
                  {
                    key: 'hocon',
                    label: `hocon ${diagnostic.hocon?.renderable ? 'renderable' : 'not renderable'}`,
                    children: (
                      <Space direction="vertical" style={{ width: '100%' }}>
                        <Text>missingVariables</Text>
                        <JsonBlock value={diagnostic.hocon?.missingVariables || []} />
                        <Text>renderedHash: {compact(diagnostic.hocon?.renderedHash)}</Text>
                        <CodeBlock value={diagnostic.hocon?.renderedHoconPreview} />
                      </Space>
                    ),
                  },
                  { key: 'checks', label: 'checks', children: <JsonBlock value={diagnostic.checks} /> },
                  { key: 'client', label: 'client', children: <ResultGrid data={diagnostic.client} /> },
                  {
                    key: 'messages',
                    label: 'diagnostics messages',
                    children: <JsonBlock value={diagnostic.diagnostics?.messages || []} />,
                  },
                  { key: 'raw', label: '原始响应', children: <JsonBlock value={diagnostic} /> },
                ]}
              />
            </Space>
          ) : (
            <Alert showIcon message="暂无诊断结果" />
          )}
        </Card>
      </Col>
    </Row>
  );

  const renderHoconRangeTab = () => (
    <Row gutter={16}>
      <Col xs={24} xl={12}>
        <Card className="section-card" title="Preview Range">
          <Form
            form={rangeForm}
            layout="vertical"
            initialValues={{ runMode: 'NORMAL', paramsJson: ID_RANGE_PARAMS }}
            onFinish={previewRange}
          >
            <Form.Item name="runMode" label="runMode">
              <Select options={[{ value: 'NORMAL' }, { value: 'BACKFILL' }]} />
            </Form.Item>
            {commonJsonFormItem()}
            <Button type="primary" htmlType="submit" loading={loading.range}>
              预览 Range
            </Button>
          </Form>
          <Divider />
          {rangePreview ? <ResultGrid data={rangePreview as Record<string, any>} /> : <Alert showIcon message="暂无 range 预览" />}
        </Card>
      </Col>
      <Col xs={24} xl={12}>
        <Card className="section-card" title="Diagnose HOCON">
          <Form
            form={hoconForm}
            layout="vertical"
            initialValues={{ paramsJson: ID_RANGE_PARAMS, includeRenderedHocon: false }}
            onFinish={diagnoseHocon}
          >
            {commonJsonFormItem()}
            <Form.Item name="includeRenderedHocon" valuePropName="checked">
              <Checkbox>includeRenderedHocon</Checkbox>
            </Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={loading.hocon}>
                诊断 HOCON
              </Button>
              <Button icon={<CopyOutlined />} onClick={() => copyText(hoconDiagnostic?.renderedHocon)}>
                复制 renderedHocon
              </Button>
            </Space>
          </Form>
          <Divider />
          {hoconDiagnostic ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <ResultGrid
                data={{
                  renderable: hoconDiagnostic.renderable,
                  renderedHash: hoconDiagnostic.renderedHash,
                  errorMessage: hoconDiagnostic.errorMessage,
                }}
              />
              <Text strong>missingVariables</Text>
              <JsonBlock value={hoconDiagnostic.missingVariables || []} />
              <Text strong>variablesUsed</Text>
              <JsonBlock value={hoconDiagnostic.variablesUsed || []} />
              <Text strong>renderedHoconPreview</Text>
              <CodeBlock value={hoconDiagnostic.renderedHoconPreview} />
              <Text strong>renderedHocon</Text>
              <CodeBlock value={hoconDiagnostic.renderedHocon} />
            </Space>
          ) : (
            <Alert showIcon message="暂无 HOCON 诊断结果" />
          )}
        </Card>
        <Card className="section-card" title="Preview HOCON">
          <Form
            form={previewHoconForm}
            layout="vertical"
            initialValues={{ paramsJson: ID_RANGE_PARAMS }}
            onFinish={previewHocon}
          >
            {commonJsonFormItem()}
            <Space>
              <Button htmlType="submit" loading={loading.previewHocon}>
                预览 HOCON
              </Button>
              <Button icon={<CopyOutlined />} onClick={() => copyText(hoconPreview?.renderedHocon)}>
                复制预览 HOCON
              </Button>
            </Space>
          </Form>
          <Divider />
          {hoconPreview ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Text>hoconHash: {compact(hoconPreview.hoconHash)}</Text>
              <CodeBlock value={hoconPreview.renderedHocon} />
            </Space>
          ) : (
            <Alert showIcon message="暂无 HOCON 预览" />
          )}
        </Card>
      </Col>
    </Row>
  );

  const renderRunResult = (title: string, result?: RunResultVO) => (
    <Card className="section-card" title={title}>
      {result ? (
        <Space direction="vertical" style={{ width: '100%' }}>
          <ResultGrid data={result as Record<string, any>} />
          <Space wrap>
            <Button
              onClick={() => {
                if (result.runId) {
                  auditForm.setFieldValue('runId', result.runId);
                  setActiveKey('audits');
                }
              }}
            >
              填充 run audits
            </Button>
            <Button
              onClick={() => {
                if (result.runId) {
                  checkResultForm.setFieldValue('runId', result.runId);
                  setActiveKey('checks');
                }
              }}
            >
              填充 check results
            </Button>
          </Space>
        </Space>
      ) : (
        <Alert showIcon message="暂无结果" />
      )}
    </Card>
  );

  const renderRunTab = () => (
    <Row gutter={16}>
      <Col xs={24} xl={8}>
        <Card className="section-card" title="普通运行">
          <Form
            form={runForm}
            layout="vertical"
            initialValues={{
              triggerType: 'MANUAL',
              runMode: 'NORMAL',
              waitForFinish: true,
              paramsJson: DEFAULT_PARAMS,
            }}
            onFinish={runTask}
          >
            <Form.Item name="triggerType" label="triggerType">
              <Select options={[{ value: 'MANUAL' }]} />
            </Form.Item>
            <Form.Item name="runMode" label="runMode">
              <Select options={[{ value: 'NORMAL' }]} />
            </Form.Item>
            <Form.Item name="waitForFinish" valuePropName="checked">
              <Checkbox>waitForFinish</Checkbox>
            </Form.Item>
            {commonJsonFormItem()}
            <Button type="primary" icon={<PlayCircleOutlined />} htmlType="submit" loading={loading.runTask}>
              运行任务
            </Button>
          </Form>
        </Card>
      </Col>
      <Col xs={24} xl={8}>
        <Card className="section-card" title="补数 Backfill">
          <Form
            form={backfillForm}
            layout="vertical"
            initialValues={{
              startTime: '2026-06-01 00:00:00',
              endTime: '2026-06-01 01:00:00',
              advanceWatermark: false,
              waitForFinish: true,
              paramsJson: DEFAULT_PARAMS,
            }}
            onFinish={backfillTask}
          >
            <Form.Item name="startTime" label="startTime">
              <Input />
            </Form.Item>
            <Form.Item name="endTime" label="endTime">
              <Input />
            </Form.Item>
            <Form.Item name="startValue" label="startValue">
              <Input />
            </Form.Item>
            <Form.Item name="endValue" label="endValue">
              <Input />
            </Form.Item>
            <Form.Item name="advanceWatermark" valuePropName="checked">
              <Checkbox>advanceWatermark</Checkbox>
            </Form.Item>
            <Form.Item name="waitForFinish" valuePropName="checked">
              <Checkbox>waitForFinish</Checkbox>
            </Form.Item>
            {commonJsonFormItem()}
            <Button htmlType="submit" loading={loading.backfill}>
              补数运行
            </Button>
          </Form>
        </Card>
      </Col>
      <Col xs={24} xl={8}>
        <Card className="section-card" title="Rerun failed run">
          <Form
            form={rerunForm}
            layout="vertical"
            initialValues={{
              mode: 'RERUN_SAME_RANGE',
              waitForFinish: true,
              paramsJson: DEFAULT_PARAMS,
            }}
            onFinish={rerun}
          >
            <Form.Item name="runId" label="runId" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="mode" label="mode">
              <Select options={[{ value: 'RERUN_SAME_RANGE' }]} />
            </Form.Item>
            <Form.Item name="waitForFinish" valuePropName="checked">
              <Checkbox>waitForFinish</Checkbox>
            </Form.Item>
            {commonJsonFormItem()}
            <Button htmlType="submit" loading={loading.rerun}>
              Rerun
            </Button>
          </Form>
        </Card>
      </Col>
      <Col xs={24} xl={8}>{renderRunResult('普通运行结果', runResult)}</Col>
      <Col xs={24} xl={8}>{renderRunResult('补数运行结果', backfillResult)}</Col>
      <Col xs={24} xl={8}>{renderRunResult('Rerun 结果', rerunResult)}</Col>
    </Row>
  );

  const runColumns: TableColumnsType<SyncRunVO> = [
    { title: 'runId', dataIndex: 'runId', render: (value) => ellipsisText(value, 210) },
    { title: 'batchId', dataIndex: 'batchId', render: (value) => ellipsisText(value, 180) },
    { title: 'status', dataIndex: 'status', render: (value, row) => statusTag(value || row.runStatus) },
    { title: 'seatunnelJobId', dataIndex: 'seatunnelJobId', render: (value) => ellipsisText(value, 150) },
    { title: 'submitTime', dataIndex: 'submitTime', width: 170 },
    { title: 'startTime', dataIndex: 'startTime', width: 170 },
    { title: 'endTime', dataIndex: 'endTime', width: 170 },
    { title: 'sourceCount', dataIndex: 'sourceCount', width: 110 },
    { title: 'sinkCount', dataIndex: 'sinkCount', width: 100 },
    { title: 'errorCount', dataIndex: 'errorCount', width: 100 },
    { title: 'errorMessage', dataIndex: 'errorMessage', render: (value) => ellipsisText(value, 240) },
    {
      title: '操作',
      fixed: 'right',
      width: 260,
      render: (_, row) => (
        <Space wrap>
          <Button size="small" onClick={() => openRunDetail(row.runId)}>详情</Button>
          <Button
            size="small"
            onClick={() => {
              auditForm.setFieldValue('runId', row.runId);
              setActiveKey('audits');
              loadRunAudits(row.runId);
            }}
          >
            audits
          </Button>
          <Button
            size="small"
            onClick={() => {
              checkResultForm.setFieldValue('runId', row.runId);
              setActiveKey('checks');
              loadCheckResults(row.runId);
            }}
          >
            checks
          </Button>
          <Button
            size="small"
            onClick={() => {
              rerunForm.setFieldValue('runId', row.runId);
              setActiveKey('run');
            }}
          >
            rerun
          </Button>
        </Space>
      ),
    },
  ];

  const renderRunsTab = () => (
    <Card className="section-card" title="Runs">
      <Form
        form={runsForm}
        layout="inline"
        initialValues={{ pageNo: 1, pageSize: PAGE_SIZE }}
        onFinish={() => loadRuns()}
        style={{ marginBottom: 16 }}
      >
        <Form.Item name="status" label="status">
          <Input allowClear style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="startTime" label="startTime">
          <Input allowClear style={{ width: 190 }} />
        </Form.Item>
        <Form.Item name="endTime" label="endTime">
          <Input allowClear style={{ width: 190 }} />
        </Form.Item>
        <Form.Item name="pageNo" label="pageNo">
          <InputNumber min={1} />
        </Form.Item>
        <Form.Item name="pageSize" label="pageSize">
          <InputNumber min={1} max={100} />
        </Form.Item>
        <Button htmlType="submit" icon={<SearchOutlined />} loading={loading.runs}>
          查询
        </Button>
      </Form>
      <Table
        rowKey={(row) => row.runId || Math.random().toString()}
        size="small"
        scroll={{ x: 1600 }}
        columns={runColumns}
        dataSource={runs}
        loading={loading.runs}
        pagination={{
          total: runsTotal,
          pageSize: runsForm.getFieldValue('pageSize') || PAGE_SIZE,
          current: runsForm.getFieldValue('pageNo') || 1,
          onChange: loadRuns,
          showSizeChanger: true,
        }}
      />
    </Card>
  );

  const batchColumns: TableColumnsType<SyncBatchVO> = [
    { title: 'batchId', dataIndex: 'batchId', render: (value) => ellipsisText(value, 220) },
    { title: 'status', dataIndex: 'status', render: statusTag },
    { title: 'batchStartValue', dataIndex: 'batchStartValue', render: (value) => ellipsisText(value, 180) },
    { title: 'batchEndValue', dataIndex: 'batchEndValue', render: (value) => ellipsisText(value, 180) },
    { title: 'batchStartTime', dataIndex: 'batchStartTime', width: 170 },
    { title: 'batchEndTime', dataIndex: 'batchEndTime', width: 170 },
    { title: 'sourceCount', dataIndex: 'sourceCount', width: 110 },
    { title: 'sinkCount', dataIndex: 'sinkCount', width: 100 },
    { title: 'errorCount', dataIndex: 'errorCount', width: 100 },
    { title: 'errorMessage', dataIndex: 'errorMessage', render: (value) => ellipsisText(value, 240) },
    { title: 'createTime', dataIndex: 'createTime', width: 170 },
    { title: 'updateTime', dataIndex: 'updateTime', width: 170 },
    {
      title: '操作',
      fixed: 'right',
      width: 160,
      render: (_, row) => (
        <Space wrap>
          <Button size="small" onClick={() => openBatchDetail(row.batchId)}>详情</Button>
          <Button
            size="small"
            onClick={() => {
              auditForm.setFieldValue('batchId', row.batchId);
              setActiveKey('audits');
              loadBatchAudits(row.batchId);
            }}
          >
            audits
          </Button>
        </Space>
      ),
    },
  ];

  const renderBatchesTab = () => (
    <Card className="section-card" title="Batches">
      <Form
        form={batchesForm}
        layout="inline"
        initialValues={{ pageNo: 1, pageSize: PAGE_SIZE }}
        onFinish={() => loadBatches()}
        style={{ marginBottom: 16 }}
      >
        <Form.Item name="status" label="status">
          <Input allowClear style={{ width: 140 }} />
        </Form.Item>
        <Form.Item name="startTime" label="startTime">
          <Input allowClear style={{ width: 190 }} />
        </Form.Item>
        <Form.Item name="endTime" label="endTime">
          <Input allowClear style={{ width: 190 }} />
        </Form.Item>
        <Form.Item name="pageNo" label="pageNo">
          <InputNumber min={1} />
        </Form.Item>
        <Form.Item name="pageSize" label="pageSize">
          <InputNumber min={1} max={100} />
        </Form.Item>
        <Button htmlType="submit" icon={<SearchOutlined />} loading={loading.batches}>
          查询
        </Button>
      </Form>
      <Table
        rowKey={(row) => row.batchId || Math.random().toString()}
        size="small"
        scroll={{ x: 1700 }}
        columns={batchColumns}
        dataSource={batches}
        loading={loading.batches}
        pagination={{
          total: batchesTotal,
          pageSize: batchesForm.getFieldValue('pageSize') || PAGE_SIZE,
          current: batchesForm.getFieldValue('pageNo') || 1,
          onChange: loadBatches,
          showSizeChanger: true,
        }}
      />
    </Card>
  );

  const renderWatermarkTab = () => (
    <Row gutter={16}>
      <Col xs={24} xl={16}>
        <Card
          className="section-card"
          title="Watermark"
          extra={<Button icon={<ReloadOutlined />} onClick={loadWatermark} loading={loading.watermark}>刷新</Button>}
        >
          <Table
            rowKey={(row) => String(row.id || row.watermarkKey)}
            size="small"
            dataSource={watermarks}
            loading={loading.watermark}
            columns={[
              { title: 'watermarkKey', dataIndex: 'watermarkKey' },
              { title: 'currentValue', dataIndex: 'currentValue', render: (value) => ellipsisText(value, 220) },
              { title: 'previousValue', dataIndex: 'previousValue', render: (value) => ellipsisText(value, 220) },
              { title: 'currentValueType', dataIndex: 'currentValueType' },
              { title: 'lastSuccessRunId', dataIndex: 'lastSuccessRunId' },
              { title: 'lastSuccessBatchId', dataIndex: 'lastSuccessBatchId', render: (value) => ellipsisText(value, 180) },
              { title: 'updateTime', dataIndex: 'updateTime' },
            ]}
          />
        </Card>
      </Col>
      <Col xs={24} xl={8}>
        <Card className="section-card" title="手动修正 Watermark">
          <Alert
            type="warning"
            showIcon
            message="危险操作"
            description="只建议在 lab 测试或人工修复时使用。"
            style={{ marginBottom: 16 }}
          />
          <Form
            form={watermarkForm}
            layout="vertical"
            initialValues={{ watermarkKey: 'default' }}
            onFinish={updateWatermark}
          >
            <Form.Item name="watermarkKey" label="watermarkKey">
              <Input />
            </Form.Item>
            <Form.Item name="currentValue" label="currentValue" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="reason" label="reason" rules={[{ required: true }]}>
              <TextArea rows={3} />
            </Form.Item>
            <Button type="primary" danger htmlType="submit" loading={loading.updateWatermark}>
              更新 watermark
            </Button>
          </Form>
        </Card>
      </Col>
    </Row>
  );

  const checkConfigColumns: TableColumnsType<SyncCheckConfigVO> = [
    { title: 'checkCode', dataIndex: 'checkCode', render: (value) => ellipsisText(value, 160) },
    { title: 'checkName', dataIndex: 'checkName', render: (value) => ellipsisText(value, 160) },
    { title: 'checkType', dataIndex: 'checkType' },
    { title: 'datasourceType', dataIndex: 'datasourceType' },
    { title: 'datasourceId', dataIndex: 'datasourceId' },
    { title: 'expectedOperator', dataIndex: 'expectedOperator' },
    { title: 'expectedValue', dataIndex: 'expectedValue' },
    { title: 'compareToCheckCode', dataIndex: 'compareToCheckCode' },
    { title: 'failOnMismatch', dataIndex: 'failOnMismatch', render: boolTag },
    { title: 'enabled', dataIndex: 'enabled', render: boolTag },
    { title: 'sortOrder', dataIndex: 'sortOrder' },
    {
      title: '操作',
      render: (_, row) => (
        <Button
          size="small"
          onClick={() => {
            setEditingCheck(row);
            checkConfigForm.setFieldsValue(row);
            setCheckModalOpen(true);
          }}
        >
          编辑
        </Button>
      ),
    },
  ];

  const checkDiagnosticColumns: TableColumnsType<SyncCheckDiagnosticItem> = [
    { title: 'checkCode', dataIndex: 'checkCode' },
    { title: 'renderable', dataIndex: 'renderable', render: boolTag },
    { title: 'missingVariables', dataIndex: 'missingVariables', render: (value) => (value || []).join(', ') || '-' },
    { title: 'renderedSqlPreview', dataIndex: 'renderedSqlPreview', render: (value) => ellipsisText(value, 320) },
    { title: 'executed', dataIndex: 'executed', render: boolTag },
    { title: 'actualValue', dataIndex: 'actualValue' },
    { title: 'passed', dataIndex: 'passed', render: boolTag },
    { title: 'errorMessage', dataIndex: 'errorMessage', render: (value) => ellipsisText(value, 240) },
  ];

  const renderChecksTab = () => (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card
        className="section-card"
        title="Check Config"
        extra={
          <Space>
            <Button onClick={loadCheckConfigs} loading={loading.checkConfigs}>刷新配置</Button>
            <Button
              type="primary"
              onClick={() => {
                setEditingCheck(undefined);
                checkConfigForm.resetFields();
                setCheckModalOpen(true);
              }}
            >
              新增
            </Button>
          </Space>
        }
      >
        <Table
          rowKey={(row) => row.checkCode || String(row.id)}
          size="small"
          scroll={{ x: 1400 }}
          columns={checkConfigColumns}
          dataSource={checkConfigs}
          loading={loading.checkConfigs}
        />
      </Card>

      <Card className="section-card" title="Check Diagnose">
        <Form
          form={checkDiagnoseForm}
          layout="vertical"
          initialValues={{ paramsJson: ID_RANGE_PARAMS, executeSql: false }}
          onFinish={diagnoseChecks}
        >
          {commonJsonFormItem()}
          <Form.Item name="executeSql" valuePropName="checked">
            <Checkbox>executeSql</Checkbox>
          </Form.Item>
          <Button htmlType="submit" loading={loading.diagnoseChecks}>
            诊断 checks
          </Button>
        </Form>
        <Divider />
        <ResultGrid
          data={{
            checkCount: checkDiagnostic?.checkCount,
            enabledCheckCount: checkDiagnostic?.enabledCheckCount,
            renderable: checkDiagnostic?.renderable,
            missingDatasourceIds: checkDiagnostic?.missingDatasourceIds?.join(', '),
          }}
        />
        <Table
          style={{ marginTop: 12 }}
          rowKey={(row) => row.checkCode || Math.random().toString()}
          size="small"
          columns={checkDiagnosticColumns}
          dataSource={checkDiagnostic?.checks || []}
          expandable={{
            expandedRowRender: (row) => <CodeBlock value={row.renderedSqlPreview} />,
          }}
        />
      </Card>

      <Card className="section-card" title="Check Result">
        <Form form={checkResultForm} layout="inline" onFinish={() => loadCheckResults()}>
          <Form.Item name="runId" label="runId" rules={[{ required: true }]}>
            <Input style={{ width: 260 }} />
          </Form.Item>
          <Button htmlType="submit" loading={loading.checkResults}>
            查询 check results
          </Button>
        </Form>
        <Table
          style={{ marginTop: 16 }}
          rowKey={(row) => String(row.id || row.checkCode)}
          size="small"
          scroll={{ x: 1400 }}
          dataSource={checkResults}
          columns={[
            { title: 'checkCode', dataIndex: 'checkCode' },
            { title: 'checkType', dataIndex: 'checkType' },
            { title: 'actualValue', dataIndex: 'actualValue' },
            { title: 'expectedOperator', dataIndex: 'expectedOperator' },
            { title: 'expectedValue', dataIndex: 'expectedValue' },
            { title: 'compareToCheckCode', dataIndex: 'compareToCheckCode' },
            { title: 'compareToActualValue', dataIndex: 'compareToActualValue' },
            { title: 'passed', dataIndex: 'passed', render: boolTag },
            { title: 'errorMessage', dataIndex: 'errorMessage', render: (value) => ellipsisText(value, 260) },
            { title: 'startTime', dataIndex: 'startTime' },
            { title: 'endTime', dataIndex: 'endTime' },
          ]}
          expandable={{
            expandedRowRender: (row) => <CodeBlock value={row.renderedSql} />,
          }}
        />
      </Card>
    </Space>
  );

  const renderAuditsTab = () => (
    <Card className="section-card" title="Audits">
      <Form
        form={auditForm}
        layout="inline"
        initialValues={{ pageNo: 1, pageSize: PAGE_SIZE }}
        style={{ marginBottom: 16 }}
      >
        <Form.Item name="runId" label="runId">
          <Input allowClear style={{ width: 260 }} />
        </Form.Item>
        <Form.Item name="batchId" label="batchId">
          <Input allowClear style={{ width: 260 }} />
        </Form.Item>
        <Form.Item name="startTime" label="startTime">
          <Input allowClear style={{ width: 190 }} />
        </Form.Item>
        <Form.Item name="endTime" label="endTime">
          <Input allowClear style={{ width: 190 }} />
        </Form.Item>
        <Form.Item name="pageNo" label="pageNo">
          <InputNumber min={1} />
        </Form.Item>
        <Form.Item name="pageSize" label="pageSize">
          <InputNumber min={1} max={100} />
        </Form.Item>
        <Space>
          <Button onClick={() => loadRunAudits()} loading={loading.audits}>查询 run audits</Button>
          <Button onClick={() => loadBatchAudits()} loading={loading.audits}>查询 batch audits</Button>
        </Space>
      </Form>
      <Table
        rowKey={(row) => String(row.id || `${row.runId}-${row.eventType}-${row.createTime}`)}
        size="small"
        dataSource={audits}
        loading={loading.audits}
        pagination={{
          total: auditsTotal,
          pageSize: auditForm.getFieldValue('pageSize') || PAGE_SIZE,
          current: auditForm.getFieldValue('pageNo') || 1,
        }}
        columns={[
          { title: 'eventType', dataIndex: 'eventType', render: (value) => ellipsisText(value, 220) },
          { title: 'eventLevel', dataIndex: 'eventLevel', render: statusTag },
          { title: 'eventMessage', dataIndex: 'eventMessage', render: (value) => ellipsisText(value, 360) },
          { title: 'detailJson', dataIndex: 'detailJson', render: (value) => ellipsisText(value, 300) },
          { title: 'createTime', dataIndex: 'createTime', width: 170 },
        ]}
        expandable={{
          expandedRowRender: (row) => <CodeBlock value={row.detailJson} />,
        }}
      />
    </Card>
  );

  return (
    <div className="sync-test-console">
      <div className="console-hero">
        <h1>增量同步测试台</h1>
        <p>
          用于测试 JDBC / SQL / Oracle / StarRocks 增量 Batch 任务的 batch、watermark、HOCON、Zeta
          提交、check、audit、rerun 能力。
        </p>
      </div>

      <Card className="task-bar">
        <Row gutter={12} align="middle">
          <Col flex="auto">
            <Input
              size="large"
              prefix={<DatabaseOutlined />}
              placeholder="输入 taskCode，后续所有 Tab 默认使用该任务"
              value={taskCode}
              onChange={(event) => setTaskCode(event.target.value)}
            />
          </Col>
          <Col>
            <Button size="large" icon={<ReloadOutlined />} onClick={refreshCurrentTask} loading={loading.refreshTask}>
              刷新当前任务
            </Button>
          </Col>
          <Col>
            <Space>
              <Text type="secondary">当前状态</Text>
              {statusTag(diagnostic?.task?.status)}
              {statusTag(diagnostic?.diagnostics?.level)}
            </Space>
          </Col>
        </Row>
      </Card>

      <Card className="main-card">
        <Tabs
          activeKey={activeKey}
          onChange={setActiveKey}
          items={[
            { key: 'template', label: '模板创建', children: renderCreateTab() },
            { key: 'diagnose', label: '任务诊断', children: renderDiagnosticTab() },
            { key: 'hocon-range', label: 'HOCON / Range', children: renderHoconRangeTab() },
            { key: 'run', label: '运行任务', children: renderRunTab() },
            { key: 'runs', label: 'Runs', children: renderRunsTab() },
            { key: 'batches', label: 'Batches', children: renderBatchesTab() },
            { key: 'watermark', label: 'Watermark', children: renderWatermarkTab() },
            { key: 'checks', label: 'Checks', children: renderChecksTab() },
            { key: 'audits', label: 'Audits', children: renderAuditsTab() },
          ]}
        />
      </Card>

      <Drawer
        title={detailDrawer.title}
        open={detailDrawer.open}
        width={760}
        onClose={() => setDetailDrawer({ open: false, title: '' })}
      >
        <JsonBlock value={detailDrawer.data} />
      </Drawer>

      <Modal
        title={editingCheck ? `编辑 Check: ${editingCheck.checkCode}` : '新增 Check'}
        open={checkModalOpen}
        onCancel={() => {
          setCheckModalOpen(false);
          setEditingCheck(undefined);
        }}
        onOk={() => checkConfigForm.submit()}
        confirmLoading={loading.saveCheck}
        width={760}
      >
        <Form form={checkConfigForm} layout="vertical" onFinish={saveCheckConfig}>
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item name="checkCode" label="checkCode" rules={[{ required: !editingCheck }]}>
                <Input disabled={Boolean(editingCheck)} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="checkName" label="checkName">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="checkType" label="checkType" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'SOURCE_COUNT' },
                    { value: 'SINK_COUNT' },
                    { value: 'ERROR_COUNT' },
                    { value: 'CUSTOM_COUNT' },
                    { value: 'CUSTOM_BOOLEAN' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="datasourceType" label="datasourceType">
                <Select allowClear options={[{ value: 'SOURCE' }, { value: 'SINK' }]} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="datasourceId" label="datasourceId">
                <InputNumber style={{ width: '100%' }} min={1} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="expectedOperator" label="expectedOperator">
                <Select
                  allowClear
                  options={[
                    { value: 'EQ' },
                    { value: 'NE' },
                    { value: 'GT' },
                    { value: 'GE' },
                    { value: 'LT' },
                    { value: 'LE' },
                    { value: 'IS_NULL' },
                    { value: 'IS_NOT_NULL' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="expectedValue" label="expectedValue">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="compareToCheckCode" label="compareToCheckCode">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="sortOrder" label="sortOrder">
                <InputNumber style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name="failOnMismatch" valuePropName="checked">
                <Checkbox>failOnMismatch</Checkbox>
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name="enabled" valuePropName="checked">
                <Checkbox>enabled</Checkbox>
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="sqlText" label="sqlText" rules={[{ required: true }]}>
                <TextArea className="json-editor" rows={5} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item name="description" label="description">
                <Input />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};

export default SyncTestConsole;
