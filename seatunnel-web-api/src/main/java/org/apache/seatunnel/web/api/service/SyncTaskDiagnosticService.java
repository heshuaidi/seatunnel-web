package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.spi.bean.dto.SyncCheckDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncHoconDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncRangePreviewRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncTaskDiagnoseRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncWatermarkUpdateRequest;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.vo.SyncAuditItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBatchListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncCheckDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncHoconDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncRangePreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncRunListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncTaskDiagnosticVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;

public interface SyncTaskDiagnosticService {

    SyncTaskDiagnosticVO diagnoseTask(String taskCode, SyncTaskDiagnoseRequest request);

    SyncRangePreviewVO previewRange(String taskCode, SyncRangePreviewRequest request);

    SyncHoconDiagnosticVO diagnoseHocon(String taskCode, SyncHoconDiagnoseRequest request);

    SyncCheckDiagnosticVO diagnoseChecks(String taskCode, SyncCheckDiagnoseRequest request);

    PaginationResult<SyncRunListItemVO> listRuns(
            String taskCode,
            Integer pageNo,
            Integer pageSize,
            String status,
            String startTime,
            String endTime
    );

    PaginationResult<SyncBatchListItemVO> listBatches(
            String taskCode,
            Integer pageNo,
            Integer pageSize,
            String status,
            String startTime,
            String endTime
    );

    SyncBatchListItemVO getBatch(String batchId);

    PaginationResult<SyncAuditItemVO> listRunAudits(
            String runId,
            Integer pageNo,
            Integer pageSize,
            String startTime,
            String endTime
    );

    PaginationResult<SyncAuditItemVO> listBatchAudits(
            String batchId,
            Integer pageNo,
            Integer pageSize,
            String startTime,
            String endTime
    );

    WatermarkVO updateWatermark(String taskCode, SyncWatermarkUpdateRequest request);
}
