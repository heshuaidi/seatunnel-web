package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncCheckResultService;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity;
import org.apache.seatunnel.web.dao.repository.SyncCheckResultDao;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncCheckResultServiceImpl extends SyncServiceSupport implements SyncCheckResultService {

    @Resource
    private SyncCheckResultDao syncCheckResultDao;

    @Override
    public Long insertResult(SyncCheckResultEntity entity) {
        requireEntity(entity, "syncCheckResult");
        if (isBlank(entity.getRunId())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "runId");
        }
        if (isBlank(entity.getCheckCode())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "checkCode");
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now());
        }
        if (entity.getPassed() == null) {
            entity.setPassed(false);
        }
        if (entity.getFailOnMismatch() == null) {
            entity.setFailOnMismatch(true);
        }
        syncCheckResultDao.insert(entity);
        return entity.getId();
    }

    @Override
    public List<SyncCheckResultEntity> listByRunId(String runId) {
        return syncCheckResultDao.listByRunId(runId);
    }

    @Override
    public List<SyncCheckResultEntity> listByBatchId(String batchId) {
        return syncCheckResultDao.listByBatchId(batchId);
    }

    @Override
    public Boolean hasFailedBlockingCheck(String runId) {
        return syncCheckResultDao.hasFailedBlockingCheck(runId);
    }
}
