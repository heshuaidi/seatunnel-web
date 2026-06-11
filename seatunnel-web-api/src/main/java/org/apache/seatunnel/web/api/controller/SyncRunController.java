package org.apache.seatunnel.web.api.controller;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncRunCoordinatorService;
import org.apache.seatunnel.web.spi.bean.dto.BackfillTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.PreviewHoconRequest;
import org.apache.seatunnel.web.spi.bean.dto.RunTaskRequest;
import org.apache.seatunnel.web.spi.bean.entity.Result;
import org.apache.seatunnel.web.spi.bean.vo.HoconPreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncRunController {

    @Resource
    private SyncRunCoordinatorService syncRunCoordinatorService;

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

    @GetMapping("/tasks/{taskCode}/watermark")
    public Result<List<WatermarkVO>> getWatermark(@PathVariable("taskCode") String taskCode) {
        return Result.buildSuc(syncRunCoordinatorService.getWatermark(taskCode));
    }
}
