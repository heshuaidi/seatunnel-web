package org.apache.seatunnel.web.api.service;

import org.apache.seatunnel.web.spi.bean.dto.BackfillTaskRequest;
import org.apache.seatunnel.web.spi.bean.dto.PreviewHoconRequest;
import org.apache.seatunnel.web.spi.bean.dto.RunTaskRequest;
import org.apache.seatunnel.web.spi.bean.vo.HoconPreviewVO;
import org.apache.seatunnel.web.spi.bean.vo.RunDetailVO;
import org.apache.seatunnel.web.spi.bean.vo.RunResultVO;
import org.apache.seatunnel.web.spi.bean.vo.WatermarkVO;

import java.util.List;

public interface SyncRunCoordinatorService {

    HoconPreviewVO previewHocon(String taskCode, PreviewHoconRequest request);

    RunResultVO runTask(String taskCode, RunTaskRequest request);

    RunResultVO backfillTask(String taskCode, BackfillTaskRequest request);

    RunDetailVO getRun(String runId);

    List<WatermarkVO> getWatermark(String taskCode);
}
