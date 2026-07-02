import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  SyncOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import {
  Alert,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
} from "antd";
import dayjs from "dayjs";
import { useEffect, useMemo, useState } from "react";
import "./index.less";
import {
  createMeasurementTask,
  deleteMeasurementTask,
  discoverMeasurementFiles,
  fetchAllDataSources,
  fetchCleanupSql,
  fetchMeasurementFiles,
  fetchMeasurementRuns,
  fetchMeasurementTasks,
  fetchRecommendedDdl,
  fetchRunHocon,
  loadParsedMeasurementFiles,
  markMeasurementFileFailed,
  parseAndLoadMeasurementFiles,
  parseOnlyMeasurementFiles,
  preflightMeasurementTask,
  previewParseMeasurementFile,
  resetMeasurementFilePending,
  retryFailedMeasurementFile,
  schemaCheckMeasurement,
  testScanMeasurementTask,
  updateMeasurementTask,
} from "./service";
import type {
  DataSourceRecord,
  MeasurementFileItem,
  MeasurementFileRun,
  MeasurementFileTask,
  MeasurementParseLoadResult,
  MeasurementParsePreview,
  MeasurementPreflight,
  MeasurementScanResult,
  MeasurementSqlTemplate,
  PaginationInfo,
} from "./types";

const FILE_SOURCE_TYPES = ["LOCAL_FILE", "NAS", "FTP", "SFTP"];
const TARGET_SOURCE_TYPES = ["STARROCKS"];

const DEFAULT_PAGE: PaginationInfo = {
  pageNo: 1,
  pageSize: 10,
  total: 0,
};

const DEFAULT_TASK_VALUES = {
  parserType: "SIMPLE_CSV",
  parserConfigJson: JSON.stringify(
    {
      header: true,
      delimiter: ",",
      columns: {
        lot_id: "LOT_ID",
        wafer_id: "WAFER_ID",
        item_name: "ITEM",
        item_value: "VALUE",
        item_unit: "UNIT",
      },
    },
    null,
    2,
  ),
  parseCharset: "UTF-8",
  parseMaxErrorRows: 100,
  parseFailFast: false,
  includePatterns: "*.wat,*.cp,*.txt,*.csv,*.dat,*.std",
  recursive: false,
  maxDepth: 3,
  fileStableSeconds: 0,
  enabled: true,
  discoveryMode: "FULL_SCAN",
  watermarkKey: "default",
  dedupStrategy: "PATH_SIZE_MTIME",
  checksumEnabled: false,
  maxFilesPerRun: 1000,
  lockTtlMinutes: 60,
  stagingDir: "/opt/seatunnel-web/staging/measurement",
  stagingFormat: "JSONL",
  stagingRetentionDays: 7,
  keepStagingFile: true,
  loadMode: "APPEND",
  loadBatchMode: "ONE_FILE_ONE_JOB",
  maxFilesPerParseRun: 100,
  retryParseFailed: false,
  retryLoadFailed: false,
  cleanupBeforeReload: false,
};

const SIMPLE_CSV_CONFIG = JSON.stringify(
  {
    header: true,
    delimiter: ",",
    columns: {
      lot_id: "LOT_ID",
      wafer_id: "WAFER_ID",
      item_name: "ITEM",
      item_value: "VALUE",
      item_unit: "UNIT",
    },
  },
  null,
  2,
);

const SIMPLE_TEXT_CONFIG = JSON.stringify(
  {
    includeBlankLine: false,
  },
  null,
  2,
);

const statusColor: Record<string, string> = {
  SUCCESS: "success",
  FAILED: "error",
  SKIPPED: "warning",
  RUNNING: "processing",
  PARSE_PENDING: "blue",
  DISCOVERED: "cyan",
  PARSING: "processing",
  PARSED: "green",
  PARSE_FAILED: "error",
  LOAD_PENDING: "gold",
  LOADING: "processing",
  LOADED: "success",
  LOAD_FAILED: "error",
};

const MeasurementFileSyncPage = () => {
  const [taskForm] = Form.useForm();
  const [tasks, setTasks] = useState<MeasurementFileTask[]>([]);
  const [runs, setRuns] = useState<MeasurementFileRun[]>([]);
  const [files, setFiles] = useState<MeasurementFileItem[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);

  const [taskPage, setTaskPage] = useState(DEFAULT_PAGE);
  const [runPage, setRunPage] = useState(DEFAULT_PAGE);
  const [filePage, setFilePage] = useState(DEFAULT_PAGE);

  const [taskLoading, setTaskLoading] = useState(false);
  const [runLoading, setRunLoading] = useState(false);
  const [fileLoading, setFileLoading] = useState(false);
  const [operationLoading, setOperationLoading] = useState(false);

  const [keyword, setKeyword] = useState("");
  const [fileStatus, setFileStatus] = useState<string>();
  const [selectedTask, setSelectedTask] = useState<MeasurementFileTask>();
  const [taskModalOpen, setTaskModalOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<MeasurementFileTask>();
  const [scanResult, setScanResult] = useState<MeasurementScanResult>();
  const [scanModalOpen, setScanModalOpen] = useState(false);
  const [parseResult, setParseResult] = useState<MeasurementParseLoadResult>();
  const [parseModalOpen, setParseModalOpen] = useState(false);
  const [parsePreview, setParsePreview] = useState<MeasurementParsePreview>();
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [schemaMissing, setSchemaMissing] = useState(false);
  const [schemaMessage, setSchemaMessage] = useState("");
  const [preflightResult, setPreflightResult] = useState<MeasurementPreflight>();
  const [preflightModalOpen, setPreflightModalOpen] = useState(false);
  const [sqlTemplate, setSqlTemplate] = useState<MeasurementSqlTemplate>();
  const [sqlModalTitle, setSqlModalTitle] = useState("SQL");
  const [sqlModalOpen, setSqlModalOpen] = useState(false);

  const fileDataSourceOptions = useMemo(() => {
    return dataSources
      .filter((item) => FILE_SOURCE_TYPES.includes(item.dbType || ""))
      .map((item) => ({
        label: `${item.name || "-"} (${item.dbType || "-"})`,
        value: Number(item.id),
      }));
  }, [dataSources]);

  const targetDataSourceOptions = useMemo(() => {
    return dataSources
      .filter((item) => TARGET_SOURCE_TYPES.includes(item.dbType || ""))
      .map((item) => ({
        label: `${item.name || "-"} (${item.dbType || "-"})`,
        value: Number(item.id),
      }));
  }, [dataSources]);

  const responseMessage = (response: { message?: string; msg?: string }) =>
    response.message || response.msg || "";

  const handleApiError = (
    response: { message?: string; msg?: string },
    fallback: string,
  ) => {
    const text = responseMessage(response) || fallback;
    if (
      text.includes("Measurement File Sync tables are missing") ||
      text.includes("Measurement File Sync schema is incomplete")
    ) {
      setSchemaMissing(true);
      setSchemaMessage("请先执行 Measurement File Sync 数据库初始化脚本。");
    }
    message.error(text);
  };

  const checkSchema = async () => {
    const response = await schemaCheckMeasurement();
    if (response.code !== 0) {
      handleApiError(response, "检查 Measurement File Sync 数据库初始化状态失败");
      return false;
    }
    const success = Boolean(response.data?.success);
    setSchemaMissing(!success);
    setSchemaMessage(
      success
        ? ""
        : response.data?.errors?.[0] || "请先执行 Measurement File Sync 数据库初始化脚本。",
    );
    return success;
  };

  const fetchDataSources = async () => {
    const response = await fetchAllDataSources();
    if (response.code === 0) {
      setDataSources(response.data || []);
    }
  };

  const fetchTaskList = async (pagePatch?: Partial<PaginationInfo>) => {
    setTaskLoading(true);
    try {
      const nextPage = {
        ...taskPage,
        ...pagePatch,
      };
      const response = await fetchMeasurementTasks({
        pageNo: nextPage.pageNo,
        pageSize: nextPage.pageSize,
        taskName: keyword || undefined,
      });
      if (response.code !== 0) {
        handleApiError(response, "查询任务失败");
        return;
      }
      const list = response.data?.bizData || [];
      setTasks(list);
      setTaskPage(response.data?.pagination || DEFAULT_PAGE);
      if (list.length && !list.some((item) => item.id === selectedTask?.id)) {
        setSelectedTask(list[0]);
      }
      if (!list.length) {
        setSelectedTask(undefined);
      }
    } finally {
      setTaskLoading(false);
    }
  };

  const fetchRunList = async (
    taskId?: number,
    pagePatch?: Partial<PaginationInfo>,
  ) => {
    if (!taskId) {
      setRuns([]);
      setRunPage(DEFAULT_PAGE);
      return;
    }
    setRunLoading(true);
    try {
      const nextPage = {
        ...runPage,
        ...pagePatch,
      };
      const response = await fetchMeasurementRuns({
        taskId,
        pageNo: nextPage.pageNo,
        pageSize: nextPage.pageSize,
      });
      if (response.code !== 0) {
        handleApiError(response, "查询 Run History 失败");
        return;
      }
      setRuns(response.data?.bizData || []);
      setRunPage(response.data?.pagination || DEFAULT_PAGE);
    } finally {
      setRunLoading(false);
    }
  };

  const fetchFileList = async (
    taskId?: number,
    pagePatch?: Partial<PaginationInfo>,
    nextStatus = fileStatus,
  ) => {
    if (!taskId) {
      setFiles([]);
      setFilePage(DEFAULT_PAGE);
      return;
    }
    setFileLoading(true);
    try {
      const nextPage = {
        ...filePage,
        ...pagePatch,
      };
      const response = await fetchMeasurementFiles({
        taskId,
        fileStatus: nextStatus,
        pageNo: nextPage.pageNo,
        pageSize: nextPage.pageSize,
      });
      if (response.code !== 0) {
        handleApiError(response, "查询文件清单失败");
        return;
      }
      setFiles(response.data?.bizData || []);
      setFilePage(response.data?.pagination || DEFAULT_PAGE);
    } finally {
      setFileLoading(false);
    }
  };

  useEffect(() => {
    fetchDataSources();
    checkSchema().then((schemaReady) => {
      if (schemaReady) {
        fetchTaskList();
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    fetchRunList(selectedTask?.id, { pageNo: 1 });
    fetchFileList(selectedTask?.id, { pageNo: 1 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedTask?.id]);

  const openCreateModal = () => {
    setEditingTask(undefined);
    taskForm.resetFields();
    taskForm.setFieldsValue(DEFAULT_TASK_VALUES);
    setTaskModalOpen(true);
  };

  const openEditModal = (record: MeasurementFileTask) => {
    setEditingTask(record);
    taskForm.resetFields();
    taskForm.setFieldsValue({
      ...DEFAULT_TASK_VALUES,
      ...record,
      minLastModifiedTime: record.minLastModifiedTime
        ? dayjs(record.minLastModifiedTime)
        : undefined,
    });
    setTaskModalOpen(true);
  };

  const closeTaskModal = () => {
    setTaskModalOpen(false);
    setEditingTask(undefined);
    taskForm.resetFields();
  };

  const saveTask = async () => {
    const values = await taskForm.validateFields();
    const payload = {
      ...values,
      minLastModifiedTime: values.minLastModifiedTime
        ? values.minLastModifiedTime.format("YYYY-MM-DD HH:mm:ss")
        : undefined,
    };
    const response = editingTask?.id
      ? await updateMeasurementTask(editingTask.id, payload)
      : await createMeasurementTask(payload);
    if (response.code !== 0) {
      handleApiError(response, "保存任务失败");
      return;
    }
    message.success("保存成功");
    closeTaskModal();
    fetchTaskList();
  };

  const removeTask = async (record: MeasurementFileTask) => {
    if (!record.id) return;
    const response = await deleteMeasurementTask(record.id);
    if (response.code !== 0) {
      handleApiError(response, "删除任务失败");
      return;
    }
    message.success("删除成功");
    fetchTaskList({ pageNo: 1 });
  };

  const runTestScan = async (record: MeasurementFileTask) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const response = await testScanMeasurementTask(record.id);
      if (response.code !== 0) {
        handleApiError(response, "测试扫描失败");
        return;
      }
      setScanResult(response.data);
      setScanModalOpen(true);
    } finally {
      setOperationLoading(false);
    }
  };

  const runDiscovery = async (record: MeasurementFileTask) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const response = await discoverMeasurementFiles(record.id);
      if (response.code !== 0) {
        handleApiError(response, "发现文件失败");
        return;
      }
      setScanResult(response.data);
      setScanModalOpen(true);
      message.success("发现文件运行已完成");
      fetchRunList(record.id, { pageNo: 1 });
      fetchFileList(record.id, { pageNo: 1 });
      fetchTaskList();
    } finally {
      setOperationLoading(false);
    }
  };

  const refreshTaskRelated = (taskId?: number) => {
    fetchRunList(taskId, { pageNo: 1 });
    fetchFileList(taskId, { pageNo: 1 });
    fetchTaskList();
  };

  const runParseOnly = async (record: MeasurementFileTask) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const response = await parseOnlyMeasurementFiles(record.id, {});
      if (response.code !== 0) {
        handleApiError(response, "解析失败");
        return;
      }
      setParseResult(response.data);
      setParseModalOpen(true);
      message.success("解析运行已完成");
      refreshTaskRelated(record.id);
    } finally {
      setOperationLoading(false);
    }
  };

  const runLoadParsed = async (record: MeasurementFileTask) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const preflight = await runPreflight(record, false);
      if (!preflight) return;
      if (!preflight.success) {
        setPreflightModalOpen(true);
        message.error("端到端预检查失败，请先处理 ERROR 项");
        return;
      }
      if (preflight.warnings?.length) {
        const confirmed = await confirmPreflightWarnings(preflight.warnings);
        if (!confirmed) return;
      }
      const response = await loadParsedMeasurementFiles(record.id, {});
      if (response.code !== 0) {
        handleApiError(response, "装载失败");
        return;
      }
      setParseResult(response.data);
      setParseModalOpen(true);
      message.success("装载运行已完成");
      refreshTaskRelated(record.id);
    } finally {
      setOperationLoading(false);
    }
  };

  const showSql = (title: string, template?: MeasurementSqlTemplate) => {
    setSqlModalTitle(title);
    setSqlTemplate(template);
    setSqlModalOpen(true);
  };

  const copyText = async (text?: string) => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      message.success("已复制");
    } catch {
      message.error("复制失败，请手动选择内容复制");
    }
  };

  const runPreflight = async (record: MeasurementFileTask, openModal = true) => {
    if (!record.id) return undefined;
    const response = await preflightMeasurementTask(record.id, {
      allowCreateStagingDir: true,
    });
    if (response.code !== 0) {
      handleApiError(response, "端到端预检查失败");
      return undefined;
    }
    setPreflightResult(response.data);
    if (openModal) {
      setPreflightModalOpen(true);
    }
    return response.data;
  };

  const runPreflightButton = async (record: MeasurementFileTask) => {
    setOperationLoading(true);
    try {
      await runPreflight(record, true);
    } finally {
      setOperationLoading(false);
    }
  };

  const confirmPreflightWarnings = (warnings: string[]) =>
    new Promise<boolean>((resolve) => {
      Modal.confirm({
        title: "预检查存在 Warning，确认继续？",
        content: warnings.join("\n"),
        okText: "继续",
        cancelText: "取消",
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });

  const openRecommendedDdl = async (record: MeasurementFileTask) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const response = await fetchRecommendedDdl(record.id);
      if (response.code !== 0) {
        handleApiError(response, "获取推荐建表 SQL 失败");
        return;
      }
      showSql("推荐 StarRocks 建表 SQL", response.data);
    } finally {
      setOperationLoading(false);
    }
  };

  const runParseAndLoad = async (
    record: MeasurementFileTask,
    payload: Record<string, unknown> = {},
  ) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const preflight = await runPreflight(record, false);
      if (!preflight) return;
      if (!preflight.success) {
        setPreflightModalOpen(true);
        message.error("端到端预检查失败，请先处理 ERROR 项");
        return;
      }
      if (preflight.warnings?.length) {
        const confirmed = await confirmPreflightWarnings(preflight.warnings);
        if (!confirmed) return;
      }
      const response = await parseAndLoadMeasurementFiles(record.id, payload);
      if (response.code !== 0) {
        handleApiError(response, "解析并装载失败");
        return;
      }
      setParseResult(response.data);
      setParseModalOpen(true);
      message.success("解析并装载运行已完成");
      refreshTaskRelated(record.id);
    } finally {
      setOperationLoading(false);
    }
  };

  const previewFile = async (record: MeasurementFileItem) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const response = await previewParseMeasurementFile(record.id, 20);
      if (response.code !== 0) {
        handleApiError(response, "预览解析失败");
        return;
      }
      setParsePreview(response.data);
      setPreviewModalOpen(true);
    } finally {
      setOperationLoading(false);
    }
  };

  const retryFile = async (record: MeasurementFileItem) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const response = await retryFailedMeasurementFile(record.id);
      if (response.code !== 0) {
        handleApiError(response, "重试失败");
        return;
      }
      setParseResult(response.data);
      setParseModalOpen(true);
      message.success("重试运行已完成");
      refreshTaskRelated(record.taskId);
    } finally {
      setOperationLoading(false);
    }
  };

  const openCleanupSql = async (record: MeasurementFileItem) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const response = await fetchCleanupSql(record.id);
      if (response.code !== 0) {
        handleApiError(response, "获取 cleanup SQL 失败");
        return;
      }
      showSql("文件级 cleanup SQL", response.data);
    } finally {
      setOperationLoading(false);
    }
  };

  const openRunHocon = async (record: MeasurementFileRun) => {
    if (!record.runId) return;
    if (record.generatedHocon) {
      showSql("本次生成 HOCON", {
        sql: record.generatedHocon,
        warning: "敏感字段已脱敏。",
      });
      return;
    }
    setOperationLoading(true);
    try {
      const response = await fetchRunHocon(record.runId);
      if (response.code !== 0) {
        handleApiError(response, "获取 HOCON 失败");
        return;
      }
      showSql("本次生成 HOCON", response.data);
    } finally {
      setOperationLoading(false);
    }
  };

  const markFileFailedAction = async (record: MeasurementFileItem) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const response = await markMeasurementFileFailed(record.id);
      if (response.code !== 0) {
        handleApiError(response, "标记失败失败");
        return;
      }
      setParseResult(response.data);
      setParseModalOpen(true);
      refreshTaskRelated(record.taskId);
    } finally {
      setOperationLoading(false);
    }
  };

  const resetFilePendingAction = async (record: MeasurementFileItem) => {
    if (!record.id) return;
    setOperationLoading(true);
    try {
      const response = await resetMeasurementFilePending(record.id);
      if (response.code !== 0) {
        handleApiError(response, "重置待处理失败");
        return;
      }
      setParseResult(response.data);
      setParseModalOpen(true);
      refreshTaskRelated(record.taskId);
    } finally {
      setOperationLoading(false);
    }
  };

  const parseAndLoadFile = (record: MeasurementFileItem) => {
    if (!selectedTask?.id || !record.id) return;
    const payload = {
      fileIds: [record.id],
      forceReload: false,
    };
    if (record.fileStatus === "LOADED") {
      Modal.confirm({
        title: "确认强制重跑？",
        content:
          "当前任务使用 APPEND 装载时，强制重跑可能产生重复数据。生产环境建议使用 StarRocks 主键表或 cleanup 策略。",
        okText: "强制重跑",
        cancelText: "取消",
        onOk: () =>
          runParseAndLoad(selectedTask, {
            ...payload,
            forceReload: true,
            forceReloadConfirmed: true,
          }),
      });
      return;
    }
    runParseAndLoad(selectedTask, payload);
  };

  const taskColumns = [
    {
      title: "任务名称",
      dataIndex: "taskName",
      width: 220,
      render: (text: string, record: MeasurementFileTask) => (
        <button
          type="button"
          className="text-left font-semibold text-[#1677ff]"
          onClick={() => setSelectedTask(record)}
        >
          {text || "-"}
        </button>
      ),
    },
    {
      title: "任务编码",
      dataIndex: "taskCode",
      width: 180,
      ellipsis: true,
    },
    {
      title: "Parser",
      dataIndex: "parserType",
      width: 100,
      render: (value: string) => <Tag>{value || "-"}</Tag>,
    },
    {
      title: "文件源",
      dataIndex: "sourceDatasourceName",
      width: 220,
      render: (_: string, record: MeasurementFileTask) => (
        <span>
          {record.sourceDatasourceName || "-"}
          <span className="ml-1 text-xs text-slate-400">
            {record.sourceType ? `(${record.sourceType})` : ""}
          </span>
        </span>
      ),
    },
    {
      title: "StarRocks 目标",
      dataIndex: "targetDatasourceName",
      width: 220,
      render: (_: string, record: MeasurementFileTask) => (
        <span>
          {record.targetDatasourceName || "-"}
          <span className="ml-1 text-xs text-slate-400">
            {record.targetDatabase && record.targetTable
              ? `${record.targetDatabase}.${record.targetTable}`
              : ""}
          </span>
        </span>
      ),
    },
    {
      title: "Root Path",
      dataIndex: "sourceRootPath",
      ellipsis: true,
      render: (value: string) => value || "使用数据源默认路径",
    },
    {
      title: "启用",
      dataIndex: "enabled",
      width: 80,
      render: (value: boolean) => (
        <Tag color={value ? "success" : "default"}>{value ? "启用" : "停用"}</Tag>
      ),
    },
    {
      title: "更新时间",
      dataIndex: "updateTime",
      width: 170,
    },
    {
      title: "操作",
      width: 480,
      fixed: "right" as const,
      render: (_: unknown, record: MeasurementFileTask) => (
        <Space size={6}>
          <Tooltip title="测试扫描">
            <Button
              size="small"
              icon={<SearchOutlined />}
              onClick={() => runTestScan(record)}
            />
          </Tooltip>
          <Tooltip title="发现文件">
            <Button
              size="small"
              type="primary"
              icon={<PlayCircleOutlined />}
              onClick={() => runDiscovery(record)}
            />
          </Tooltip>
          <Tooltip title="端到端预检查">
            <Button
              size="small"
              icon={<SearchOutlined />}
              onClick={() => runPreflightButton(record)}
            />
          </Tooltip>
          <Tooltip title="推荐建表 SQL">
            <Button size="small" onClick={() => openRecommendedDdl(record)}>
              DDL
            </Button>
          </Tooltip>
          <Tooltip title="解析">
            <Button
              size="small"
              icon={<SyncOutlined />}
              onClick={() => runParseOnly(record)}
            />
          </Tooltip>
          <Tooltip title="装载已解析">
            <Button
              size="small"
              icon={<UploadOutlined />}
              onClick={() => runLoadParsed(record)}
            />
          </Tooltip>
          <Tooltip title="解析并装载">
            <Button
              size="small"
              type="primary"
              ghost
              icon={<PlayCircleOutlined />}
              onClick={() => runParseAndLoad(record)}
            />
          </Tooltip>
          <Tooltip title="编辑">
            <Button
              size="small"
              icon={<EditOutlined />}
              onClick={() => openEditModal(record)}
            />
          </Tooltip>
          <Popconfirm
            title="确认删除该任务？"
            okText="删除"
            cancelText="取消"
            onConfirm={() => removeTask(record)}
          >
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const runColumns = [
    {
      title: "Run ID",
      dataIndex: "runId",
      ellipsis: true,
    },
    {
      title: "Batch ID",
      dataIndex: "batchId",
      ellipsis: true,
    },
    {
      title: "触发",
      dataIndex: "triggerType",
      width: 110,
    },
    {
      title: "阶段",
      dataIndex: "runPhase",
      width: 120,
      render: (value: string) => <Tag>{value || "DISCOVER"}</Tag>,
    },
    {
      title: "状态",
      dataIndex: "status",
      width: 110,
      render: (value: string) => <Tag color={statusColor[value] || "default"}>{value}</Tag>,
    },
    {
      title: "扫描",
      dataIndex: "scannedCount",
      width: 90,
    },
    {
      title: "新发现",
      dataIndex: "discoveredCount",
      width: 90,
    },
    {
      title: "跳过",
      dataIndex: "skippedCount",
      width: 90,
    },
    {
      title: "失败",
      dataIndex: "failedCount",
      width: 90,
    },
    {
      title: "选择文件",
      dataIndex: "selectedFileCount",
      width: 90,
    },
    {
      title: "解析文件",
      dataIndex: "parsedFileCount",
      width: 90,
    },
    {
      title: "装载文件",
      dataIndex: "loadedFileCount",
      width: 90,
    },
    {
      title: "解析失败",
      dataIndex: "parseFailedCount",
      width: 90,
    },
    {
      title: "装载失败",
      dataIndex: "loadFailedCount",
      width: 90,
    },
    {
      title: "解析行数",
      dataIndex: "parsedRowCount",
      width: 100,
    },
    {
      title: "装载行数",
      dataIndex: "loadedRowCount",
      width: 100,
    },
    {
      title: "错误原因",
      dataIndex: "errorMessage",
      ellipsis: true,
    },
    {
      title: "开始时间",
      dataIndex: "startTime",
      width: 170,
    },
    {
      title: "结束时间",
      dataIndex: "endTime",
      width: 170,
    },
    {
      title: "操作",
      width: 100,
      fixed: "right" as const,
      render: (_: unknown, record: MeasurementFileRun) => (
        <Button
          size="small"
          disabled={!record.generatedHocon}
          onClick={() => openRunHocon(record)}
        >
          HOCON
        </Button>
      ),
    },
  ];

  const fileColumns = [
    {
      title: "文件名",
      dataIndex: "fileName",
      width: 180,
      ellipsis: true,
    },
    {
      title: "相对路径",
      dataIndex: "relativePath",
      ellipsis: true,
    },
    {
      title: "大小",
      dataIndex: "fileSize",
      width: 110,
    },
    {
      title: "修改时间",
      dataIndex: "lastModifiedTime",
      width: 170,
    },
    {
      title: "状态",
      dataIndex: "fileStatus",
      width: 130,
      render: (value: string) => <Tag color={statusColor[value] || "default"}>{value}</Tag>,
    },
    {
      title: "解析行数",
      dataIndex: "parsedRowCount",
      width: 100,
    },
    {
      title: "装载行数",
      dataIndex: "loadedRowCount",
      width: 100,
    },
    {
      title: "Staging",
      dataIndex: "stagingFilePath",
      width: 220,
      ellipsis: true,
    },
    {
      title: "Batch ID",
      dataIndex: "batchId",
      width: 180,
      ellipsis: true,
    },
    {
      title: "Run ID",
      dataIndex: "runId",
      width: 180,
      ellipsis: true,
    },
    {
      title: "错误原因",
      dataIndex: "errorMessage",
      ellipsis: true,
    },
    {
      title: "操作",
      width: 310,
      fixed: "right" as const,
      render: (_: unknown, record: MeasurementFileItem) => (
        <Space size={6}>
          <Tooltip title="预览解析">
            <Button
              size="small"
              icon={<EyeOutlined />}
              onClick={() => previewFile(record)}
            />
          </Tooltip>
          <Tooltip title="解析并装载">
            <Button
              size="small"
              type="primary"
              ghost
              icon={<PlayCircleOutlined />}
              disabled={record.fileStatus === "LOADED"}
              onClick={() => parseAndLoadFile(record)}
            />
          </Tooltip>
          <Tooltip title="强制重跑">
            <Button
              size="small"
              danger
              disabled={record.fileStatus !== "LOADED"}
              onClick={() => parseAndLoadFile(record)}
            >
              重跑
            </Button>
          </Tooltip>
          <Tooltip title="重试失败">
            <Button
              size="small"
              icon={<SyncOutlined />}
              disabled={!["PARSE_FAILED", "LOAD_FAILED"].includes(record.fileStatus || "")}
              onClick={() => retryFile(record)}
            />
          </Tooltip>
          <Tooltip title="Cleanup SQL">
            <Button size="small" onClick={() => openCleanupSql(record)}>
              SQL
            </Button>
          </Tooltip>
          <Tooltip title="标记失败">
            <Button
              size="small"
              disabled={!["PARSING", "LOADING"].includes(record.fileStatus || "")}
              onClick={() => markFileFailedAction(record)}
            >
              失败
            </Button>
          </Tooltip>
          <Tooltip title="重置待处理">
            <Button
              size="small"
              disabled={!["PARSING", "LOADING", "PARSE_FAILED", "LOAD_FAILED"].includes(
                record.fileStatus || "",
              )}
              onClick={() => resetFilePendingAction(record)}
            >
              重置
            </Button>
          </Tooltip>
        </Space>
      ),
    },
  ];

  const scanFiles = scanResult?.files || [];
  const parseFiles = parseResult?.files || [];
  const previewRows = parsePreview?.rows || [];
  const previewErrorRows = parsePreview?.errorRows || [];

  return (
    <div className="measurement-file-sync-page">
      <div className="measurement-file-sync-page__inner">
        <div className="measurement-file-sync-page__header">
          <div>
            <h1 className="measurement-file-sync-page__title">量测文件同步任务</h1>
            <p className="measurement-file-sync-page__subtitle">
              发现量测文件，使用 parser 生成 JSONL staging，并通过 SeaTunnel 装载到 StarRocks。
            </p>
          </div>
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() =>
                checkSchema().then((schemaReady) => {
                  if (schemaReady) {
                    fetchTaskList();
                  }
                })
              }
            >
              刷新
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={schemaMissing}
              onClick={openCreateModal}
            >
              新建任务
            </Button>
          </Space>
        </div>

        {schemaMissing ? (
          <Alert
            showIcon
            type="error"
            className="mb-3"
            message="请先执行 Measurement File Sync 数据库初始化脚本。"
            description={schemaMessage}
          />
        ) : null}

        <section className="measurement-file-sync-page__panel">
          <div className="measurement-file-sync-page__toolbar">
            <Space>
              <Input.Search
                allowClear
                placeholder="按任务名称搜索"
                style={{ width: 260 }}
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                onSearch={() => fetchTaskList({ pageNo: 1 })}
              />
            </Space>
            <span className="text-sm text-slate-500">
              当前任务：{selectedTask?.taskName || "-"}
            </span>
          </div>
          <Table
            rowKey="id"
            size="small"
            loading={taskLoading || operationLoading}
            columns={taskColumns}
            dataSource={tasks}
            scroll={{ x: 1500 }}
            rowClassName={(record) =>
              record.id === selectedTask?.id ? "ant-table-row-selected" : ""
            }
            onRow={(record) => ({
              onClick: () => setSelectedTask(record),
            })}
            pagination={{
              current: taskPage.pageNo,
              pageSize: taskPage.pageSize,
              total: taskPage.total,
              showSizeChanger: true,
            }}
            onChange={(pagination) =>
              fetchTaskList({
                pageNo: pagination.current || 1,
                pageSize: pagination.pageSize || 10,
              })
            }
          />
        </section>

        <section className="measurement-file-sync-page__panel">
          <Tabs
            items={[
              {
                key: "runs",
                label: "Run History",
                children: (
                  <Table
                    rowKey="id"
                    size="small"
                    loading={runLoading}
                    columns={runColumns}
                    dataSource={runs}
                    scroll={{ x: 1800 }}
                    pagination={{
                      current: runPage.pageNo,
                      pageSize: runPage.pageSize,
                      total: runPage.total,
                      showSizeChanger: true,
                    }}
                    onChange={(pagination) =>
                      fetchRunList(selectedTask?.id, {
                        pageNo: pagination.current || 1,
                        pageSize: pagination.pageSize || 10,
                      })
                    }
                  />
                ),
              },
              {
                key: "files",
                label: "文件清单",
                children: (
                  <>
                    <div className="measurement-file-sync-page__toolbar">
                      <Space>
                        <Select
                          allowClear
                          placeholder="文件状态"
                          style={{ width: 180 }}
                          value={fileStatus}
                          options={[
                            "DISCOVERED",
                            "SKIPPED",
                            "PARSE_PENDING",
                            "PARSING",
                            "PARSED",
                            "PARSE_FAILED",
                            "LOAD_PENDING",
                            "LOADING",
                            "LOADED",
                            "LOAD_FAILED",
                            "FAILED",
                          ].map((value) => ({ label: value, value }))}
                          onChange={(value) => {
                            setFileStatus(value);
                            fetchFileList(selectedTask?.id, { pageNo: 1 }, value);
                          }}
                        />
                        <Button
                          icon={<ReloadOutlined />}
                          onClick={() => fetchFileList(selectedTask?.id)}
                        >
                          刷新清单
                        </Button>
                      </Space>
                    </div>
                    <Table
                      rowKey="id"
                      size="small"
                      loading={fileLoading}
                      columns={fileColumns}
                      dataSource={files}
                      scroll={{ x: 1700 }}
                      pagination={{
                        current: filePage.pageNo,
                        pageSize: filePage.pageSize,
                        total: filePage.total,
                        showSizeChanger: true,
                      }}
                      onChange={(pagination) =>
                        fetchFileList(selectedTask?.id, {
                          pageNo: pagination.current || 1,
                          pageSize: pagination.pageSize || 10,
                        })
                      }
                    />
                  </>
                ),
              },
            ]}
          />
        </section>
      </div>

      <Modal
        width={980}
        open={taskModalOpen}
        title={editingTask ? "编辑量测文件同步任务" : "新建量测文件同步任务"}
        okText="保存"
        cancelText="取消"
        onCancel={closeTaskModal}
        onOk={saveTask}
        destroyOnClose
      >
        <Form form={taskForm} layout="vertical" initialValues={DEFAULT_TASK_VALUES}>
          <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <Form.Item
              label="任务名称"
              name="taskName"
              rules={[{ required: true, message: "请输入任务名称" }]}
            >
              <Input maxLength={200} />
            </Form.Item>
            <Form.Item
              label="任务编码"
              name="taskCode"
              rules={[{ required: true, message: "请输入任务编码" }]}
            >
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item
              label="Parser Type"
              name="parserType"
              rules={[{ required: true, message: "请选择 Parser Type" }]}
            >
              <Select
                onChange={(value) => {
                  if (value === "SIMPLE_CSV") {
                    taskForm.setFieldValue("parserConfigJson", SIMPLE_CSV_CONFIG);
                  }
                  if (value === "SIMPLE_TEXT") {
                    taskForm.setFieldValue("parserConfigJson", SIMPLE_TEXT_CONFIG);
                  }
                }}
                options={["SIMPLE_CSV", "SIMPLE_TEXT", "WAT", "CP", "CUSTOM"].map((value) => ({
                  label: value,
                  value,
                }))}
              />
            </Form.Item>
            <Form.Item label="Parse Charset" name="parseCharset">
              <Input placeholder="UTF-8" />
            </Form.Item>
            <Form.Item label="Parse Max Error Rows" name="parseMaxErrorRows">
              <InputNumber className="!w-full" min={0} />
            </Form.Item>
            <Form.Item label="Parse Fail Fast" name="parseFailFast" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item
              label="文件源数据源"
              name="sourceDatasourceId"
              rules={[{ required: true, message: "请选择文件源数据源" }]}
            >
              <Select
                showSearch
                options={fileDataSourceOptions}
                optionFilterProp="label"
                placeholder="LOCAL_FILE / NAS / FTP / SFTP"
              />
            </Form.Item>
            <Form.Item label="任务 Root Path 覆盖" name="sourceRootPath">
              <Input placeholder="不填则使用数据源 rootPath" />
            </Form.Item>
            <Form.Item label="Include Patterns" name="includePatterns">
              <Input placeholder="*.wat,*.cp,*.std" />
            </Form.Item>
            <Form.Item label="Exclude Patterns" name="excludePatterns">
              <Input placeholder="*.tmp,*.swp" />
            </Form.Item>
            <Form.Item label="递归扫描" name="recursive" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item label="Max Depth" name="maxDepth">
              <InputNumber className="!w-full" min={0} />
            </Form.Item>
            <Form.Item label="Min Last Modified Time" name="minLastModifiedTime">
              <DatePicker className="!w-full" showTime />
            </Form.Item>
            <Form.Item label="File Stable Seconds" name="fileStableSeconds">
              <InputNumber className="!w-full" min={0} />
            </Form.Item>
            <Form.Item label="启用任务" name="enabled" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item label="Discovery Mode" name="discoveryMode">
              <Select
                options={["BY_LAST_MODIFIED", "BY_FILE_NAME", "FULL_SCAN"].map(
                  (value) => ({ label: value, value }),
                )}
              />
            </Form.Item>
            <Form.Item label="Watermark Key" name="watermarkKey">
              <Input />
            </Form.Item>
            <Form.Item label="Current Watermark" name="currentWatermark">
              <Input placeholder="按发现模式保存时间或文件名" />
            </Form.Item>
            <Form.Item label="Dedup Strategy" name="dedupStrategy">
              <Select
                options={["PATH_SIZE_MTIME", "PATH_CHECKSUM", "PATH_ONLY"].map(
                  (value) => ({ label: value, value }),
                )}
              />
            </Form.Item>
            <Form.Item label="Checksum Enabled" name="checksumEnabled" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item label="Max Files Per Run" name="maxFilesPerRun">
              <InputNumber className="!w-full" min={1} />
            </Form.Item>
            <Form.Item label="Lock TTL Minutes" name="lockTtlMinutes">
              <InputNumber className="!w-full" min={1} />
            </Form.Item>
            <Form.Item label="Schedule Cron" name="scheduleCron">
              <Input placeholder="预留字段，调度接入后使用" />
            </Form.Item>
            <Form.Item label="Staging Dir" name="stagingDir">
              <Input placeholder="/opt/seatunnel-web/staging/measurement" />
            </Form.Item>
            <Form.Item label="Staging Format" name="stagingFormat">
              <Select options={[{ label: "JSONL", value: "JSONL" }]} />
            </Form.Item>
            <Form.Item label="Keep Staging File" name="keepStagingFile" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item label="Target StarRocks 数据源" name="targetDatasourceId">
              <Select
                allowClear
                showSearch
                options={targetDataSourceOptions}
                optionFilterProp="label"
                placeholder="StarRocks"
              />
            </Form.Item>
            <Form.Item label="Target Database" name="targetDatabase">
              <Input placeholder="st_test" />
            </Form.Item>
            <Form.Item label="Target Table" name="targetTable">
              <Input placeholder="measurement_item_result" />
            </Form.Item>
            <Form.Item label="Load Mode" name="loadMode">
              <Select
                options={["APPEND", "UPSERT"].map((value) => ({
                  label: value,
                  value,
                  disabled: value === "UPSERT",
                }))}
              />
            </Form.Item>
            <Form.Item label="Load Batch Mode" name="loadBatchMode">
              <Select
                options={["ONE_FILE_ONE_JOB", "MULTI_FILE_ONE_JOB"].map((value) => ({
                  label: value,
                  value,
                  disabled: value === "MULTI_FILE_ONE_JOB",
                }))}
              />
            </Form.Item>
            <Form.Item label="StarRocks Node URLs" name="starrocksNodeUrls">
              <Input placeholder="starrocks.lab:8030" />
            </Form.Item>
            <Form.Item label="StarRocks Base URL" name="starrocksBaseUrl">
              <Input placeholder="jdbc:mysql://starrocks.lab:9030/" />
            </Form.Item>
            <Form.Item label="Max Files Per Parse Run" name="maxFilesPerParseRun">
              <InputNumber className="!w-full" min={1} />
            </Form.Item>
            <Form.Item label="Retry Parse Failed" name="retryParseFailed" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item label="Retry Load Failed" name="retryLoadFailed" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item label="Cleanup Before Reload" name="cleanupBeforeReload" valuePropName="checked">
              <Switch />
            </Form.Item>
          </div>
          <Form.Item label="Parser Config JSON" name="parserConfigJson">
            <Input.TextArea
              rows={8}
              placeholder={`SIMPLE_CSV 示例:\n${SIMPLE_CSV_CONFIG}\n\nSIMPLE_TEXT 示例:\n${SIMPLE_TEXT_CONFIG}`}
            />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Alert
            showIcon
            type="warning"
            message="staging_dir 必须是 SeaTunnel worker 可访问路径；APPEND + DUPLICATE KEY 表强制重跑可能产生重复数据，生产建议使用 StarRocks 主键表或 cleanup 策略。"
          />
        </Form>
      </Modal>

      <Modal
        width={980}
        open={scanModalOpen}
        title="扫描结果"
        footer={null}
        onCancel={() => setScanModalOpen(false)}
      >
        <div className="measurement-file-sync-page__stats">
          {[
            ["扫描文件", scanResult?.scannedCount || 0],
            ["新发现", scanResult?.discoveredCount || 0],
            ["跳过", scanResult?.skippedCount || 0],
            ["失败", scanResult?.failedCount || 0],
          ].map(([label, value]) => (
            <div className="measurement-file-sync-page__stat" key={label}>
              <div className="measurement-file-sync-page__stat-label">{label}</div>
              <div className="measurement-file-sync-page__stat-value">{value}</div>
            </div>
          ))}
        </div>
        {scanResult?.errorMessage ? (
          <div className="mb-3 rounded border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-700">
            {scanResult.errorMessage}
          </div>
        ) : null}
        <Table
          rowKey={(record) => `${record.fullPath || record.relativePath}-${record.fileStatus}`}
          size="small"
          columns={fileColumns}
          dataSource={scanFiles}
          scroll={{ x: 1200 }}
          pagination={{ pageSize: 8 }}
        />
      </Modal>

      <Modal
        width={980}
        open={parseModalOpen}
        title="解析装载结果"
        footer={null}
        onCancel={() => setParseModalOpen(false)}
      >
        <div className="measurement-file-sync-page__stats">
          {[
            ["选择文件", parseResult?.selectedFileCount || 0],
            ["解析文件", parseResult?.parsedFileCount || 0],
            ["装载文件", parseResult?.loadedFileCount || 0],
            ["解析失败", parseResult?.parseFailedCount || 0],
            ["装载失败", parseResult?.loadFailedCount || 0],
            ["解析行数", parseResult?.parsedRowCount || 0],
            ["装载行数", parseResult?.loadedRowCount || 0],
          ].map(([label, value]) => (
            <div className="measurement-file-sync-page__stat" key={label}>
              <div className="measurement-file-sync-page__stat-label">{label}</div>
              <div className="measurement-file-sync-page__stat-value">{value}</div>
            </div>
          ))}
        </div>
        {parseResult?.errorMessage ? (
          <div className="mb-3 rounded border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-700">
            {parseResult.errorMessage}
          </div>
        ) : null}
        <Table
          rowKey="id"
          size="small"
          columns={fileColumns}
          dataSource={parseFiles}
          scroll={{ x: 1700 }}
          pagination={{ pageSize: 8 }}
        />
      </Modal>

      <Modal
        width={980}
        open={previewModalOpen}
        title={`解析预览：${parsePreview?.fileName || "-"}`}
        footer={null}
        onCancel={() => setPreviewModalOpen(false)}
      >
        <div className="measurement-file-sync-page__stats">
          {[
            ["状态", parsePreview?.success ? "SUCCESS" : "FAILED"],
            ["解析行数", parsePreview?.rowCount || 0],
            ["错误行数", parsePreview?.errorRowCount || 0],
          ].map(([label, value]) => (
            <div className="measurement-file-sync-page__stat" key={label}>
              <div className="measurement-file-sync-page__stat-label">{label}</div>
              <div className="measurement-file-sync-page__stat-value">{value}</div>
            </div>
          ))}
        </div>
        {parsePreview?.errorMessage ? (
          <div className="mb-3 rounded border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-700">
            {parsePreview.errorMessage}
          </div>
        ) : null}
        <Tabs
          items={[
            {
              key: "rows",
              label: "Rows",
              children: (
                <Table
                  rowKey={(_, index) => String(index)}
                  size="small"
                  dataSource={previewRows}
                  columns={[
                    "row_no",
                    "lot_id",
                    "wafer_id",
                    "item_name",
                    "item_value",
                    "item_unit",
                    "raw_line",
                  ].map((key) => ({
                    title: key,
                    dataIndex: key,
                    ellipsis: true,
                  }))}
                  scroll={{ x: 1200 }}
                  pagination={{ pageSize: 8 }}
                />
              ),
            },
            {
              key: "errors",
              label: "Error Rows",
              children: (
                <Table
                  rowKey={(_, index) => String(index)}
                  size="small"
                  dataSource={previewErrorRows}
                  columns={[
                    "line_no",
                    "raw_line",
                    "error_message",
                  ].map((key) => ({
                    title: key,
                    dataIndex: key,
                    ellipsis: true,
                  }))}
                  scroll={{ x: 900 }}
                  pagination={{ pageSize: 8 }}
                />
              ),
            },
          ]}
        />
      </Modal>

      <Modal
        width={980}
        open={preflightModalOpen}
        title="端到端预检查"
        footer={null}
        onCancel={() => setPreflightModalOpen(false)}
      >
        <Alert
          showIcon
          className="mb-3"
          type={preflightResult?.success ? "success" : "error"}
          message={preflightResult?.success ? "预检查通过" : "预检查未通过"}
          description={
            preflightResult?.warnings?.length
              ? `Warnings: ${preflightResult.warnings.join("; ")}`
              : undefined
          }
        />
        <Table
          rowKey={(record) => record.name || record.message || ""}
          size="small"
          dataSource={preflightResult?.checks || []}
          columns={[
            {
              title: "检查项",
              dataIndex: "name",
              width: 220,
            },
            {
              title: "状态",
              dataIndex: "status",
              width: 110,
              render: (value: string) => (
                <Tag
                  color={
                    value === "PASS" ? "success" : value === "WARN" ? "warning" : "error"
                  }
                >
                  {value}
                </Tag>
              ),
            },
            {
              title: "说明",
              dataIndex: "message",
              ellipsis: true,
            },
            {
              title: "DDL",
              width: 110,
              render: (_: unknown, record) =>
                record.suggestedDdl ? (
                  <Button
                    size="small"
                    onClick={() =>
                      showSql("推荐 StarRocks 建表 SQL", {
                        sql: record.suggestedDdl,
                        warning: "目标表不存在时请先建表，再执行解析并装载。",
                      })
                    }
                  >
                    查看
                  </Button>
                ) : null,
            },
          ]}
          pagination={false}
        />
      </Modal>

      <Modal
        width={980}
        open={sqlModalOpen}
        title={sqlModalTitle}
        onCancel={() => setSqlModalOpen(false)}
        footer={[
          <Button key="copy" type="primary" onClick={() => copyText(sqlTemplate?.sql)}>
            复制
          </Button>,
          <Button key="close" onClick={() => setSqlModalOpen(false)}>
            关闭
          </Button>,
        ]}
      >
        {sqlTemplate?.warning ? (
          <Alert showIcon type="warning" className="mb-3" message={sqlTemplate.warning} />
        ) : null}
        <Input.TextArea rows={18} value={sqlTemplate?.sql || ""} readOnly />
      </Modal>
    </div>
  );
};

export default MeasurementFileSyncPage;
