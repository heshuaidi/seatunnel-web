package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.spi.bean.dto.MeasurementParseLoadRequestDTO;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementPreflightRequestDTO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementParseLoadResultVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementParsePreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementPreflightVO;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementSqlTemplateVO;

public interface MeasurementFileParseLoadService {

    MeasurementPreflightVO preflight(Long taskId, MeasurementPreflightRequestDTO request);

    MeasurementSqlTemplateVO recommendedDdl(Long taskId);

    MeasurementSqlTemplateVO cleanupSql(Long fileId);

    MeasurementSqlTemplateVO generatedHocon(String runId);

    MeasurementParseLoadResultVO parseAndLoad(Long taskId, MeasurementParseLoadRequestDTO request);

    MeasurementParseLoadResultVO parseOnly(Long taskId, MeasurementParseLoadRequestDTO request);

    MeasurementParseLoadResultVO loadParsed(Long taskId, MeasurementParseLoadRequestDTO request);

    MeasurementParseLoadResultVO retryFailedFile(Long fileId);

    MeasurementParseLoadResultVO markFileFailed(Long fileId);

    MeasurementParseLoadResultVO resetFilePending(Long fileId);

    MeasurementParseLoadResultVO repairStaleFiles(Long taskId, Integer timeoutMinutes, Boolean resetToPending);

    MeasurementParsePreviewVO previewParse(Long fileId, Integer maxRows);
}
