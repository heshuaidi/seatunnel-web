package org.apache.seatunnel.web.api.controller;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.BatchLinkUpIncrementalService;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalConfigRequest;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalPreviewRequest;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalRunRequest;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalSqlTestRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncWatermarkUpdateRequest;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.entity.Result;
import org.apache.seatunnel.web.spi.bean.vo.BatchLinkUpIncrementalConfigVO;
import org.apache.seatunnel.web.spi.bean.vo.BatchLinkUpIncrementalContextVO;
import org.apache.seatunnel.web.spi.bean.vo.BatchLinkUpIncrementalHoconPreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBatchListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncRunListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/batch-link-up/tasks/{taskId}")
public class BatchLinkUpIncrementalController {

    @Resource
    private BatchLinkUpIncrementalService batchLinkUpIncrementalService;

    @GetMapping("/incremental-config")
    public Result<BatchLinkUpIncrementalConfigVO> getConfig(@PathVariable("taskId") Long taskId) {
        return Result.buildSuc(batchLinkUpIncrementalService.getConfig(taskId));
    }

    @PutMapping("/incremental-config")
    public Result<BatchLinkUpIncrementalConfigVO> saveConfig(
            @PathVariable("taskId") Long taskId,
            @RequestBody BatchLinkUpIncrementalConfigRequest request
    ) {
        return Result.buildSuc(batchLinkUpIncrementalService.saveConfig(taskId, request));
    }

    @PostMapping("/preview-incremental-context")
    public Result<BatchLinkUpIncrementalContextVO> previewContext(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) BatchLinkUpIncrementalPreviewRequest request
    ) {
        return Result.buildSuc(batchLinkUpIncrementalService.previewContext(taskId, request));
    }

    @PostMapping("/preview-incremental-hocon")
    public Result<BatchLinkUpIncrementalHoconPreviewVO> previewHocon(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) BatchLinkUpIncrementalPreviewRequest request
    ) {
        return Result.buildSuc(batchLinkUpIncrementalService.previewHocon(taskId, request));
    }

    @PostMapping("/run-incremental")
    public Result<RunResultVO> runIncremental(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) BatchLinkUpIncrementalRunRequest request
    ) {
        return Result.buildSuc(batchLinkUpIncrementalService.runIncremental(taskId, request));
    }

    @GetMapping("/incremental-runs")
    public PaginationResult<SyncRunListItemVO> listRuns(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return batchLinkUpIncrementalService.listRuns(taskId, pageNo, pageSize, status, startTime, endTime, keyword);
    }

    @GetMapping("/incremental-runs/{runId}")
    public Result<RunDetailVO> getRun(
            @PathVariable("taskId") Long taskId,
            @PathVariable("runId") String runId
    ) {
        return Result.buildSuc(batchLinkUpIncrementalService.getRun(taskId, runId));
    }

    @GetMapping("/incremental-batches")
    public PaginationResult<SyncBatchListItemVO> listBatches(
            @PathVariable("taskId") Long taskId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "status", required = false) String status
    ) {
        return batchLinkUpIncrementalService.listBatches(taskId, pageNo, pageSize, status);
    }

    @GetMapping("/watermark")
    public Result<List<WatermarkVO>> getWatermark(@PathVariable("taskId") Long taskId) {
        return Result.buildSuc(batchLinkUpIncrementalService.getWatermark(taskId));
    }

    @PutMapping("/watermark")
    public Result<WatermarkVO> updateWatermark(
            @PathVariable("taskId") Long taskId,
            @RequestBody SyncWatermarkUpdateRequest request
    ) {
        return Result.buildSuc(batchLinkUpIncrementalService.updateWatermark(taskId, request));
    }

    @PostMapping("/test-incremental-sql")
    public Result<BatchLinkUpIncrementalContextVO.SqlExecutionVO> testSql(
            @PathVariable("taskId") Long taskId,
            @RequestBody(required = false) BatchLinkUpIncrementalSqlTestRequest request
    ) {
        return Result.buildSuc(batchLinkUpIncrementalService.testSql(taskId, request));
    }
}
