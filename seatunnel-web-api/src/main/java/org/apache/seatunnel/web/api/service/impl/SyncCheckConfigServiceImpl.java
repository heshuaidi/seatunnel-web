package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.seatunnel.web.api.service.SyncCheckConfigService;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.dao.repository.SyncCheckConfigDao;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class SyncCheckConfigServiceImpl extends SyncServiceSupport implements SyncCheckConfigService {

    @Resource
    private SyncCheckConfigDao syncCheckConfigDao;

    @Override
    public Long create(SyncCheckConfigEntity entity) {
        requireEntity(entity, "syncCheckConfig");
        if (isBlank(entity.getCheckCode())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "checkCode");
        }
        if (entity.getCheckType() == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "checkType");
        }
        if (isBlank(entity.getSqlText())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "sqlText");
        }
        Date now = now();
        if (entity.getFailOnMismatch() == null) {
            entity.setFailOnMismatch(true);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(true);
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(now);
        }
        syncCheckConfigDao.insert(entity);
        return entity.getId();
    }

    @Override
    public Boolean update(SyncCheckConfigEntity entity) {
        requireEntity(entity, "syncCheckConfig");
        requireId(entity.getId());
        entity.setUpdateTime(now());
        return syncCheckConfigDao.updateById(entity);
    }

    @Override
    public SyncCheckConfigEntity getById(Long id) {
        requireId(id);
        return syncCheckConfigDao.queryById(id);
    }

    @Override
    public List<SyncCheckConfigEntity> listByTaskId(Long taskId) {
        requireId(taskId);
        return syncCheckConfigDao.listByTaskId(taskId);
    }

    @Override
    public List<SyncCheckConfigEntity> listEnabledByTaskId(Long taskId) {
        requireId(taskId);
        return syncCheckConfigDao.listEnabledByTaskId(taskId);
    }

    @Override
    public SyncCheckConfigEntity getByTaskIdAndCheckCode(Long taskId, String checkCode) {
        requireId(taskId);
        if (isBlank(checkCode)) {
            return null;
        }
        return syncCheckConfigDao.queryByTaskIdAndCheckCode(taskId, checkCode);
    }
}
