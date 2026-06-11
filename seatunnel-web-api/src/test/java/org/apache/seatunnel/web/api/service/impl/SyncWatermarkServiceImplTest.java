package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.common.enums.SyncWatermarkValueType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
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

class SyncWatermarkServiceImplTest {

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
    void updateTimeRangeShouldUseCurrentWatermarkAndLookback() {
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(updateTimeConfig(60));
        Mockito.when(watermarkDao.queryByTaskIdAndWatermarkKey(1L, "default"))
                .thenReturn(SyncWatermarkEntity.builder()
                        .currentValue("2026-06-02 10:00:00")
                        .build());

        WatermarkRange range = service.calculateNextRange(1L, Map.of());

        Assertions.assertEquals("2026-06-02 09:59:00", range.getStartValue());
        Assertions.assertNotNull(range.getEndValue());
        Assertions.assertTrue(range.isAdvanceWatermark());
    }

    @Test
    void updateTimeRangeShouldUseStartValueWhenCurrentWatermarkEmpty() {
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(updateTimeConfig(0));
        Mockito.when(watermarkDao.queryByTaskIdAndWatermarkKey(1L, "default")).thenReturn(null);

        WatermarkRange range = service.calculateNextRange(1L, Map.of());

        Assertions.assertEquals("2026-06-01 00:00:00", range.getStartValue());
    }

    @Test
    void idRangeShouldRejectMissingBatchEndValue() {
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(idRangeConfig());
        Mockito.when(watermarkDao.queryByTaskIdAndWatermarkKey(1L, "default")).thenReturn(null);

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> service.calculateNextRange(1L, Map.of())
        );

        Assertions.assertTrue(exception.getMessage().contains("batchEndValue"));
    }

    @Test
    void backfillShouldNotAdvanceWatermarkByDefault() {
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(updateTimeConfig(0));

        WatermarkRange range = service.calculateBackfillRange(
                1L,
                Map.of("startTime", "2026-06-01 00:00:00", "endTime", "2026-06-02 00:00:00"),
                false
        );

        Assertions.assertTrue(range.isBackfill());
        Assertions.assertFalse(range.isAdvanceWatermark());
    }

    private SyncIncrementalConfigEntity updateTimeConfig(int lookbackSeconds) {
        return SyncIncrementalConfigEntity.builder()
                .taskId(1L)
                .sourceType(SyncSourceType.JDBC)
                .strategy(SyncIncrementalStrategy.UPDATE_TIME_RANGE)
                .watermarkKey("default")
                .watermarkFieldType(SyncWatermarkValueType.DATETIME)
                .startValue("2026-06-01 00:00:00")
                .lookbackSeconds(lookbackSeconds)
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
