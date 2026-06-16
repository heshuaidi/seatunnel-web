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
  parserType?: "WAT" | "CP" | "CUSTOM";
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
  sourceDatasourceId?: number;
  scannedCount?: number;
  discoveredCount?: number;
  skippedCount?: number;
  failedCount?: number;
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

export interface DataSourceRecord {
  id?: string;
  name?: string;
  dbType?: string;
  jdbcUrl?: string;
  connStatus?: string;
}
