package org.apache.seatunnel.web.dao.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.seatunnel.web.dao.entity.MeasurementFileEntity;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileQueryDTO;

import java.util.Date;

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
}
