package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;

public interface SyncCheckSqlExecutor {

    Object executeScalar(SyncCheckConfigEntity config, String renderedSql);
}
