package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;

import java.util.List;

@Data
public class MeasurementParseLoadRequestDTO {

    private List<Long> fileIds;

    private Integer maxFiles;

    private Boolean retryParseFailed;

    private Boolean retryLoadFailed;

    private Boolean forceReload;

    private Boolean forceReloadConfirmed;

    private Boolean waitForLoadFinish;

    private SyncTriggerType triggerType;
}
