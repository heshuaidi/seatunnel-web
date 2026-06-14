package org.apache.seatunnel.web.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.NonNull;
import org.apache.seatunnel.web.api.service.model.IncrementalLockResult;
import org.apache.seatunnel.web.dao.entity.SyncTaskLockEntity;
import org.apache.seatunnel.web.dao.repository.SyncTaskLockDao;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

class IncrementalTaskLockServiceImplTest {

    @Test
    void acquireRejectTakeoverExpiredAndReleaseByToken() {
        InMemorySyncTaskLockDao dao = new InMemorySyncTaskLockDao();
        SyncRunProperties properties = new SyncRunProperties();
        IncrementalTaskLockServiceImpl service = new IncrementalTaskLockServiceImpl(dao, properties);
        ReflectionTestUtils.setField(service, "configuredLockTtlMinutes", 60L);

        IncrementalLockResult first = service.acquireLock(1L, "default", "run-1", "batch-1");
        Assertions.assertTrue(first.isAcquired());
        Assertions.assertEquals("run-1", dao.lock.getRunId());

        IncrementalLockResult rejected = service.acquireLock(1L, "default", "run-2", "batch-2");
        Assertions.assertFalse(rejected.isAcquired());
        Assertions.assertEquals("run-1", rejected.getExistingLock().getRunId());

        dao.lock.setExpiresAt(new Date(System.currentTimeMillis() - 1000L));
        IncrementalLockResult takeover = service.acquireLock(1L, "default", "run-3", "batch-3");
        Assertions.assertTrue(takeover.isAcquired());
        Assertions.assertEquals("run-3", dao.lock.getRunId());

        Assertions.assertFalse(service.releaseLock(1L, "default", first.getLockToken()));
        Assertions.assertNotNull(dao.lock);
        Assertions.assertTrue(service.releaseLock(1L, "default", takeover.getLockToken()));
        Assertions.assertNull(dao.lock);
    }

    private static class InMemorySyncTaskLockDao implements SyncTaskLockDao {
        private SyncTaskLockEntity lock;

        @Override
        public SyncTaskLockEntity queryByTaskIdAndWatermarkKey(Long taskId, String watermarkKey) {
            if (lock == null
                    || !lock.getTaskId().equals(taskId)
                    || !lock.getWatermarkKey().equals(watermarkKey)) {
                return null;
            }
            return lock;
        }

        @Override
        public boolean takeoverExpiredLock(
                Long taskId,
                String watermarkKey,
                String lockToken,
                String lockOwner,
                String runId,
                String batchId,
                Date now,
                Date expiresAt
        ) {
            if (lock == null
                    || !lock.getTaskId().equals(taskId)
                    || !lock.getWatermarkKey().equals(watermarkKey)
                    || !lock.getExpiresAt().before(now)) {
                return false;
            }
            lock.setLockToken(lockToken);
            lock.setLockOwner(lockOwner);
            lock.setRunId(runId);
            lock.setBatchId(batchId);
            lock.setLockedAt(now);
            lock.setExpiresAt(expiresAt);
            lock.setUpdateTime(now);
            return true;
        }

        @Override
        public boolean release(Long taskId, String watermarkKey, String lockToken) {
            if (lock == null
                    || !lock.getTaskId().equals(taskId)
                    || !lock.getWatermarkKey().equals(watermarkKey)
                    || !lock.getLockToken().equals(lockToken)) {
                return false;
            }
            lock = null;
            return true;
        }

        @Override
        public int insert(@NonNull SyncTaskLockEntity model) {
            if (lock != null
                    && lock.getTaskId().equals(model.getTaskId())
                    && lock.getWatermarkKey().equals(model.getWatermarkKey())) {
                throw new DuplicateKeyException("duplicate task lock");
            }
            lock = model;
            return 1;
        }

        @Override
        public SyncTaskLockEntity queryById(@NonNull Serializable id) {
            throw unsupported();
        }

        @Override
        public Optional<SyncTaskLockEntity> queryOptionalById(@NonNull Serializable id) {
            throw unsupported();
        }

        @Override
        public List<SyncTaskLockEntity> queryByIds(Collection<? extends Serializable> ids) {
            throw unsupported();
        }

        @Override
        public List<SyncTaskLockEntity> queryAll() {
            throw unsupported();
        }

        @Override
        public List<SyncTaskLockEntity> queryByCondition(SyncTaskLockEntity queryCondition) {
            throw unsupported();
        }

        @Override
        public void insertBatch(Collection<SyncTaskLockEntity> models) {
            throw unsupported();
        }

        @Override
        public boolean updateById(@NonNull SyncTaskLockEntity model) {
            throw unsupported();
        }

        @Override
        public boolean deleteById(@NonNull Serializable id) {
            throw unsupported();
        }

        @Override
        public boolean deleteByIds(Collection<? extends Serializable> ids) {
            throw unsupported();
        }

        @Override
        public boolean deleteByCondition(SyncTaskLockEntity queryCondition) {
            throw unsupported();
        }

        @Override
        public List<SyncTaskLockEntity> selectList(Wrapper<SyncTaskLockEntity> queryWrapper) {
            throw unsupported();
        }

        @Override
        public IPage<SyncTaskLockEntity> selectPage(
                IPage<SyncTaskLockEntity> page,
                Wrapper<SyncTaskLockEntity> queryWrapper
        ) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not needed for this test");
        }
    }
}
