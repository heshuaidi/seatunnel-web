export interface ApiResult<T> {
  code: number;
  msg?: string;
  message?: string;
  data: T;
}

export interface Pagination {
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface PaginationData<T> {
  bizData: T[];
  pagination: Pagination;
}

export interface PaginationResult<T> {
  code: number;
  msg?: string;
  message?: string;
  data: PaginationData<T>;
}

export interface SyncTemplate {
  templateCode?: string;
  templateName?: string;
  description?: string;
  sourceType?: string;
  sinkType?: string;
  incrementalStrategy?: string;
  hoconTemplate?: string;
  requiredVariables?: string[];
  docs?: string[];
}

export interface CreateGenericJdbcStarRocksTaskRequest {
  taskCode?: string;
  taskName?: string;
  clientId?: number;
  description?: string;
  incrementalStrategy?: 'UPDATE_TIME_RANGE' | 'ID_RANGE';
  watermarkField?: string;
  watermarkFieldType?: 'DATETIME' | 'LONG';
  startValue?: string;
  lookbackSeconds?: number;
  maxBatchSeconds?: number;
  sourceJdbcUrl?: string;
  sourceJdbcDriver?: string;
  sourceUsername?: string;
  sourcePassword?: string;
  sourceQuery?: string;
  starrocksNodeUrls?: string;
  starrocksBaseUrl?: string;
  starrocksUsername?: string;
  starrocksPassword?: string;
  starrocksDatabase?: string;
  starrocksTable?: string;
  starrocksErrorTable?: string;
  sourceDatasourceId?: number;
  sinkDatasourceId?: number;
  enableDefaultChecks?: boolean;
}

export interface CreateTaskFromTemplateResult {
  taskId?: number;
  taskCode?: string;
  versionId?: number;
  incrementalConfigId?: number;
  watermarkId?: number;
  createdCheckCount?: number;
  warnings?: string[];
  nextActions?: string[];
}

export interface SyncTaskDiagnosticVO {
  task?: Record<string, any>;
  version?: Record<string, any>;
  incrementalConfig?: Record<string, any>;
  watermark?: Record<string, any>;
  rangePreview?: SyncRangePreviewVO;
  hocon?: SyncHoconDiagnosticVO;
  checks?: SyncCheckDiagnosticVO;
  client?: Record<string, any>;
  diagnostics?: {
    level?: 'OK' | 'WARN' | 'ERROR' | string;
    messages?: string[];
  };
}

export interface SyncRangePreviewVO {
  taskCode?: string;
  strategy?: string;
  watermarkKey?: string;
  currentWatermark?: string;
  startValue?: string;
  endValue?: string;
  startTime?: string;
  endTime?: string;
  lookbackApplied?: boolean;
  maxBatchSecondsApplied?: boolean;
  willAdvanceWatermark?: boolean;
  warnings?: string[];
}

export interface SyncHoconDiagnosticVO {
  renderable?: boolean;
  missingVariables?: string[];
  renderedHash?: string;
  renderedHocon?: string;
  renderedHoconPreview?: string;
  variablesUsed?: string[];
  maskedParams?: Record<string, any>;
  errorMessage?: string;
}

export interface SyncCheckDiagnosticItem {
  checkCode?: string;
  checkName?: string;
  checkType?: string;
  datasourceId?: number;
  enabled?: boolean;
  renderable?: boolean;
  missingVariables?: string[];
  renderedSqlPreview?: string;
  executeSql?: boolean;
  executed?: boolean;
  actualValue?: string;
  passed?: boolean;
  errorMessage?: string;
}

export interface SyncCheckDiagnosticVO {
  checkCount?: number;
  enabledCheckCount?: number;
  missingDatasourceIds?: string[];
  renderable?: boolean;
  checkSqlPreview?: string[];
  checks?: SyncCheckDiagnosticItem[];
  maskedParams?: Record<string, any>;
}

export interface HoconPreviewVO {
  taskId?: number;
  taskCode?: string;
  taskVersionId?: number;
  renderedHocon?: string;
  hoconHash?: string;
}

export interface SyncRunVO {
  runId?: string;
  batchId?: string;
  taskCode?: string;
  status?: string;
  runStatus?: string;
  batchStatus?: string;
  triggerType?: string;
  seatunnelJobId?: string;
  seatunnelJobName?: string;
  submitTime?: string;
  startTime?: string;
  endTime?: string;
  sourceCount?: number;
  sinkCount?: number;
  errorCount?: number;
  errorMessage?: string;
  generatedHocon?: string;
  createTime?: string;
  updateTime?: string;
  audits?: SyncAuditVO[];
}

export interface RunResultVO {
  runId?: string;
  batchId?: string;
  taskId?: number;
  taskCode?: string;
  taskVersionId?: number;
  seatunnelJobId?: string;
  seatunnelJobName?: string;
  runStatus?: string;
  batchStatus?: string;
  watermarkAdvanced?: boolean;
  errorMessage?: string;
}

export interface SyncBatchVO {
  batchId?: string;
  taskCode?: string;
  status?: string;
  batchStartValue?: string;
  batchEndValue?: string;
  batchStartTime?: string;
  batchEndTime?: string;
  sourceCount?: number;
  sinkCount?: number;
  errorCount?: number;
  errorMessage?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SyncWatermarkVO {
  id?: number;
  taskId?: number;
  taskCode?: string;
  watermarkKey?: string;
  currentValue?: string;
  previousValue?: string;
  currentValueType?: string;
  lastSuccessRunId?: number;
  lastSuccessBatchId?: string;
  updateTime?: string;
}

export interface SyncAuditVO {
  id?: number;
  runId?: string;
  batchId?: string;
  taskId?: number;
  taskCode?: string;
  eventType?: string;
  eventLevel?: string;
  eventMessage?: string;
  detailJson?: string;
  createTime?: string;
}

export interface SyncCheckConfigVO {
  id?: number;
  taskId?: number;
  taskCode?: string;
  checkCode?: string;
  checkName?: string;
  checkType?: string;
  datasourceType?: string;
  datasourceId?: number;
  sqlText?: string;
  expectedOperator?: string;
  expectedValue?: string;
  compareToCheckCode?: string;
  failOnMismatch?: boolean;
  enabled?: boolean;
  sortOrder?: number;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SyncCheckResultVO {
  id?: number;
  runId?: string;
  batchId?: string;
  taskId?: number;
  taskCode?: string;
  checkCode?: string;
  checkName?: string;
  checkType?: string;
  renderedSql?: string;
  actualValue?: string;
  expectedOperator?: string;
  expectedValue?: string;
  compareToCheckCode?: string;
  compareToActualValue?: string;
  passed?: boolean;
  failOnMismatch?: boolean;
  errorMessage?: string;
  startTime?: string;
  endTime?: string;
  createTime?: string;
}

export interface ListQuery {
  pageNo?: number;
  pageSize?: number;
  status?: string;
  startTime?: string;
  endTime?: string;
}
