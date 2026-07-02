package org.apache.seatunnel.web.dao.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.seatunnel.web.dao.entity.MeasurementFileEntity;
import org.apache.seatunnel.web.common.enums.MeasurementFileStatus;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileQueryDTO;

import java.util.Date;
import java.util.List;
import java.util.Set;

public interface MeasurementFileDao extends IDao<MeasurementFileEntity> {

    IPage<MeasurementFileEntity> queryPage(MeasurementFileQueryDTO dto);

    MeasurementFileEntity findByPath(Long taskId, String fullPath);

    MeasurementFileEntity findByPathSizeMtime(
            Long taskId,
            String fullPath,
            Long fileSize,
            Date lastModifiedTime
    );

    MeasurementFileEntity findByPathChecksum(Long taskId, String fullPath, String checksum);

    List<MeasurementFileEntity> listByTaskAndStatuses(
            Long taskId,
            Set<MeasurementFileStatus> statuses,
            List<Long> fileIds,
            Integer limit
    );

    List<MeasurementFileEntity> listStaleByTaskAndStatuses(
            Long taskId,
            Set<MeasurementFileStatus> statuses,
            Date cutoffTime,
            Integer limit
    );
}
