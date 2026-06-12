package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.HoconRenderService;
import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.api.service.SyncCheckResultService;
import org.apache.seatunnel.web.api.service.SyncCheckSqlExecutor;
import org.apache.seatunnel.web.api.service.SyncVerifyService;
import org.apache.seatunnel.web.api.service.model.VerifyResult;
import org.apache.seatunnel.web.api.utils.SyncCheckCompareUtils;
import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.common.enums.SyncCheckType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncVerifyServiceImpl extends SyncServiceSupport implements SyncVerifyService {

    @Resource
    private SyncCheckConfigService syncCheckConfigService;

    @Resource
    private SyncCheckResultService syncCheckResultService;

    @Resource
    private SyncCheckSqlExecutor syncCheckSqlExecutor;

    @Resource
    private HoconRenderService hoconRenderService;

    @Resource
    private SyncAuditService syncAuditService;

    @Override
    public VerifyResult verifyRun(
            SyncTaskEntity task,
            SyncBatchEntity batch,
            SyncRunEntity run,
            Map<String, Object> variables
    ) {
        requireEntity(task, "syncTask");
        requireEntity(batch, "syncBatch");
        requireEntity(run, "syncRun");

        VerifyResult result = new VerifyResult();
        List<SyncCheckConfigEntity> configs = syncCheckConfigService.listEnabledByTaskId(task.getId());
        if (configs.isEmpty()) {
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.VERIFYING,
                    "No check configured, skip verification",
                    null);
            return result;
        }

        Map<String, String> actualValueByCheckCode = new LinkedHashMap<>();
        for (SyncCheckConfigEntity config : configs) {
            SyncCheckResultEntity checkResult = executeOneCheck(task, batch, run, variables, config, actualValueByCheckCode);
            result.getResults().add(checkResult);
            actualValueByCheckCode.put(config.getCheckCode(), checkResult.getActualValue());

            collectMetrics(result, checkResult);
            if (!Boolean.TRUE.equals(checkResult.getPassed())
                    && Boolean.TRUE.equals(checkResult.getFailOnMismatch())) {
                result.setPassed(false);
                result.setHasBlockingFailure(true);
                result.setErrorMessage(appendMessage(result.getErrorMessage(), checkFailureMessage(checkResult)));
            }
        }

        if (!result.isHasBlockingFailure()) {
            result.setPassed(true);
        }
        return result;
    }

    private SyncCheckResultEntity executeOneCheck(
            SyncTaskEntity task,
            SyncBatchEntity batch,
            SyncRunEntity run,
            Map<String, Object> variables,
            SyncCheckConfigEntity config,
            Map<String, String> actualValueByCheckCode
    ) {
        Date startTime = now();
        String renderedSql = null;
        String actualValue = null;
        String expectedValue = null;
        String compareToActualValue = null;
        boolean passed = false;
        String errorMessage = null;

        try {
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.VERIFYING,
                    "Render check SQL",
                    Map.of("checkCode", config.getCheckCode()));
            renderedSql = hoconRenderService.render(config.getSqlText(), variables);
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.VERIFYING,
                    "Check SQL rendered",
                    Map.of("checkCode", config.getCheckCode()));

            Object scalar = syncCheckSqlExecutor.executeScalar(config, renderedSql);
            actualValue = scalar == null ? null : String.valueOf(scalar);
            expectedValue = SyncCheckCompareUtils.resolveExpectedValue(config, actualValueByCheckCode);
            compareToActualValue = expectedValueFromCompareTarget(config, actualValueByCheckCode);
            passed = evaluate(config, actualValue, expectedValue);
        } catch (Exception e) {
            errorMessage = e.getMessage() == null ? e.toString() : e.getMessage();
            passed = false;
        }

        SyncCheckResultEntity entity = SyncCheckResultEntity.builder()
                .runId(run.getRunId())
                .batchId(batch.getBatchId())
                .taskId(task.getId())
                .taskCode(task.getTaskCode())
                .checkCode(config.getCheckCode())
                .checkName(config.getCheckName())
                .checkType(config.getCheckType())
                .renderedSql(renderedSql)
                .actualValue(actualValue)
                .expectedOperator(config.getExpectedOperator())
                .expectedValue(expectedValue)
                .compareToCheckCode(config.getCompareToCheckCode())
                .compareToActualValue(compareToActualValue)
                .passed(passed)
                .failOnMismatch(config.getFailOnMismatch() == null || Boolean.TRUE.equals(config.getFailOnMismatch()))
                .errorMessage(errorMessage)
                .startTime(startTime)
                .endTime(now())
                .createTime(now())
                .build();
        syncCheckResultService.insertResult(entity);

        appendCheckAudit(task, batch, run, entity);
        return entity;
    }

    private boolean evaluate(SyncCheckConfigEntity config, String actualValue, String expectedValue) {
        if (config.getCheckType() == SyncCheckType.CUSTOM_BOOLEAN
                && config.getExpectedOperator() == null
                && isBlank(config.getCompareToCheckCode())) {
            return SyncCheckCompareUtils.toBoolean(actualValue);
        }
        return SyncCheckCompareUtils.compare(actualValue, config.getExpectedOperator(), expectedValue);
    }

    private String expectedValueFromCompareTarget(
            SyncCheckConfigEntity config,
            Map<String, String> actualValueByCheckCode
    ) {
        if (isBlank(config.getCompareToCheckCode())) {
            return null;
        }
        if (!actualValueByCheckCode.containsKey(config.getCompareToCheckCode())) {
            throw new ServiceException("Compare target check result not found: " + config.getCompareToCheckCode());
        }
        return actualValueByCheckCode.get(config.getCompareToCheckCode());
    }

    private void collectMetrics(VerifyResult result, SyncCheckResultEntity checkResult) {
        Long metricValue = toLong(checkResult.getActualValue());
        if (metricValue == null || checkResult.getCheckType() == null) {
            return;
        }
        if (checkResult.getCheckType() == SyncCheckType.SOURCE_COUNT && result.getSourceCount() == null) {
            result.setSourceCount(metricValue);
        } else if (checkResult.getCheckType() == SyncCheckType.SINK_COUNT && result.getSinkCount() == null) {
            result.setSinkCount(metricValue);
        } else if (checkResult.getCheckType() == SyncCheckType.ERROR_COUNT && result.getErrorCount() == null) {
            result.setErrorCount(metricValue);
        }
    }

    private Long toLong(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void appendCheckAudit(
            SyncTaskEntity task,
            SyncBatchEntity batch,
            SyncRunEntity run,
            SyncCheckResultEntity checkResult
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("checkCode", checkResult.getCheckCode());
        detail.put("checkType", checkResult.getCheckType() == null ? null : checkResult.getCheckType().getCode());
        detail.put("actualValue", checkResult.getActualValue());
        detail.put("expectedOperator", checkResult.getExpectedOperator() == null
                ? null
                : checkResult.getExpectedOperator().getCode());
        detail.put("expectedValue", checkResult.getExpectedValue());
        detail.put("compareToCheckCode", checkResult.getCompareToCheckCode());
        detail.put("passed", checkResult.getPassed());
        detail.put("errorMessage", checkResult.getErrorMessage());

        if (Boolean.TRUE.equals(checkResult.getPassed())) {
            syncAuditService.appendInfo(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.VERIFYING,
                    "Sync check passed",
                    detail);
        } else {
            syncAuditService.appendWarn(run.getRunId(), batch.getBatchId(), task.getId(), task.getTaskCode(),
                    SyncAuditEventType.VERIFYING,
                    "Sync check failed",
                    detail);
        }
    }

    private String checkFailureMessage(SyncCheckResultEntity checkResult) {
        if (!isBlank(checkResult.getErrorMessage())) {
            return checkResult.getCheckCode() + ": " + checkResult.getErrorMessage();
        }
        return checkResult.getCheckCode()
                + " mismatch, actual="
                + checkResult.getActualValue()
                + ", expected="
                + checkResult.getExpectedValue();
    }

    private String appendMessage(String current, String next) {
        if (isBlank(current)) {
            return next;
        }
        return current + "; " + next;
    }
}
