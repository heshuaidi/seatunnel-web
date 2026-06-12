package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.api.service.SyncAuditService;
import org.apache.seatunnel.web.api.service.file.LocalSyncFileScanner;
import org.apache.seatunnel.web.api.service.file.SyncRemoteFileScanner;
import org.apache.seatunnel.web.api.service.model.DiscoveredFile;
import org.apache.seatunnel.web.api.service.model.FileDiscoveryResult;
import org.apache.seatunnel.web.common.enums.SyncFileCursorMode;
import org.apache.seatunnel.web.common.enums.SyncFileItemStatus;
import org.apache.seatunnel.web.common.enums.SyncIncrementalStrategy;
import org.apache.seatunnel.web.common.enums.SyncSinkType;
import org.apache.seatunnel.web.common.enums.SyncSourceType;
import org.apache.seatunnel.web.dao.entity.SyncFileItemEntity;
import org.apache.seatunnel.web.dao.entity.SyncIncrementalConfigEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;
import org.apache.seatunnel.web.dao.repository.SyncFileItemDao;
import org.apache.seatunnel.web.dao.repository.SyncIncrementalConfigDao;
import org.apache.seatunnel.web.dao.repository.SyncTaskDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

class SyncFileDiscoveryServiceImplTest {

    private SyncFileDiscoveryServiceImpl service;
    private SyncFileItemDao fileItemDao;
    private LocalSyncFileScanner localScanner;

    @BeforeEach
    void setUp() {
        service = new SyncFileDiscoveryServiceImpl();
        SyncTaskDao taskDao = Mockito.mock(SyncTaskDao.class);
        SyncIncrementalConfigDao configDao = Mockito.mock(SyncIncrementalConfigDao.class);
        fileItemDao = Mockito.mock(SyncFileItemDao.class);
        SyncAuditService auditService = Mockito.mock(SyncAuditService.class);
        localScanner = Mockito.mock(LocalSyncFileScanner.class);
        SyncRemoteFileScanner remoteScanner = Mockito.mock(SyncRemoteFileScanner.class);

        Mockito.when(taskDao.queryById(1L)).thenReturn(task());
        Mockito.when(taskDao.queryByTaskCode("file_task")).thenReturn(task());
        Mockito.when(configDao.queryByTaskId(1L)).thenReturn(config());
        Mockito.when(localScanner.scan(Mockito.any())).thenReturn(List.of(discoveredFile()));

        ReflectionTestUtils.setField(service, "syncTaskDao", taskDao);
        ReflectionTestUtils.setField(service, "syncIncrementalConfigDao", configDao);
        ReflectionTestUtils.setField(service, "syncFileItemDao", fileItemDao);
        ReflectionTestUtils.setField(service, "syncAuditService", auditService);
        ReflectionTestUtils.setField(service, "localSyncFileScanner", localScanner);
        ReflectionTestUtils.setField(service, "syncRemoteFileScanner", remoteScanner);
    }

    @Test
    void discoverFilesShouldInsertNewFileAsDiscovered() {
        Mockito.when(fileItemDao.findByPathMtimeSizeIdentity(
                Mockito.eq(1L),
                Mockito.eq("/data/a.json"),
                Mockito.eq(10L),
                Mockito.any(Date.class)
        )).thenReturn(null);

        FileDiscoveryResult result = service.discoverFiles(1L);

        Assertions.assertEquals(1, result.getDiscoveredCount());
        ArgumentCaptor<SyncFileItemEntity> captor = ArgumentCaptor.forClass(SyncFileItemEntity.class);
        Mockito.verify(fileItemDao).insert(captor.capture());
        Assertions.assertEquals(SyncFileItemStatus.DISCOVERED, captor.getValue().getStatus());
        Assertions.assertEquals("/data/a.json", captor.getValue().getFilePath());
    }

    @Test
    void discoverFilesShouldSkipAlreadySuccessfulFile() {
        SyncFileItemEntity existing = new SyncFileItemEntity();
        existing.setStatus(SyncFileItemStatus.SUCCESS);
        Mockito.when(fileItemDao.findByPathMtimeSizeIdentity(
                Mockito.eq(1L),
                Mockito.eq("/data/a.json"),
                Mockito.eq(10L),
                Mockito.any(Date.class)
        )).thenReturn(existing);

        FileDiscoveryResult result = service.discoverFiles("file_task");

        Assertions.assertEquals(0, result.getDiscoveredCount());
        Assertions.assertEquals(1, result.getSkippedCount());
        Mockito.verify(fileItemDao, Mockito.never()).insert(Mockito.any());
    }

    @Test
    void discoverFilesShouldUsePathMtimeSizeIdentity() {
        service.discoverFiles(1L);

        Mockito.verify(fileItemDao).findByPathMtimeSizeIdentity(
                Mockito.eq(1L),
                Mockito.eq("/data/a.json"),
                Mockito.eq(10L),
                Mockito.any(Date.class)
        );
        Mockito.verify(fileItemDao, Mockito.never())
                .findByMtimeIdentity(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void claimFilesForBatchShouldResetFailedFileToClaimed() {
        SyncFileItemEntity failed = new SyncFileItemEntity();
        failed.setId(5L);
        failed.setTaskId(1L);
        failed.setStatus(SyncFileItemStatus.FAILED);
        Mockito.when(fileItemDao.listClaimableByTaskId(1L, 3)).thenReturn(List.of(failed));
        Mockito.when(fileItemDao.claimFile(5L, "batch_1")).thenReturn(true);

        List<SyncFileItemEntity> claimed = service.claimFilesForBatch(1L, "batch_1", 3);

        Assertions.assertEquals(1, claimed.size());
        Assertions.assertEquals(SyncFileItemStatus.CLAIMED, claimed.get(0).getStatus());
        Assertions.assertEquals("batch_1", claimed.get(0).getBatchId());
    }

    @Test
    void markFilesShouldDelegateBatchStatusUpdates() {
        SyncFileItemEntity file = new SyncFileItemEntity();
        file.setTaskId(1L);
        Mockito.when(fileItemDao.listByBatchId("batch_1")).thenReturn(List.of(file));
        Mockito.when(fileItemDao.markBatchProcessing("batch_1", "run_1")).thenReturn(1);
        Mockito.when(fileItemDao.markBatchSuccess("batch_1", "run_1")).thenReturn(1);
        Mockito.when(fileItemDao.markBatchFailed("batch_1", "run_1", "failed")).thenReturn(1);

        service.markFilesProcessing("batch_1", "run_1");
        service.markFilesSuccess("batch_1", "run_1");
        service.markFilesFailed("batch_1", "run_1", "failed");

        Mockito.verify(fileItemDao).markBatchProcessing("batch_1", "run_1");
        Mockito.verify(fileItemDao).markBatchSuccess("batch_1", "run_1");
        Mockito.verify(fileItemDao).markBatchFailed("batch_1", "run_1", "failed");
    }

    private SyncTaskEntity task() {
        return SyncTaskEntity.builder()
                .id(1L)
                .taskCode("file_task")
                .sourceType(SyncSourceType.LOCAL_FILE)
                .sinkType(SyncSinkType.JDBC)
                .build();
    }

    private SyncIncrementalConfigEntity config() {
        return SyncIncrementalConfigEntity.builder()
                .taskId(1L)
                .sourceType(SyncSourceType.LOCAL_FILE)
                .strategy(SyncIncrementalStrategy.FILE_MANIFEST)
                .filePath("/data")
                .fileRecursive(true)
                .fileCursorMode(SyncFileCursorMode.PATH_MTIME_SIZE)
                .build();
    }

    private DiscoveredFile discoveredFile() {
        return DiscoveredFile.builder()
                .absolutePath("/data/a.json")
                .fileName("a.json")
                .relativePath("a.json")
                .size(10L)
                .lastModifiedTime(LocalDateTime.of(2026, 6, 1, 8, 0, 0))
                .build();
    }
}
