package org.apache.seatunnel.web.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.api.service.MeasurementFileDiscoveryService;
import org.apache.seatunnel.web.api.service.MeasurementFileParseLoadService;
import org.apache.seatunnel.web.api.service.MeasurementFileSchemaService;
import org.apache.seatunnel.web.api.service.MeasurementFileSyncTaskService;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileQueryDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileRunQueryDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileSyncTaskDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementParseLoadRequestDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementPreflightRequestDTO;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.entity.Result;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementParseLoadResultVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementParsePreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileRunVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileScanResultVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileSyncTaskVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementPreflightVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementSqlTemplateVO;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "MEASUREMENT_FILE_SYNC_TAG")
@RequestMapping("/api/v1/measurement-file-sync")
public class MeasurementFileSyncController {

    @Resource
    private MeasurementFileSyncTaskService taskService;

    @Resource
    private MeasurementFileDiscoveryService discoveryService;

    @Resource
    private MeasurementFileParseLoadService parseLoadService;

    @Resource
    private MeasurementFileSchemaService schemaService;

    @GetMapping("/schema-check")
    @Operation(summary = "checkMeasurementFileSyncSchema")
    public Result<MeasurementPreflightVO> schemaCheck() {
        return Result.buildSuc(schemaService.check());
    }

    @PostMapping
    @Operation(summary = "createMeasurementFileSyncTask")
    public Result<MeasurementFileSyncTaskVO> create(@RequestBody MeasurementFileSyncTaskDTO dto) {
        schemaService.checkOrThrow();
        return Result.buildSuc(taskService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "updateMeasurementFileSyncTask")
    public Result<MeasurementFileSyncTaskVO> update(
            @PathVariable("id") Long id,
            @RequestBody MeasurementFileSyncTaskDTO dto
    ) {
        schemaService.checkOrThrow();
        return Result.buildSuc(taskService.update(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "getMeasurementFileSyncTask")
    public Result<MeasurementFileSyncTaskVO> get(@PathVariable("id") Long id) {
        schemaService.checkOrThrow();
        return Result.buildSuc(taskService.get(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "deleteMeasurementFileSyncTask")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        schemaService.checkOrThrow();
        return Result.buildSuc(taskService.delete(id));
    }

    @PostMapping("/page")
    @Operation(summary = "pageMeasurementFileSyncTask")
    public PaginationResult<MeasurementFileSyncTaskVO> page(@RequestBody MeasurementFileSyncTaskDTO dto) {
        schemaService.checkOrThrow();
        return taskService.page(dto);
    }

    @PostMapping("/{taskId}/test-scan")
    @Operation(summary = "testScanMeasurementFiles")
    public Result<MeasurementFileScanResultVO> testScan(@PathVariable("taskId") Long taskId) {
        schemaService.checkOrThrow();
        return Result.buildSuc(discoveryService.testScan(taskId));
    }

    @PostMapping("/{taskId}/discover")
    @Operation(summary = "discoverMeasurementFiles")
    public Result<MeasurementFileScanResultVO> discover(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "triggerType", required = false) String triggerType
    ) {
        schemaService.checkOrThrow();
        return Result.buildSuc(discoveryService.discover(taskId, parseTriggerType(triggerType)));
    }

    @PostMapping("/tasks/{taskId}/preflight")
    @Operation(summary = "preflightMeasurementFileParseLoad")
    public Result<MeasurementPreflightVO> preflight(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) MeasurementPreflightRequestDTO request
    ) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.preflight(taskId, request));
    }

    @GetMapping("/{taskId}/recommended-ddl")
    @Operation(summary = "recommendedMeasurementStarRocksDdl")
    public Result<MeasurementSqlTemplateVO> recommendedDdl(@PathVariable("taskId") Long taskId) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.recommendedDdl(taskId));
    }

    @PostMapping("/{taskId}/parse-only")
    @Operation(summary = "parseMeasurementFiles")
    public Result<MeasurementParseLoadResultVO> parseOnly(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) MeasurementParseLoadRequestDTO request
    ) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.parseOnly(taskId, request));
    }

    @PostMapping("/{taskId}/load-parsed")
    @Operation(summary = "loadParsedMeasurementFiles")
    public Result<MeasurementParseLoadResultVO> loadParsed(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) MeasurementParseLoadRequestDTO request
    ) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.loadParsed(taskId, request));
    }

    @PostMapping("/{taskId}/parse-and-load")
    @Operation(summary = "parseAndLoadMeasurementFiles")
    public Result<MeasurementParseLoadResultVO> parseAndLoad(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) MeasurementParseLoadRequestDTO request
    ) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.parseAndLoad(taskId, request));
    }

    @PostMapping("/files/{fileId}/preview-parse")
    @Operation(summary = "previewParseMeasurementFile")
    public Result<MeasurementParsePreviewVO> previewParse(
            @PathVariable("fileId") Long fileId,
            @RequestParam(value = "maxRows", required = false) Integer maxRows
    ) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.previewParse(fileId, maxRows));
    }

    @PostMapping("/files/{fileId}/retry-failed")
    @Operation(summary = "retryFailedMeasurementFile")
    public Result<MeasurementParseLoadResultVO> retryFailedFile(@PathVariable("fileId") Long fileId) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.retryFailedFile(fileId));
    }

    @PostMapping("/files/{fileId}/mark-failed")
    @Operation(summary = "markMeasurementFileFailed")
    public Result<MeasurementParseLoadResultVO> markFileFailed(@PathVariable("fileId") Long fileId) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.markFileFailed(fileId));
    }

    @PostMapping("/files/{fileId}/reset-pending")
    @Operation(summary = "resetMeasurementFilePending")
    public Result<MeasurementParseLoadResultVO> resetFilePending(@PathVariable("fileId") Long fileId) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.resetFilePending(fileId));
    }

    @PostMapping("/{taskId}/repair-stale")
    @Operation(summary = "repairStaleMeasurementFiles")
    public Result<MeasurementParseLoadResultVO> repairStaleFiles(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "timeoutMinutes", required = false) Integer timeoutMinutes,
            @RequestParam(value = "resetToPending", required = false) Boolean resetToPending
    ) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.repairStaleFiles(taskId, timeoutMinutes, resetToPending));
    }

    @GetMapping("/files/{fileId}/cleanup-sql")
    @Operation(summary = "cleanupSqlForMeasurementFile")
    public Result<MeasurementSqlTemplateVO> cleanupSql(@PathVariable("fileId") Long fileId) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.cleanupSql(fileId));
    }

    @GetMapping("/runs/{runId}/hocon")
    @Operation(summary = "generatedHoconForMeasurementRun")
    public Result<MeasurementSqlTemplateVO> generatedHocon(@PathVariable("runId") String runId) {
        schemaService.checkOrThrow();
        return Result.buildSuc(parseLoadService.generatedHocon(runId));
    }

    @PostMapping("/runs/page")
    @Operation(summary = "pageMeasurementFileRuns")
    public PaginationResult<MeasurementFileRunVO> runPage(@RequestBody MeasurementFileRunQueryDTO dto) {
        schemaService.checkOrThrow();
        return discoveryService.runPage(dto);
    }

    @PostMapping("/files/page")
    @Operation(summary = "pageMeasurementFiles")
    public PaginationResult<MeasurementFileVO> filePage(@RequestBody MeasurementFileQueryDTO dto) {
        schemaService.checkOrThrow();
        return discoveryService.filePage(dto);
    }

    private SyncTriggerType parseTriggerType(String triggerType) {
        if (StringUtils.isBlank(triggerType)) {
            return SyncTriggerType.MANUAL;
        }
        try {
            return SyncTriggerType.valueOf(triggerType.trim().toUpperCase());
        } catch (Exception e) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "triggerType");
        }
    }
}
