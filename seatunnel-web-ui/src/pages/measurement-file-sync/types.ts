export interface CommonApiResponse<T> {
  code: number;
  data: T;
  message?: string;
  msg?: string;
}

export interface PaginationInfo {
  pageNo: number;
  pageSize: number;
  total: number;
}

export interface PageData<T> {
  bizData: T[];
  pagination: PaginationInfo;
}

export interface MeasurementFileTask {
  id?: number;
  taskName?: string;
  taskCode?: string;
  parserType?: "SIMPLE_CSV" | "SIMPLE_TEXT" | "WAT" | "CP" | "CUSTOM";
  parserConfigJson?: string;
  parseCharset?: string;
  parseMaxErrorRows?: number;
  parseFailFast?: boolean;
  sourceDatasourceId?: number;
  sourceDatasourceName?: string;
  sourceType?: string;
  sourceRootPath?: string;
  includePatterns?: string;
  excludePatterns?: string;
  recursive?: boolean;
  maxDepth?: number;
  minLastModifiedTime?: string;
  fileStableSeconds?: number;
  enabled?: boolean;
  discoveryMode?: "BY_LAST_MODIFIED" | "BY_FILE_NAME" | "FULL_SCAN";
  watermarkKey?: string;
  currentWatermark?: string;
  dedupStrategy?: "PATH_SIZE_MTIME" | "PATH_CHECKSUM" | "PATH_ONLY";
  checksumEnabled?: boolean;
  maxFilesPerRun?: number;
  lockTtlMinutes?: number;
  scheduleCron?: string;
  stagingDir?: string;
  stagingFormat?: "JSONL";
  stagingRetentionDays?: number;
  keepStagingFile?: boolean;
  targetDatasourceId?: number;
  targetDatasourceName?: string;
  targetDatabase?: string;
  targetTable?: string;
  loadMode?: "APPEND" | "UPSERT";
  loadBatchMode?: "ONE_FILE_ONE_JOB" | "MULTI_FILE_ONE_JOB";
  starrocksNodeUrls?: string;
  starrocksBaseUrl?: string;
  maxFilesPerParseRun?: number;
  retryParseFailed?: boolean;
  retryLoadFailed?: boolean;
  cleanupBeforeReload?: boolean;
  seatunnelClientId?: number;
  description?: string;
  createTime?: string;
  updateTime?: string;
}

export interface MeasurementFileRun {
  id?: number;
  runId?: string;
  batchId?: string;
  taskId?: number;
  triggerType?: string;
  status?: string;
  runPhase?: string;
  sourceDatasourceId?: number;
  scannedCount?: number;
  discoveredCount?: number;
  skippedCount?: number;
  failedCount?: number;
  selectedFileCount?: number;
  parsedFileCount?: number;
  loadedFileCount?: number;
  parseFailedCount?: number;
  loadFailedCount?: number;
  parsedRowCount?: number;
  loadedRowCount?: number;
  stagingDir?: string;
  targetDatasourceId?: number;
  targetDatabase?: string;
  targetTable?: string;
  generatedHocon?: string;
  errorMessage?: string;
  startTime?: string;
  endTime?: string;
}

export interface MeasurementFileItem {
  id?: number;
  taskId?: number;
  batchId?: string;
  runId?: string;
  sourceDatasourceId?: number;
  sourceType?: string;
  parserType?: string;
  rootPath?: string;
  relativePath?: string;
  fileName?: string;
  fullPath?: string;
  fileSize?: number;
  lastModifiedTime?: string;
  checksum?: string;
  checksumType?: string;
  fileStatus?: string;
  discoverTime?: string;
  parseTime?: string;
  loadTime?: string;
  stagingFilePath?: string;
  parsedRowCount?: number;
  loadedRowCount?: number;
  parseErrorCount?: number;
  parserConfigSnapshot?: string;
  loadJobId?: string;
  loadJobName?: string;
  errorMessage?: string;
}

export interface MeasurementScanResult {
  taskId?: number;
  runId?: string;
  batchId?: string;
  status?: string;
  scannedCount?: number;
  discoveredCount?: number;
  skippedCount?: number;
  failedCount?: number;
  errorMessage?: string;
  files?: MeasurementFileItem[];
}

export interface MeasurementParseLoadResult {
  taskId?: number;
  runId?: string;
  batchId?: string;
  runPhase?: string;
  status?: string;
  selectedFileCount?: number;
  parsedFileCount?: number;
  loadedFileCount?: number;
  parseFailedCount?: number;
  loadFailedCount?: number;
  skippedCount?: number;
  parsedRowCount?: number;
  loadedRowCount?: number;
  errorMessage?: string;
  generatedHocon?: string;
  files?: MeasurementFileItem[];
}

export interface MeasurementParsePreview {
  fileId?: number;
  taskId?: number;
  fileName?: string;
  parserType?: string;
  success?: boolean;
  rowCount?: number;
  errorRowCount?: number;
  errorMessage?: string;
  rows?: Record<string, unknown>[];
  errorRows?: Record<string, unknown>[];
}

export interface DataSourceRecord {
  id?: string;
  name?: string;
  dbType?: string;
  jdbcUrl?: string;
  connStatus?: string;
}

export interface MeasurementCheckItem {
  name?: string;
  status?: "PASS" | "WARN" | "ERROR";
  message?: string;
  suggestedDdl?: string;
}

export interface MeasurementPreflight {
  success?: boolean;
  errors?: string[];
  warnings?: string[];
  checks?: MeasurementCheckItem[];
}

export interface MeasurementSqlTemplate {
  sql?: string;
  warning?: string;
}
