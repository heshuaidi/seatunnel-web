package org.apache.seatunnel.web.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.api.service.MeasurementFileDiscoveryService;
import org.apache.seatunnel.web.api.service.MeasurementFileSyncTaskService;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileQueryDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileRunQueryDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileSyncTaskDTO;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.entity.Result;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileRunVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileScanResultVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileSyncTaskVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileVO;
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

    @PostMapping
    @Operation(summary = "createMeasurementFileSyncTask")
    public Result<MeasurementFileSyncTaskVO> create(@RequestBody MeasurementFileSyncTaskDTO dto) {
        return Result.buildSuc(taskService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "updateMeasurementFileSyncTask")
    public Result<MeasurementFileSyncTaskVO> update(
            @PathVariable("id") Long id,
            @RequestBody MeasurementFileSyncTaskDTO dto
    ) {
        return Result.buildSuc(taskService.update(id, dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "getMeasurementFileSyncTask")
    public Result<MeasurementFileSyncTaskVO> get(@PathVariable("id") Long id) {
        return Result.buildSuc(taskService.get(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "deleteMeasurementFileSyncTask")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        return Result.buildSuc(taskService.delete(id));
    }

    @PostMapping("/page")
    @Operation(summary = "pageMeasurementFileSyncTask")
    public PaginationResult<MeasurementFileSyncTaskVO> page(@RequestBody MeasurementFileSyncTaskDTO dto) {
        return taskService.page(dto);
    }

    @PostMapping("/{taskId}/test-scan")
    @Operation(summary = "testScanMeasurementFiles")
    public Result<MeasurementFileScanResultVO> testScan(@PathVariable("taskId") Long taskId) {
        return Result.buildSuc(discoveryService.testScan(taskId));
    }

    @PostMapping("/{taskId}/discover")
    @Operation(summary = "discoverMeasurementFiles")
    public Result<MeasurementFileScanResultVO> discover(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "triggerType", required = false) String triggerType
    ) {
        return Result.buildSuc(discoveryService.discover(taskId, parseTriggerType(triggerType)));
    }

    @PostMapping("/runs/page")
    @Operation(summary = "pageMeasurementFileRuns")
    public PaginationResult<MeasurementFileRunVO> runPage(@RequestBody MeasurementFileRunQueryDTO dto) {
        return discoveryService.runPage(dto);
    }

    @PostMapping("/files/page")
    @Operation(summary = "pageMeasurementFiles")
    public PaginationResult<MeasurementFileVO> filePage(@RequestBody MeasurementFileQueryDTO dto) {
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
