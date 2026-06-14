package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalConfigRequest;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalPreviewRequest;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalRunRequest;
import org.apache.seatunnel.web.spi.bean.dto.BatchLinkUpIncrementalSqlTestRequest;
import org.apache.seatunnel.web.spi.bean.dto.SyncWatermarkUpdateRequest;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.vo.BatchLinkUpIncrementalConfigVO;
import org.apache.seatunnel.web.spi.bean.vo.BatchLinkUpIncrementalContextVO;
import org.apache.seatunnel.web.spi.bean.vo.BatchLinkUpIncrementalHoconPreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncBatchListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.SyncRunListItemVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;

import java.util.List;

public interface BatchLinkUpIncrementalService {

    BatchLinkUpIncrementalConfigVO getConfig(Long taskId);

    BatchLinkUpIncrementalConfigVO saveConfig(Long taskId, BatchLinkUpIncrementalConfigRequest request);

    BatchLinkUpIncrementalContextVO previewContext(Long taskId, BatchLinkUpIncrementalPreviewRequest request);

    BatchLinkUpIncrementalHoconPreviewVO previewHocon(Long taskId, BatchLinkUpIncrementalPreviewRequest request);

    RunResultVO runIncremental(Long taskId, BatchLinkUpIncrementalRunRequest request);

    PaginationResult<SyncRunListItemVO> listRuns(
            Long taskId,
            Integer pageNo,
            Integer pageSize,
            String status,
            String startTime,
            String endTime,
            String keyword
    );

    RunDetailVO getRun(Long taskId, String runId);

    PaginationResult<SyncBatchListItemVO> listBatches(Long taskId, Integer pageNo, Integer pageSize, String status);

    List<WatermarkVO> getWatermark(Long taskId);

    WatermarkVO updateWatermark(Long taskId, SyncWatermarkUpdateRequest request);

    BatchLinkUpIncrementalContextVO.SqlExecutionVO testSql(Long taskId, BatchLinkUpIncrementalSqlTestRequest request);
}
