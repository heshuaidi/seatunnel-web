package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.api.service.model.VerifyResult;
import org.apache.seatunnel.web.dao.entity.SyncBatchEntity;
import org.apache.seatunnel.web.dao.entity.SyncRunEntity;
import org.apache.seatunnel.web.dao.entity.SyncTaskEntity;

import java.util.Map;

public interface SyncVerifyService {

    VerifyResult verifyRun(
            SyncTaskEntity task,
            SyncBatchEntity batch,
            SyncRunEntity run,
            Map<String, Object> variables
    );
}
