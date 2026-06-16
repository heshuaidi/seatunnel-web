import HttpUtils from "@/utils/HttpUtils";
import type {
  CommonApiResponse,
  DataSourceRecord,
  MeasurementFileItem,
  MeasurementFileRun,
  MeasurementFileTask,
  MeasurementScanResult,
  PageData,
} from "./types";

const API_PREFIX = "/api/v1/measurement-file-sync";

export async function fetchMeasurementTasks(
  params: Record<string, unknown>,
): Promise<CommonApiResponse<PageData<MeasurementFileTask>>> {
  return HttpUtils.post(`${API_PREFIX}/page`, params);
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
