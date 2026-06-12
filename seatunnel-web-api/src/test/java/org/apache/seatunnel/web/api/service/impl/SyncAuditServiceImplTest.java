package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.common.enums.SyncAuditEventType;
import org.apache.seatunnel.web.common.enums.SyncAuditLevel;
import org.apache.seatunnel.web.dao.entity.SyncAuditEntity;
import org.apache.seatunnel.web.dao.repository.SyncAuditDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

class SyncAuditServiceImplTest {

    @Test
    void appendInfoShouldPersistAuditEntity() {
        SyncAuditServiceImpl service = new SyncAuditServiceImpl();
        SyncAuditDao dao = Mockito.mock(SyncAuditDao.class);
        ReflectionTestUtils.setField(service, "syncAuditDao", dao);

        service.appendInfo(
                "run-1",
                "batch-1",
                1L,
                "task-1",
                SyncAuditEventType.CREATE_BATCH,
                "created",
                Map.of("status", "CREATED")
        );

        ArgumentCaptor<SyncAuditEntity> captor = ArgumentCaptor.forClass(SyncAuditEntity.class);
        Mockito.verify(dao).insert(captor.capture());
        Assertions.assertEquals(SyncAuditLevel.INFO, captor.getValue().getEventLevel());
        Assertions.assertEquals(SyncAuditEventType.CREATE_BATCH, captor.getValue().getEventType());
        Assertions.assertTrue(captor.getValue().getDetailJson().contains("CREATED"));
    }
}
