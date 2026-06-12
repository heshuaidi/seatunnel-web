import HttpUtils from '@/utils/HttpUtils';
import type {
  ApiResult,
  CreateGenericJdbcStarRocksTaskRequest,
  CreateTaskFromTemplateResult,
  HoconPreviewVO,
  ListQuery,
  PaginationResult,
  RunResultVO,
  SyncAuditVO,
  SyncBatchVO,
  SyncCheckConfigVO,
  SyncCheckDiagnosticVO,
  SyncCheckResultVO,
  SyncHoconDiagnosticVO,
  SyncRangePreviewVO,
  SyncRunVO,
  SyncTaskDiagnosticVO,
  SyncTemplate,
  SyncWatermarkVO,
} from './types';

const syncPrefix = '/api/v1/sync';
const templatePrefix = `${syncPrefix}/templates`;

const queryString = (query?: Record<string, any>) => {
  const params = new URLSearchParams();
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  });
  const text = params.toString();
  return text ? `?${text}` : '';
};

export const syncControlApi = {
  listTemplates: (): Promise<ApiResult<SyncTemplate[]>> => {
    return HttpUtils.get(templatePrefix);
  },

  getTemplate: (templateCode: string): Promise<ApiResult<SyncTemplate>> => {
    return HttpUtils.get(`${templatePrefix}/${templateCode}`);
  },

  createGenericJdbcStarRocksTask: (
    data: CreateGenericJdbcStarRocksTaskRequest
  ): Promise<ApiResult<CreateTaskFromTemplateResult>> => {
    return HttpUtils.post(`${templatePrefix}/generic-jdbc-starrocks/create-task`, data);
  },

  diagnoseTask: (
    taskCode: string,
    data: Record<string, any>
  ): Promise<ApiResult<SyncTaskDiagnosticVO>> => {
    return HttpUtils.post(`${syncPrefix}/tasks/${taskCode}/diagnose`, data);
  },

  previewRange: (
    taskCode: string,
    data: Record<string, any>
  ): Promise<ApiResult<SyncRangePreviewVO>> => {
    return HttpUtils.post(`${syncPrefix}/tasks/${taskCode}/preview-range`, data);
  },

  diagnoseHocon: (
    taskCode: string,
    data: Record<string, any>
  ): Promise<ApiResult<SyncHoconDiagnosticVO>> => {
    return HttpUtils.post(`${syncPrefix}/tasks/${taskCode}/diagnose-hocon`, data);
  },

  diagnoseChecks: (
    taskCode: string,
    data: Record<string, any>
  ): Promise<ApiResult<SyncCheckDiagnosticVO>> => {
    return HttpUtils.post(`${syncPrefix}/tasks/${taskCode}/diagnose-checks`, data);
  },

  previewHocon: (
    taskCode: string,
    data: Record<string, any>
  ): Promise<ApiResult<HoconPreviewVO>> => {
    return HttpUtils.post(`${syncPrefix}/tasks/${taskCode}/preview-hocon`, data);
  },

  runTask: (
    taskCode: string,
    data: Record<string, any>
  ): Promise<ApiResult<RunResultVO>> => {
    return HttpUtils.post(`${syncPrefix}/tasks/${taskCode}/run`, data);
  },

  backfillTask: (
    taskCode: string,
    data: Record<string, any>
  ): Promise<ApiResult<RunResultVO>> => {
    return HttpUtils.post(`${syncPrefix}/tasks/${taskCode}/backfill`, data);
  },

  rerun: (runId: string, data: Record<string, any>): Promise<ApiResult<RunResultVO>> => {
    return HttpUtils.post(`${syncPrefix}/runs/${runId}/rerun`, data);
  },

  getRun: (runId: string): Promise<ApiResult<SyncRunVO>> => {
    return HttpUtils.get(`${syncPrefix}/runs/${runId}`);
  },

  listRuns: (taskCode: string, query?: ListQuery): Promise<PaginationResult<SyncRunVO>> => {
    return HttpUtils.get(`${syncPrefix}/tasks/${taskCode}/runs${queryString(query)}`);
  },

  listBatches: (
    taskCode: string,
    query?: ListQuery
  ): Promise<PaginationResult<SyncBatchVO>> => {
    return HttpUtils.get(`${syncPrefix}/tasks/${taskCode}/batches${queryString(query)}`);
  },

  getBatch: (batchId: string): Promise<ApiResult<SyncBatchVO>> => {
    return HttpUtils.get(`${syncPrefix}/batches/${batchId}`);
  },

  listRunAudits: (
    runId: string,
    query?: Pick<ListQuery, 'pageNo' | 'pageSize' | 'startTime' | 'endTime'>
  ): Promise<PaginationResult<SyncAuditVO>> => {
    return HttpUtils.get(`${syncPrefix}/runs/${runId}/audits${queryString(query)}`);
  },

  listBatchAudits: (
    batchId: string,
    query?: Pick<ListQuery, 'pageNo' | 'pageSize' | 'startTime' | 'endTime'>
  ): Promise<PaginationResult<SyncAuditVO>> => {
    return HttpUtils.get(`${syncPrefix}/batches/${batchId}/audits${queryString(query)}`);
  },

  listRunChecks: (runId: string): Promise<ApiResult<SyncCheckResultVO[]>> => {
    return HttpUtils.get(`${syncPrefix}/runs/${runId}/checks`);
  },

  getWatermark: (taskCode: string): Promise<ApiResult<SyncWatermarkVO[]>> => {
    return HttpUtils.get(`${syncPrefix}/tasks/${taskCode}/watermark`);
  },

  updateWatermark: (
    taskCode: string,
    data: Record<string, any>
  ): Promise<ApiResult<SyncWatermarkVO>> => {
    return HttpUtils.put(`${syncPrefix}/tasks/${taskCode}/watermark`, data);
  },

  listCheckConfigs: (taskCode: string): Promise<ApiResult<SyncCheckConfigVO[]>> => {
    return HttpUtils.get(`${syncPrefix}/tasks/${taskCode}/checks`);
  },

  createCheckConfig: (
    taskCode: string,
    data: SyncCheckConfigVO
  ): Promise<ApiResult<SyncCheckConfigVO>> => {
    return HttpUtils.post(`${syncPrefix}/tasks/${taskCode}/checks`, data);
  },

  updateCheckConfig: (
    taskCode: string,
    checkCode: string,
    data: SyncCheckConfigVO
  ): Promise<ApiResult<SyncCheckConfigVO>> => {
    return HttpUtils.put(`${syncPrefix}/tasks/${taskCode}/checks/${checkCode}`, data);
  },
};
