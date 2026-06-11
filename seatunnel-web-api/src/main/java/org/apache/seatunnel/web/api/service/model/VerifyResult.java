package org.apache.seatunnel.web.api.service.model;

import lombok.Data;
import org.apache.seatunnel.web.dao.entity.SyncCheckResultEntity;

import java.util.ArrayList;
import java.util.List;

@Data
public class VerifyResult {

    private boolean passed = true;

    private boolean hasBlockingFailure;

    private Long sourceCount;

    private Long sinkCount;

    private Long errorCount;

    private List<SyncCheckResultEntity> results = new ArrayList<>();

    private String errorMessage;
}
