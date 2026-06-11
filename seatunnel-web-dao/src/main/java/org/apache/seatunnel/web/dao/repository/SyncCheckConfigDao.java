package org.apache.seatunnel.web.dao.repository;

import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;

import java.util.List;

public interface SyncCheckConfigDao extends IDao<SyncCheckConfigEntity> {

    List<SyncCheckConfigEntity> listByTaskId(Long taskId);

    List<SyncCheckConfigEntity> listEnabledByTaskId(Long taskId);

    SyncCheckConfigEntity queryByTaskIdAndCheckCode(Long taskId, String checkCode);
}
