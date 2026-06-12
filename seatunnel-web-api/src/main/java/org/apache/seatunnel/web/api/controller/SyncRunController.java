package org.apache.seatunnel.web.api.controller;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.api.service.SyncCheckResultService;
import org.apache.seatunnel.web.api.service.SyncFileDiscoveryService;
import org.apache.seatunnel.web.api.service.SyncRunCoordinatorService;
import org.apache.seatunnel.web.api.service.SyncTaskService;
import org.apache.seatunnel.web.api.service.SyncTaskDiagnosticService;
import org.apache.seatunnel.web.common.enums.SyncCheckDatasourceType;
import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.common.enums.SyncCheckType;
import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.api.service.model.FileDiscoveryResult;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.spi.bean.dto.BackfillTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.DiscoverFilesRequest;
import org.apache.seatunnel.web.spi.bean.dto.PreviewHoconRequest;
import org.apache.seatunnel.web.spi.bean.dto.RunTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncCheckConfigRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncCheckDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncHoconDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncRangePreviewRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncRunRerunRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncTaskDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncWatermarkUpdateRequest;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.entity.Result;
import org.apache.seatunnel.web.spi.bean.vo.HoconPreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncAuditItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBatchListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncCheckDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncCheckConfigVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncCheckResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncFileDiscoveryResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncFileItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncFileRetryResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncHoconDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncRangePreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncRunListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncTaskDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncRunController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private SyncRunCoordinatorService syncRunCoordinatorService;

    @Resource
    private SyncTaskService syncTaskService;

    @Resource
    private SyncCheckConfigService syncCheckConfigService;

    @Resource
    private SyncCheckResultService syncCheckResultService;

    @Resource
    private SyncFileDiscoveryService syncFileDiscoveryService;

    @Resource
    private SyncTaskDiagnosticService syncTaskDiagnosticService;

    @PostMapping("/tasks/{taskCode}/diagnose")
    public Result<SyncTaskDiagnosticVO> diagnoseTask(
            @PathVariable("taskCode") String taskCode,
            @RequestBody(required = false) SyncTaskDiagnoseRequest request
    ) {
        return Result.buildSuc(syncTaskDiagnosticService.diagnoseTask(taskCode, request));
    }

    @PostMapping("/tasks/{taskCode}/preview-range")
    public Result<SyncRangePreviewVO> previewRange(
            @PathVariable("taskCode") String taskCode,
            @RequestBody(required = false) SyncRangePreviewRequest request
    ) {
        return Result.buildSuc(syncTaskDiagnosticService.previewRange(taskCode, request));
    }

    @PostMapping("/tasks/{taskCode}/diagnose-hocon")
    public Result<SyncHoconDiagnosticVO> diagnoseHocon(
            @PathVariable("taskCode") String taskCode,
            @RequestBody(required = false) SyncHoconDiagnoseRequest request
    ) {
        return Result.buildSuc(syncTaskDiagnosticService.diagnoseHocon(taskCode, request));
    }

    @PostMapping("/tasks/{taskCode}/diagnose-checks")
    public Result<SyncCheckDiagnosticVO> diagnoseChecks(
            @PathVariable("taskCode") String taskCode,
            @RequestBody(required = false) SyncCheckDiagnoseRequest request
    ) {
        return Result.buildSuc(syncTaskDiagnosticService.diagnoseChecks(taskCode, request));
    }

    @PostMapping("/tasks/{taskCode}/preview-hocon")
    public Result<HoconPreviewVO> previewHocon(
            @PathVariable("taskCode") String taskCode,
            @RequestBody(required = false) PreviewHoconRequest request
    ) {
        return Result.buildSuc(syncRunCoordinatorService.previewHocon(taskCode, request));
    }

    @PostMapping("/tasks/{taskCode}/run")
    public Result<RunResultVO> runTask(
            @PathVariable("taskCode") String taskCode,
            @RequestBody(required = false) RunTaskRequest request
    ) {
        return Result.buildSuc(syncRunCoordinatorService.runTask(taskCode, request));
    }

    @PostMapping("/tasks/{taskCode}/backfill")
    public Result<RunResultVO> backfillTask(
            @PathVariable("taskCode") String taskCode,
            @RequestBody BackfillTaskRequest request
    ) {
        return Result.buildSuc(syncRunCoordinatorService.backfillTask(taskCode, request));
    }

    @GetMapping("/runs/{runId}")
    public Result<RunDetailVO> getRun(@PathVariable("runId") String runId) {
        return Result.buildSuc(syncRunCoordinatorService.getRun(runId));
    }

    @PostMapping("/runs/{runId}/rerun")
    public Result<RunResultVO> rerun(
            @PathVariable("runId") String runId,
            @RequestBody(required = false) SyncRunRerunRequest request
    ) {
        return Result.buildSuc(syncRunCoordinatorService.rerun(runId, request));
    }

    @GetMapping("/tasks/{taskCode}/watermark")
    public Result<List<WatermarkVO>> getWatermark(@PathVariable("taskCode") String taskCode) {
        return Result.buildSuc(syncRunCoordinatorService.getWatermark(taskCode));
    }

    @PutMapping("/tasks/{taskCode}/watermark")
    public Result<WatermarkVO> updateWatermark(
            @PathVariable("taskCode") String taskCode,
            @RequestBody SyncWatermarkUpdateRequest request
    ) {
        return Result.buildSuc(syncTaskDiagnosticService.updateWatermark(taskCode, request));
    }

    @GetMapping("/tasks/{taskCode}/runs")
    public PaginationResult<SyncRunListItemVO> listRuns(
            @PathVariable("taskCode") String taskCode,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime
    ) {
        return syncTaskDiagnosticService.listRuns(taskCode, pageNo, pageSize, status, startTime, endTime);
    }

    @GetMapping("/tasks/{taskCode}/batches")
    public PaginationResult<SyncBatchListItemVO> listBatches(
            @PathVariable("taskCode") String taskCode,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime
    ) {
        return syncTaskDiagnosticService.listBatches(taskCode, pageNo, pageSize, status, startTime, endTime);
    }

    @GetMapping("/batches/{batchId}")
    public Result<SyncBatchListItemVO> getBatch(@PathVariable("batchId") String batchId) {
        return Result.buildSuc(syncTaskDiagnosticService.getBatch(batchId));
    }

    @GetMapping("/runs/{runId}/audits")
    public PaginationResult<SyncAuditItemVO> listRunAudits(
            @PathVariable("runId") String runId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime
    ) {
        return syncTaskDiagnosticService.listRunAudits(runId, pageNo, pageSize, startTime, endTime);
    }

    @GetMapping("/batches/{batchId}/audits")
    public PaginationResult<SyncAuditItemVO> listBatchAudits(
            @PathVariable("batchId") String batchId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime
    ) {
        return syncTaskDiagnosticService.listBatchAudits(batchId, pageNo, pageSize, startTime, endTime);
    }

    @PostMapping("/tasks/{taskCode}/discover-files")
    public Result<SyncFileDiscoveryResultVO> discoverFiles(
            @PathVariable("taskCode") String taskCode,
            @RequestBody(required = false) DiscoverFilesRequest request
    ) {
        FileDiscoveryResult result = syncFileDiscoveryService.discoverFiles(taskCode);
        Integer maxFiles = request == null ? null : request.getMaxFiles();
        return Result.buildSuc(toFileDiscoveryResultVO(result, maxFiles));
    }

    @GetMapping("/tasks/{taskCode}/files")
    public Result<List<SyncFileItemVO>> listTaskFiles(
            @PathVariable("taskCode") String taskCode,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "batchId", required = false) String batchId,
            @RequestParam(value = "runId", required = false) String runId,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "filePath", required = false) String filePath
    ) {
        SyncTaskEntity task = loadTask(taskCode);
        SyncFileItemStatus itemStatus = parseNullableEnum(SyncFileItemStatus.class, status, "status");
        return Result.buildSuc(syncFileDiscoveryService.listFiles(
                        task.getId(),
                        itemStatus,
                        batchId,
                        runId,
                        fileName,
                        filePath
                )
                .stream()
                .map(item -> toFileItemVO(task, item))
                .collect(Collectors.toList()));
    }

    @GetMapping("/batches/{batchId}/files")
    public Result<List<SyncFileItemVO>> listBatchFiles(@PathVariable("batchId") String batchId) {
        return Result.buildSuc(syncFileDiscoveryService.listFilesByBatchId(batchId)
                .stream()
                .map(item -> toFileItemVO(loadTask(item.getTaskId()), item))
                .collect(Collectors.toList()));
    }

    @PostMapping("/batches/{batchId}/files/retry")
    public Result<SyncFileRetryResultVO> retryBatchFiles(@PathVariable("batchId") String batchId) {
        List<SyncFileItemEntity> before = syncFileDiscoveryService.listFilesByBatchId(batchId);
        long failedBefore = before.stream()
                .filter(item -> item.getStatus() == SyncFileItemStatus.FAILED)
                .count();
        List<SyncFileItemEntity> files = syncFileDiscoveryService.retryFailedFiles(batchId);
        SyncFileRetryResultVO vo = new SyncFileRetryResultVO();
        vo.setBatchId(batchId);
        vo.setRetryCount((int) failedBefore);
        vo.setFiles(files.stream()
                .map(item -> toFileItemVO(loadTask(item.getTaskId()), item))
                .collect(Collectors.toList()));
        return Result.buildSuc(vo);
    }

    @GetMapping("/tasks/{taskCode}/checks")
    public Result<List<SyncCheckConfigVO>> listChecks(@PathVariable("taskCode") String taskCode) {
        SyncTaskEntity task = loadTask(taskCode);
        return Result.buildSuc(syncCheckConfigService.listByTaskId(task.getId())
                .stream()
                .map(item -> toCheckConfigVO(task, item))
                .collect(Collectors.toList()));
    }

    @PostMapping("/tasks/{taskCode}/checks")
    public Result<SyncCheckConfigVO> createCheck(
            @PathVariable("taskCode") String taskCode,
            @RequestBody SyncCheckConfigRequest request
    ) {
        SyncTaskEntity task = loadTask(taskCode);
        SyncCheckConfigEntity entity = toCheckConfigEntity(task, request);
        syncCheckConfigService.create(entity);
        return Result.buildSuc(toCheckConfigVO(task, entity));
    }

    @PutMapping("/tasks/{taskCode}/checks/{checkCode}")
    public Result<SyncCheckConfigVO> updateCheck(
            @PathVariable("taskCode") String taskCode,
            @PathVariable("checkCode") String checkCode,
            @RequestBody SyncCheckConfigRequest request
    ) {
        SyncTaskEntity task = loadTask(taskCode);
        SyncCheckConfigEntity existing = syncCheckConfigService.getByTaskIdAndCheckCode(task.getId(), checkCode);
        if (existing == null) {
            throw new ServiceException("Sync check config not found, taskCode="
                    + taskCode
                    + ", checkCode="
                    + checkCode);
        }
        SyncCheckConfigEntity entity = toCheckConfigEntity(task, request);
        entity.setId(existing.getId());
        entity.setTaskId(task.getId());
        entity.setCheckCode(existing.getCheckCode());
        entity.setCreateTime(existing.getCreateTime());
        syncCheckConfigService.update(entity);
        return Result.buildSuc(toCheckConfigVO(task, entity));
    }

    @GetMapping("/runs/{runId}/checks")
    public Result<List<SyncCheckResultVO>> listRunChecks(@PathVariable("runId") String runId) {
        return Result.buildSuc(syncCheckResultService.listByRunId(runId)
                .stream()
                .map(this::toCheckResultVO)
                .collect(Collectors.toList()));
    }

    private SyncTaskEntity loadTask(String taskCode) {
        SyncTaskEntity task = syncTaskService.getByTaskCode(taskCode);
        if (task == null) {
            throw new ServiceException("Sync task not found, taskCode=" + taskCode);
        }
        return task;
    }

    private SyncTaskEntity loadTask(Long taskId) {
        SyncTaskEntity task = syncTaskService.getById(taskId);
        if (task == null) {
            throw new ServiceException("Sync task not found, taskId=" + taskId);
        }
        return task;
    }

    private SyncCheckConfigEntity toCheckConfigEntity(SyncTaskEntity task, SyncCheckConfigRequest request) {
        if (request == null) {
            throw new ServiceException("Sync check config request is required");
        }
        return SyncCheckConfigEntity.builder()
                .taskId(task.getId())
                .checkCode(request.getCheckCode())
                .checkName(request.getCheckName())
                .checkType(parseEnum(SyncCheckType.class, request.getCheckType(), "checkType"))
                .datasourceType(parseNullableEnum(SyncCheckDatasourceType.class, request.getDatasourceType(), "datasourceType"))
                .datasourceId(request.getDatasourceId())
                .sqlText(request.getSqlText())
                .expectedOperator(parseNullableEnum(
                        SyncCheckExpectedOperator.class,
                        request.getExpectedOperator(),
                        "expectedOperator"
                ))
                .expectedValue(request.getExpectedValue())
                .compareToCheckCode(request.getCompareToCheckCode())
                .failOnMismatch(request.getFailOnMismatch())
                .enabled(request.getEnabled())
                .sortOrder(request.getSortOrder())
                .description(request.getDescription())
                .build();
    }

    private SyncCheckConfigVO toCheckConfigVO(SyncTaskEntity task, SyncCheckConfigEntity entity) {
        SyncCheckConfigVO vo = new SyncCheckConfigVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setTaskCode(task.getTaskCode());
        vo.setCheckCode(entity.getCheckCode());
        vo.setCheckName(entity.getCheckName());
        vo.setCheckType(entity.getCheckType() == null ? null : entity.getCheckType().getCode());
        vo.setDatasourceType(entity.getDatasourceType() == null ? null : entity.getDatasourceType().getCode());
        vo.setDatasourceId(entity.getDatasourceId());
        vo.setSqlText(entity.getSqlText());
        vo.setExpectedOperator(entity.getExpectedOperator() == null ? null : entity.getExpectedOperator().getCode());
        vo.setExpectedValue(entity.getExpectedValue());
        vo.setCompareToCheckCode(entity.getCompareToCheckCode());
        vo.setFailOnMismatch(entity.getFailOnMismatch());
        vo.setEnabled(entity.getEnabled());
        vo.setSortOrder(entity.getSortOrder());
        vo.setDescription(entity.getDescription());
        vo.setCreateTime(formatDate(entity.getCreateTime()));
        vo.setUpdateTime(formatDate(entity.getUpdateTime()));
        return vo;
    }

    private SyncCheckResultVO toCheckResultVO(SyncCheckResultEntity entity) {
        SyncCheckResultVO vo = new SyncCheckResultVO();
        vo.setId(entity.getId());
        vo.setRunId(entity.getRunId());
        vo.setBatchId(entity.getBatchId());
        vo.setTaskId(entity.getTaskId());
        vo.setTaskCode(entity.getTaskCode());
        vo.setCheckCode(entity.getCheckCode());
        vo.setCheckName(entity.getCheckName());
        vo.setCheckType(entity.getCheckType() == null ? null : entity.getCheckType().getCode());
        vo.setRenderedSql(entity.getRenderedSql());
        vo.setActualValue(entity.getActualValue());
        vo.setExpectedOperator(entity.getExpectedOperator() == null ? null : entity.getExpectedOperator().getCode());
        vo.setExpectedValue(entity.getExpectedValue());
        vo.setCompareToCheckCode(entity.getCompareToCheckCode());
        vo.setCompareToActualValue(entity.getCompareToActualValue());
        vo.setPassed(entity.getPassed());
        vo.setFailOnMismatch(entity.getFailOnMismatch());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setStartTime(formatDate(entity.getStartTime()));
        vo.setEndTime(formatDate(entity.getEndTime()));
        vo.setCreateTime(formatDate(entity.getCreateTime()));
        return vo;
    }

    private SyncFileDiscoveryResultVO toFileDiscoveryResultVO(FileDiscoveryResult result, Integer maxFiles) {
        SyncFileDiscoveryResultVO vo = new SyncFileDiscoveryResultVO();
        vo.setTaskId(result.getTaskId());
        vo.setTaskCode(result.getTaskCode());
        vo.setSourceType(result.getSourceType() == null ? null : result.getSourceType().getCode());
        vo.setStrategy(result.getStrategy() == null ? null : result.getStrategy().getCode());
        vo.setFilePath(result.getFilePath());
        vo.setFilePattern(result.getFilePattern());
        vo.setDiscoveredCount(result.getDiscoveredCount());
        vo.setSkippedCount(result.getSkippedCount());
        vo.setFailedCount(result.getFailedCount());
        vo.setMessage(result.getMessage());
        List<SyncFileItemEntity> files = result.getDiscoveredFiles() == null
                ? Collections.emptyList()
                : result.getDiscoveredFiles();
        if (maxFiles != null && maxFiles > 0 && files.size() > maxFiles) {
            files = files.subList(0, maxFiles);
        }
        SyncTaskEntity task = loadTask(result.getTaskId());
        vo.setDiscoveredFiles(files.stream()
                .map(item -> toFileItemVO(task, item))
                .collect(Collectors.toList()));
        return vo;
    }

    private SyncFileItemVO toFileItemVO(SyncTaskEntity task, SyncFileItemEntity entity) {
        SyncFileItemVO vo = new SyncFileItemVO();
        vo.setId(entity.getId());
        vo.setTaskId(entity.getTaskId());
        vo.setTaskCode(task == null ? null : task.getTaskCode());
        vo.setBatchId(entity.getBatchId());
        vo.setRunId(entity.getRunId());
        vo.setSourceType(entity.getSourceType() == null ? null : entity.getSourceType().getCode());
        vo.setFileSystem(entity.getFileSystem() == null ? null : entity.getFileSystem().getCode());
        vo.setFilePath(entity.getFilePath());
        vo.setFileName(entity.getFileName());
        vo.setRelativePath(entity.getRelativePath());
        vo.setFileSize(entity.getFileSize());
        vo.setLastModifiedTime(formatDate(entity.getLastModifiedTime()));
        vo.setChecksum(entity.getChecksum());
        vo.setDiscoveredTime(formatDate(entity.getDiscoveredTime()));
        vo.setStatus(entity.getStatus() == null ? null : entity.getStatus().getCode());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreateTime(formatDate(entity.getCreateTime()));
        vo.setUpdateTime(formatDate(entity.getUpdateTime()));
        return vo;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ServiceException("Missing required field: " + fieldName);
        }
        return parseNullableEnum(enumType, value, fieldName);
    }

    private <T extends Enum<T>> T parseNullableEnum(Class<T> enumType, String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("Unsupported " + fieldName + ": " + value);
        }
    }

    private String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return DATE_TIME_FORMATTER.format(
                LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())
        );
    }
}
