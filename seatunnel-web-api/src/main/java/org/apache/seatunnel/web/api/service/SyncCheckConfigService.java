package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;

import java.util.List;

public interface SyncCheckConfigService {

    Long create(SyncCheckConfigEntity entity);

    Boolean update(SyncCheckConfigEntity entity);

    SyncCheckConfigEntity getById(Long id);

    List<SyncCheckConfigEntity> listByTaskId(Long taskId);

    List<SyncCheckConfigEntity> listEnabledByTaskId(Long taskId);

    SyncCheckConfigEntity getByTaskIdAndCheckCode(Long taskId, String checkCode);
}
