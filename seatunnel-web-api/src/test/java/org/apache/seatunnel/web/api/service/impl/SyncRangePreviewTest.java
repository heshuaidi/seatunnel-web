package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncWatermarkValueType;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncWatermarkEntity;
import org.apache.seatunnel.web.dao.repository.SyncIncrementalConfigDao;
import org.apache.seatunnel.web.dao.repository.SyncWatermarkDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

class SyncRangePreviewTest {

    private SyncWatermarkServiceImpl service;
    private SyncIncrementalConfigDao configDao;
    private SyncWatermarkDao watermarkDao;

    @BeforeEach
    void setUp() {
        service = new SyncWatermarkServiceImpl();
        configDao = Mockito.mock(SyncIncrementalConfigDao.class);
        watermarkDao = Mockito.mock(SyncWatermarkDao.class);
        ReflectionTestUtils.setField(service, "syncIncrementalConfigDao", configDao);
        ReflectionTestUtils.setField(service, "syncWatermarkDao", watermarkDao);
    }

    @Test
    void updateTimeRangePreviewShouldNotWriteWatermark() {
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(updateTimeConfig());
        Mockito.when(watermarkDao.queryByTaskIdAndWatermarkKey(1L, "default"))
                .thenReturn(SyncWatermarkEntity.builder()
                        .currentValue("2026-06-01 00:10:00")
                        .build());

        WatermarkRange range = service.previewRange(1L, SyncRunMode.NORMAL, Map.of());

        Assertions.assertEquals("2026-06-01 00:09:00", range.getStartValue());
        Assertions.assertTrue(range.getLookbackApplied());
        Mockito.verify(watermarkDao, Mockito.never()).insert(Mockito.any());
        Mockito.verify(watermarkDao, Mockito.never()).updateById(Mockito.any());
    }

    @Test
    void idRangePreviewShouldUseBatchEndValue() {
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(idRangeConfig());
        Mockito.when(watermarkDao.queryByTaskIdAndWatermarkKey(1L, "default"))
                .thenReturn(SyncWatermarkEntity.builder()
                        .currentValue("10")
                        .build());

        WatermarkRange range = service.previewRange(1L, SyncRunMode.NORMAL, Map.of("batchEndValue", "100"));

        Assertions.assertEquals("10", range.getStartValue());
        Assertions.assertEquals("100", range.getEndValue());
        Assertions.assertTrue(range.isAdvanceWatermark());
        Mockito.verify(watermarkDao, Mockito.never()).insert(Mockito.any());
        Mockito.verify(watermarkDao, Mockito.never()).updateById(Mockito.any());
    }

    @Test
    void backfillPreviewShouldNotAdvanceWatermarkByDefault() {
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(updateTimeConfig());

        WatermarkRange range = service.previewRange(
                1L,
                SyncRunMode.BACKFILL,
                Map.of(
                        "startTime", "2026-06-01 00:00:00",
                        "endTime", "2026-06-01 01:00:00",
                        "advanceWatermark", false
                )
        );

        Assertions.assertTrue(range.isBackfill());
        Assertions.assertFalse(range.isAdvanceWatermark());
        Mockito.verify(watermarkDao, Mockito.never()).insert(Mockito.any());
        Mockito.verify(watermarkDao, Mockito.never()).updateById(Mockito.any());
    }

    private SyncIncrementalConfigEntity updateTimeConfig() {
        return SyncIncrementalConfigEntity.builder()
                .taskId(1L)
                .sourceType(SyncSourceType.JDBC)
                .strategy(SyncIncrementalStrategy.UPDATE_TIME_RANGE)
                .watermarkKey("default")
                .watermarkFieldType(SyncWatermarkValueType.DATETIME)
                .startValue("2026-06-01 00:00:00")
                .lookbackSeconds(60)
                .build();
    }

    private SyncIncrementalConfigEntity idRangeConfig() {
        return SyncIncrementalConfigEntity.builder()
                .taskId(1L)
                .sourceType(SyncSourceType.SQL)
                .strategy(SyncIncrementalStrategy.ID_RANGE)
                .watermarkKey("default")
                .watermarkFieldType(SyncWatermarkValueType.LONG)
                .startValue("0")
                .build();
    }
}
