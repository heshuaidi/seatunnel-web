package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.dao.entity.MeasurementFileRunEntity;
import org.apache.seatunnel.web.dao.mapper.MeasurementFileRunMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileRunDao;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileRunQueryDTO;
import org.springframework.stereotype.Repository;

@Repository
public class MeasurementFileRunDaoImpl
        extends BaseDao<MeasurementFileRunEntity, MeasurementFileRunMapper>
        implements MeasurementFileRunDao {

    private final MeasurementFileRunMapper mapper;

    public MeasurementFileRunDaoImpl(@NonNull MeasurementFileRunMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    @Override
    public IPage<MeasurementFileRunEntity> queryPage(MeasurementFileRunQueryDTO dto) {
        LambdaQueryWrapper<MeasurementFileRunEntity> wrapper = new LambdaQueryWrapper<MeasurementFileRunEntity>()
                .eq(dto.getTaskId() != null, MeasurementFileRunEntity::getTaskId, dto.getTaskId())
                .eq(StringUtils.isNotBlank(dto.getRunId()), MeasurementFileRunEntity::getRunId, trim(dto.getRunId()))
                .eq(StringUtils.isNotBlank(dto.getBatchId()),
                        MeasurementFileRunEntity::getBatchId,
                        trim(dto.getBatchId()))
                .eq(dto.getTriggerType() != null, MeasurementFileRunEntity::getTriggerType, dto.getTriggerType())
                .eq(dto.getStatus() != null, MeasurementFileRunEntity::getStatus, dto.getStatus())
                .orderByDesc(MeasurementFileRunEntity::getStartTime);
        return mapper.selectPage(new Page<>(dto.getPageNo(), dto.getPageSize()), wrapper);
    }

    @Override
    public MeasurementFileRunEntity queryByRunId(String runId) {
        if (StringUtils.isBlank(runId)) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<MeasurementFileRunEntity>()
                .eq(MeasurementFileRunEntity::getRunId, runId.trim())
                .last("limit 1"));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
