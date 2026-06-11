package org.apache.seatunnel.web.api.controller;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.api.service.SyncCheckResultService;
import org.apache.seatunnel.web.api.service.SyncRunCoordinatorService;
import org.apache.seatunnel.web.api.service.SyncTaskService;
import org.apache.seatunnel.web.common.enums.SyncCheckDatasourceType;
import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.common.enums.SyncCheckType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.spi.bean.dto.BackfillTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.PreviewHoconRequest;
import org.apache.seatunnel.web.spi.bean.dto.RunTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncCheckConfigRequest;
import org.apache.seatunnel.web.spi.bean.entity.Result;
import org.apache.seatunnel.web.spi.bean.vo.HoconPreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncCheckConfigVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncCheckResultVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
