package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.seatunnel.web.common.enums.MeasurementRunStatus;
import org.apache.seatunnel.web.common.enums.SyncTriggerType;
import org.apache.seatunnel.web.spi.bean.dto.pagination.PaginationBaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class MeasurementFileRunQueryDTO extends PaginationBaseDTO {

    private Long taskId;

    private String runId;

    private String batchId;

    private SyncTriggerType triggerType;

    private MeasurementRunStatus status;
}
