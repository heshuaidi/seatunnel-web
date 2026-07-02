package org.apache.seatunnel.web.spi.bean.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.seatunnel.web.common.enums.MeasurementFileStatus;
import org.apache.seatunnel.web.spi.bean.dto.pagination.PaginationBaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class MeasurementFileQueryDTO extends PaginationBaseDTO {

    private Long taskId;

    private MeasurementFileStatus fileStatus;

    private String batchId;

    private String runId;

    private String fileName;

    private String relativePath;
}
