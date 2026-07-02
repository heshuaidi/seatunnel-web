import HttpUtils from "@/utils/HttpUtils";
import type {
  CommonApiResponse,
  DataSourceRecord,
  MeasurementFileItem,
  MeasurementFileRun,
  MeasurementFileTask,
  MeasurementPreflight,
  MeasurementParseLoadResult,
  MeasurementParsePreview,
  MeasurementScanResult,
  MeasurementSqlTemplate,
  PageData,
} from "./types";

const API_PREFIX = "/api/v1/measurement-file-sync";

export async function fetchMeasurementTasks(
  params: Record<string, unknown>,
): Promise<CommonApiResponse<PageData<MeasurementFileTask>>> {
  return HttpUtils.post(`${API_PREFIX}/page`, params);
}

export async function schemaCheckMeasurement(): Promise<
  CommonApiResponse<MeasurementPreflight>
> {
  return HttpUtils.get(`${API_PREFIX}/schema-check`);
}

export async function createMeasurementTask(
  payload: Record<string, unknown>,
): Promise<CommonApiResponse<MeasurementFileTask>> {
  return HttpUtils.post(API_PREFIX, payload);
}

export async function updateMeasurementTask(
  id: number,
  payload: Record<string, unknown>,
): Promise<CommonApiResponse<MeasurementFileTask>> {
  return HttpUtils.put(`${API_PREFIX}/${id}`, payload);
}

export async function deleteMeasurementTask(
  id: number,
): Promise<CommonApiResponse<boolean>> {
  return HttpUtils.delete(`${API_PREFIX}/${id}`);
}

export async function testScanMeasurementTask(
  taskId: number,
): Promise<CommonApiResponse<MeasurementScanResult>> {
  return HttpUtils.post(`${API_PREFIX}/${taskId}/test-scan`, {});
}

export async function discoverMeasurementFiles(
  taskId: number,
): Promise<CommonApiResponse<MeasurementScanResult>> {
  return HttpUtils.post(`${API_PREFIX}/${taskId}/discover?triggerType=MANUAL`, {});
}

export async function preflightMeasurementTask(
  taskId: number,
  payload: Record<string, unknown> = {},
): Promise<CommonApiResponse<MeasurementPreflight>> {
  return HttpUtils.post(`${API_PREFIX}/tasks/${taskId}/preflight`, payload);
}

export async function fetchRecommendedDdl(
  taskId: number,
): Promise<CommonApiResponse<MeasurementSqlTemplate>> {
  return HttpUtils.get(`${API_PREFIX}/${taskId}/recommended-ddl`);
}

export async function parseOnlyMeasurementFiles(
  taskId: number,
  payload: Record<string, unknown> = {},
): Promise<CommonApiResponse<MeasurementParseLoadResult>> {
  return HttpUtils.post(`${API_PREFIX}/${taskId}/parse-only`, payload);
}

export async function loadParsedMeasurementFiles(
  taskId: number,
  payload: Record<string, unknown> = {},
): Promise<CommonApiResponse<MeasurementParseLoadResult>> {
  return HttpUtils.post(`${API_PREFIX}/${taskId}/load-parsed`, payload);
}

export async function parseAndLoadMeasurementFiles(
  taskId: number,
  payload: Record<string, unknown> = {},
): Promise<CommonApiResponse<MeasurementParseLoadResult>> {
  return HttpUtils.post(`${API_PREFIX}/${taskId}/parse-and-load`, payload);
}

export async function previewParseMeasurementFile(
  fileId: number,
  maxRows = 20,
): Promise<CommonApiResponse<MeasurementParsePreview>> {
  return HttpUtils.post(`${API_PREFIX}/files/${fileId}/preview-parse?maxRows=${maxRows}`, {});
}

export async function retryFailedMeasurementFile(
  fileId: number,
): Promise<CommonApiResponse<MeasurementParseLoadResult>> {
  return HttpUtils.post(`${API_PREFIX}/files/${fileId}/retry-failed`, {});
}

export async function markMeasurementFileFailed(
  fileId: number,
): Promise<CommonApiResponse<MeasurementParseLoadResult>> {
  return HttpUtils.post(`${API_PREFIX}/files/${fileId}/mark-failed`, {});
}

export async function resetMeasurementFilePending(
  fileId: number,
): Promise<CommonApiResponse<MeasurementParseLoadResult>> {
  return HttpUtils.post(`${API_PREFIX}/files/${fileId}/reset-pending`, {});
}

export async function fetchCleanupSql(
  fileId: number,
): Promise<CommonApiResponse<MeasurementSqlTemplate>> {
  return HttpUtils.get(`${API_PREFIX}/files/${fileId}/cleanup-sql`);
}

export async function fetchRunHocon(
  runId: string,
): Promise<CommonApiResponse<MeasurementSqlTemplate>> {
  return HttpUtils.get(`${API_PREFIX}/runs/${runId}/hocon`);
}

export async function fetchMeasurementRuns(
  params: Record<string, unknown>,
): Promise<CommonApiResponse<PageData<MeasurementFileRun>>> {
  return HttpUtils.post(`${API_PREFIX}/runs/page`, params);
}

export async function fetchMeasurementFiles(
  params: Record<string, unknown>,
): Promise<CommonApiResponse<PageData<MeasurementFileItem>>> {
  return HttpUtils.post(`${API_PREFIX}/files/page`, params);
}

export async function fetchAllDataSources(): Promise<
  CommonApiResponse<DataSourceRecord[]>
> {
  return HttpUtils.get("/api/v1/data-source/all");
}
