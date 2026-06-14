package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncWatermarkService;
import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.common.constants.SyncConstants;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.dao.repository.SyncIncrementalConfigDao;
import org.apache.seatunnel.web.dao.repository.SyncWatermarkDao;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class SyncWatermarkServiceImpl extends SyncServiceSupport implements SyncWatermarkService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private SyncWatermarkDao syncWatermarkDao;

    @Resource
    private SyncIncrementalConfigDao syncIncrementalConfigDao;

    @Override
    public Long create(SyncWatermarkEntity entity) {
        requireEntity(entity, "syncWatermark");
        if (isBlank(entity.getWatermarkKey())) {
            entity.setWatermarkKey(SyncConstants.DEFAULT_WATERMARK_KEY);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(now());
        }
        syncWatermarkDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncWatermarkEntity entity) {
        requireEntity(entity, "syncWatermark");
        requireId(entity.getId());
        entity.setUpdateTime(now());
        return syncWatermarkDao.updateById(entity);
    }

    @Override
    public SyncWatermarkEntity getById(Long id) {
        requireId(id);
        return syncWatermarkDao.queryById(id);
    }

    @Override
    public SyncWatermarkEntity getByTaskIdAndWatermarkKey(Long taskId, String watermarkKey) {
        requireId(taskId);
        return syncWatermarkDao.queryByTaskIdAndWatermarkKey(taskId, watermarkKey);
    }

    @Override
    public List<SyncWatermarkEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncWatermarkDao.listByTaskId(taskId);
    }

    @Override
    public WatermarkRange calculateNextRange(Long taskId, Map<String, Object> runParams) {
        requireId(taskId);
        SyncIncrementalConfigEntity config = loadConfig(taskId);
        validateSupportedSource(config);

        SyncIncrementalStrategy strategy = config.getStrategy();
        if (strategy == SyncIncrementalStrategy.UPDATE_TIME_RANGE) {
            return calculateNextUpdateTimeRange(config);
        }
        if (strategy == SyncIncrementalStrategy.ID_RANGE) {
            return calculateNextIdRange(config, runParams);
        }

        throw unsupportedStrategy(strategy);
    }

    @Override
    public WatermarkRange calculateBackfillRange(Long taskId, Map<String, Object> runParams, Boolean advanceWatermark) {
        requireId(taskId);
        SyncIncrementalConfigEntity config = loadConfig(taskId);
        validateSupportedSource(config);

        boolean allowAdvance = Boolean.TRUE.equals(advanceWatermark)
                && Boolean.TRUE.equals(config.getBackfillAdvanceWatermark());

        if (config.getStrategy() == SyncIncrementalStrategy.UPDATE_TIME_RANGE) {
            LocalDateTime startTime = parseDateTime(requiredParam(runParams, "startTime", "start_time"));
            LocalDateTime endTime = parseDateTime(requiredParam(runParams, "endTime", "end_time"));
            if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
                throw new ServiceException("Backfill endTime must be greater than startTime");
            }

            WatermarkRange range = new WatermarkRange();
            range.setWatermarkKey(defaultWatermarkKey(config.getWatermarkKey()));
            range.setStartTime(startTime);
            range.setEndTime(endTime);
            range.setStartValue(formatDateTime(startTime));
            range.setEndValue(formatDateTime(endTime));
            range.setValueType(valueType(config));
            range.setCurrentWatermark(currentValue(config));
            range.setLookbackApplied(false);
            range.setMaxBatchSecondsApplied(false);
            range.setWarnings(List.of());
            range.setBackfill(true);
            range.setAdvanceWatermark(allowAdvance);
            return range;
        }

        if (config.getStrategy() == SyncIncrementalStrategy.ID_RANGE) {
            String startValue = requiredParam(runParams, "startValue", "start_value");
            String endValue = requiredParam(runParams, "endValue", "end_value");

            WatermarkRange range = new WatermarkRange();
            range.setWatermarkKey(defaultWatermarkKey(config.getWatermarkKey()));
            range.setStartValue(startValue);
            range.setEndValue(endValue);
            range.setValueType(valueType(config));
            range.setCurrentWatermark(currentValue(config));
            range.setLookbackApplied(false);
            range.setMaxBatchSecondsApplied(false);
            range.setWarnings(List.of());
            range.setBackfill(true);
            range.setAdvanceWatermark(allowAdvance);
            return range;
        }

        throw unsupportedStrategy(config.getStrategy());
    }

    @Override
    public WatermarkRange previewRange(Long taskId, SyncRunMode runMode, Map<String, Object> runParams) {
        if (runMode == SyncRunMode.BACKFILL) {
            Boolean advanceWatermark = parseBooleanParam(runParams, "advanceWatermark", "advance_watermark");
            return calculateBackfillRange(taskId, runParams, advanceWatermark);
        }
        return calculateNextRange(taskId, runParams);
    }

    @Override
    public void advanceWatermark(
            Long taskId,
            String watermarkKey,
            String newValue,
            Long runId,
            String batchId
    ) {
        requireId(taskId);
        if (isBlank(newValue)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "newValue");
        }

        String key = defaultWatermarkKey(watermarkKey);
        SyncWatermarkEntity existing = syncWatermarkDao.queryByTaskIdAndWatermarkKey(taskId, key);
        SyncIncrementalConfigEntity config = syncIncrementalConfigDao.queryByTaskIdAndWatermarkKey(taskId, key);
        Date now = now();

        if (existing == null) {
            SyncWatermarkEntity entity = SyncWatermarkEntity.builder()
                    .taskId(taskId)
                    .watermarkKey(key)
                    .currentValue(newValue)
                    .previousValue(null)
                    .currentValueType(config == null ? null : config.getWatermarkFieldType())
                    .lastSuccessRunId(runId)
                    .lastSuccessBatchId(batchId)
                    .updateTime(now)
                    .build();
            if (syncWatermarkDao.insert(entity) <= 0) {
                throw new ServiceException("Insert sync watermark failed, taskId=" + taskId + ", watermarkKey=" + key);
            }
            return;
        }

        SyncWatermarkEntity update = new SyncWatermarkEntity();
        update.setId(existing.getId());
        update.setCurrentValue(newValue);
        update.setPreviousValue(existing.getCurrentValue());
        update.setCurrentValueType(existing.getCurrentValueType() != null
                ? existing.getCurrentValueType()
                : config == null ? null : config.getWatermarkFieldType());
        update.setLastSuccessRunId(runId);
        update.setLastSuccessBatchId(batchId);
        update.setUpdateTime(now);
        if (syncWatermarkDao.updateById(update)) {
            return;
        }

        SyncWatermarkEntity fallbackInsert = SyncWatermarkEntity.builder()
                .taskId(taskId)
                .watermarkKey(key)
                .currentValue(newValue)
                .previousValue(existing.getCurrentValue())
                .currentValueType(existing.getCurrentValueType() != null
                        ? existing.getCurrentValueType()
                        : config == null ? null : config.getWatermarkFieldType())
                .lastSuccessRunId(runId)
                .lastSuccessBatchId(batchId)
                .updateTime(now)
                .build();
        if (syncWatermarkDao.insert(fallbackInsert) <= 0) {
            throw new ServiceException("Insert sync watermark after zero-row update failed, taskId="
                    + taskId + ", watermarkKey=" + key);
        }
    }

    @Override
    public void rollbackOrKeepWatermarkOnFailure(Long taskId, String watermarkKey, Long runId, String batchId) {
        // The current strategy is failure-safe by design: watermark is advanced only
        // after run success, so failures require no database mutation.
    }

    private WatermarkRange calculateNextUpdateTimeRange(SyncIncrementalConfigEntity config) {
        String actualCurrentValue = currentValue(config);
        String currentValue = actualCurrentValue;
        List<String> warnings = new ArrayList<>();
        if (isBlank(currentValue)) {
            currentValue = config.getStartValue();
            warnings.add("Current watermark is empty, use incremental startValue");
        }
        if (isBlank(currentValue)) {
            throw new ServiceException("UPDATE_TIME_RANGE requires current watermark or startValue");
        }

        int lookbackSeconds = config.getLookbackSeconds() == null ? 0 : config.getLookbackSeconds();
        LocalDateTime startTime = parseDateTime(currentValue).minusSeconds(lookbackSeconds);
        LocalDateTime endTime = LocalDateTime.now();

        boolean maxBatchSecondsApplied = false;
        if (config.getMaxBatchSeconds() != null && config.getMaxBatchSeconds() > 0) {
            LocalDateTime maxEndTime = startTime.plusSeconds(config.getMaxBatchSeconds());
            if (endTime.isAfter(maxEndTime)) {
                endTime = maxEndTime;
                maxBatchSecondsApplied = true;
            }
        }

        WatermarkRange range = new WatermarkRange();
        range.setWatermarkKey(defaultWatermarkKey(config.getWatermarkKey()));
        range.setStartTime(startTime);
        range.setEndTime(endTime);
        range.setStartValue(formatDateTime(startTime));
        range.setEndValue(formatDateTime(endTime));
        range.setValueType(valueType(config));
        range.setCurrentWatermark(isBlank(actualCurrentValue) ? currentValue : actualCurrentValue);
        range.setLookbackApplied(lookbackSeconds > 0);
        range.setMaxBatchSecondsApplied(maxBatchSecondsApplied);
        range.setWarnings(warnings);
        range.setBackfill(false);
        range.setAdvanceWatermark(true);
        return range;
    }

    private WatermarkRange calculateNextIdRange(SyncIncrementalConfigEntity config, Map<String, Object> runParams) {
        String actualCurrentValue = currentValue(config);
        String currentValue = actualCurrentValue;
        List<String> warnings = new ArrayList<>();
        if (isBlank(currentValue)) {
            currentValue = isBlank(config.getStartValue()) ? "0" : config.getStartValue();
            warnings.add("Current watermark is empty, use incremental startValue or 0");
        }

        String endValue = findParam(runParams, "batchEndValue", "batch_end_value");
        if (isBlank(endValue)) {
            throw new ServiceException("ID_RANGE requires batchEndValue in run params");
        }

        WatermarkRange range = new WatermarkRange();
        range.setWatermarkKey(defaultWatermarkKey(config.getWatermarkKey()));
        range.setStartValue(currentValue);
        range.setEndValue(endValue);
        range.setValueType(valueType(config));
        range.setCurrentWatermark(isBlank(actualCurrentValue) ? currentValue : actualCurrentValue);
        range.setLookbackApplied(false);
        range.setMaxBatchSecondsApplied(false);
        range.setWarnings(warnings);
        range.setBackfill(false);
        range.setAdvanceWatermark(true);
        return range;
    }

    private SyncIncrementalConfigEntity loadConfig(Long taskId) {
        SyncIncrementalConfigEntity config = syncIncrementalConfigDao.queryByTaskId(taskId);
        if (config == null) {
            throw new ServiceException("Sync incremental config not found, taskId=" + taskId);
        }
        if (config.getStrategy() == null) {
            throw new ServiceException("Sync incremental strategy is empty, taskId=" + taskId);
        }
        return config;
    }

    private void validateSupportedSource(SyncIncrementalConfigEntity config) {
        if (config.getSourceType() == SyncSourceType.JDBC || config.getSourceType() == SyncSourceType.SQL) {
            return;
        }
        throw new ServiceException("Unsupported sync source type: " + config.getSourceType());
    }

    private String currentValue(SyncIncrementalConfigEntity config) {
        SyncWatermarkEntity watermark = syncWatermarkDao.queryByTaskIdAndWatermarkKey(
                config.getTaskId(),
                defaultWatermarkKey(config.getWatermarkKey())
        );
        return watermark == null ? null : watermark.getCurrentValue();
    }

    private String requiredParam(Map<String, Object> params, String camelKey, String snakeKey) {
        String value = findParam(params, camelKey, snakeKey);
        if (isBlank(value)) {
            throw new ServiceException("Required run param is missing: " + camelKey);
        }
        return value;
    }

    private String findParam(Map<String, Object> params, String camelKey, String snakeKey) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        Object value = params.get(camelKey);
        if (value == null) {
            value = params.get(snakeKey);
        }
        return value == null ? null : String.valueOf(value);
    }

    private Boolean parseBooleanParam(Map<String, Object> params, String camelKey, String snakeKey) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        Object value = params.get(camelKey);
        if (value == null) {
            value = params.get(snakeKey);
        }
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            throw new ServiceException("Invalid datetime value, expected yyyy-MM-dd HH:mm:ss: " + value);
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return DATE_TIME_FORMATTER.format(value);
    }

    private String defaultWatermarkKey(String watermarkKey) {
        return isBlank(watermarkKey) ? SyncConstants.DEFAULT_WATERMARK_KEY : watermarkKey;
    }

    private String valueType(SyncIncrementalConfigEntity config) {
        return config.getWatermarkFieldType() == null ? null : config.getWatermarkFieldType().getCode();
    }

    private ServiceException unsupportedStrategy(SyncIncrementalStrategy strategy) {
        return new ServiceException("Unsupported incremental strategy: " + strategy);
    }
}
