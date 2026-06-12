package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.api.service.model.WatermarkRange;
import org.apache.seatunnel.web.common.enums.SyncRunMode;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.repository.SyncBatchDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class SyncBatchServiceImplTest {

    @Test
    void createBatchForRunShouldGenerateExpectedBatchId() {
        SyncBatchServiceImpl service = new SyncBatchServiceImpl();
        SyncBatchDao dao = Mockito.mock(SyncBatchDao.class);
        ReflectionTestUtils.setField(service, "syncBatchDao", dao);

        SyncTaskEntity task = new SyncTaskEntity();
        task.setId(1L);
        task.setTaskCode("mes_spc_measure");

        WatermarkRange range = new WatermarkRange();
        range.setStartValue("0");
        range.setEndValue("100");

        service.createBatchForRun(task, range, SyncTriggerType.MANUAL, SyncRunMode.NORMAL);

        ArgumentCaptor<SyncBatchEntity> captor = ArgumentCaptor.forClass(SyncBatchEntity.class);
        Mockito.verify(dao).insert(captor.capture());
        Assertions.assertTrue(captor.getValue().getBatchId().matches("mes_spc_measure_\\d{14}_\\d{6}"));
    }
}
