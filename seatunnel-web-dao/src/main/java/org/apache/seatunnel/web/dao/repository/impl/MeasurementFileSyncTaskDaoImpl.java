package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.dao.entity.MeasurementFileSyncTaskEntity;
import org.apache.seatunnel.web.dao.mapper.MeasurementFileSyncTaskMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileSyncTaskDao;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileSyncTaskDTO;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
public class MeasurementFileSyncTaskDaoImpl
        extends BaseDao<MeasurementFileSyncTaskEntity, MeasurementFileSyncTaskMapper>
        implements MeasurementFileSyncTaskDao {

    private final MeasurementFileSyncTaskMapper mapper;

    public MeasurementFileSyncTaskDaoImpl(@NonNull MeasurementFileSyncTaskMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    @Override
    public IPage<MeasurementFileSyncTaskEntity> queryPage(MeasurementFileSyncTaskDTO dto) {
        LambdaQueryWrapper<MeasurementFileSyncTaskEntity> wrapper =
                new LambdaQueryWrapper<MeasurementFileSyncTaskEntity>()
                        .like(StringUtils.isNotBlank(dto.getTaskName()),
                                MeasurementFileSyncTaskEntity::getTaskName,
                                trim(dto.getTaskName()))
                        .eq(StringUtils.isNotBlank(dto.getTaskCode()),
                                MeasurementFileSyncTaskEntity::getTaskCode,
                                trim(dto.getTaskCode()))
                        .eq(dto.getParserType() != null,
                                MeasurementFileSyncTaskEntity::getParserType,
                                dto.getParserType())
                        .eq(dto.getSourceDatasourceId() != null,
                                MeasurementFileSyncTaskEntity::getSourceDatasourceId,
                                dto.getSourceDatasourceId())
                        .eq(dto.getEnabled() != null,
                                MeasurementFileSyncTaskEntity::getEnabled,
                                dto.getEnabled())
                        .orderByDesc(MeasurementFileSyncTaskEntity::getCreateTime);
        return mapper.selectPage(new Page<>(dto.getPageNo(), dto.getPageSize()), wrapper);
    }

    @Override
    public MeasurementFileSyncTaskEntity queryByTaskCode(String taskCode) {
        if (StringUtils.isBlank(taskCode)) {
            return null;
        }
        return mapper.selectOne(
                new LambdaQueryWrapper<MeasurementFileSyncTaskEntity>()
                        .eq(MeasurementFileSyncTaskEntity::getTaskCode, taskCode.trim())
                        .last("limit 1")
        );
    }

    @Override
    public boolean existsByTaskCode(String taskCode, Long excludeId) {
        if (StringUtils.isBlank(taskCode)) {
            return false;
        }
        LambdaQueryWrapper<MeasurementFileSyncTaskEntity> wrapper =
                new LambdaQueryWrapper<MeasurementFileSyncTaskEntity>()
                        .eq(MeasurementFileSyncTaskEntity::getTaskCode, taskCode.trim())
                        .ne(excludeId != null, MeasurementFileSyncTaskEntity::getId, excludeId);
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean updateWatermark(Long taskId, String currentWatermark) {
        if (taskId == null) {
            return false;
        }
        return mapper.update(
                null,
                new LambdaUpdateWrapper<MeasurementFileSyncTaskEntity>()
                        .set(MeasurementFileSyncTaskEntity::getCurrentWatermark, currentWatermark)
                        .set(MeasurementFileSyncTaskEntity::getUpdateTime, new Date())
                        .eq(MeasurementFileSyncTaskEntity::getId, taskId)
        ) > 0;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
