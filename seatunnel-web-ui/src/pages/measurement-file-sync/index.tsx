import {
  DeleteOutlined,
  EditOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import {
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
  fetchMeasurementFiles,
  fetchMeasurementRuns,
  fetchMeasurementTasks,
  testScanMeasurementTask,
  updateMeasurementTask,
} from "./service";
import type {
  DataSourceRecord,
  MeasurementFileItem,
  MeasurementFileRun,
  MeasurementFileTask,
  MeasurementScanResult,
  PaginationInfo,
} from "./types";

const FILE_SOURCE_TYPES = ["LOCAL_FILE", "NAS", "FTP", "SFTP"];

const DEFAULT_PAGE: PaginationInfo = {
  pageNo: 1,
  pageSize: 10,
  total: 0,
};

const DEFAULT_TASK_VALUES = {
  parserType: "WAT",
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
};

const statusColor: Record<string, string> = {
  SUCCESS: "success",
  FAILED: "error",
  SKIPPED: "warning",
  RUNNING: "processing",
  PARSE_PENDING: "blue",
  DISCOVERED: "cyan",
  PARSED: "green",
  LOADED: "success",
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

  const fileDataSourceOptions = useMemo(() => {
    return dataSources
      .filter((item) => FILE_SOURCE_TYPES.includes(item.dbType || ""))
      .map((item) => ({
        label: `${item.name || "-"} (${item.dbType || "-"})`,
        value: Number(item.id),
      }));
  }, [dataSources]);

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
        message.error(response.message || response.msg || "查询任务失败");
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
        message.error(response.message || response.msg || "查询 Run History 失败");
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
        message.error(response.message || response.msg || "查询文件清单失败");
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
    fetchTaskList();
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
      message.error(response.message || response.msg || "保存任务失败");
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
      message.error(response.message || response.msg || "删除任务失败");
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
        message.error(response.message || response.msg || "测试扫描失败");
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
        message.error(response.message || response.msg || "发现文件失败");
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
      width: 250,
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
  ];

  const scanFiles = scanResult?.files || [];

  return (
    <div className="measurement-file-sync-page">
      <div className="measurement-file-sync-page__inner">
        <div className="measurement-file-sync-page__header">
          <div>
            <h1 className="measurement-file-sync-page__title">量测文件同步任务</h1>
            <p className="measurement-file-sync-page__subtitle">
              发现 LOCAL_FILE / NAS / FTP / SFTP 中的 WAT/CP 文件并写入清单，解析和入库留给后续 parser。
            </p>
          </div>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={() => fetchTaskList()}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
              新建任务
            </Button>
          </Space>
        </div>

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
            scroll={{ x: 1200 }}
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
                    scroll={{ x: 1300 }}
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
                            "LOAD_PENDING",
                            "LOADED",
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
                      scroll={{ x: 1300 }}
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
        width={880}
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
                options={["WAT", "CP", "CUSTOM"].map((value) => ({
                  label: value,
                  value,
                }))}
              />
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
          </div>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
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
    </div>
  );
};

export default MeasurementFileSyncPage;
