package org.apache.seatunnel.web.dao.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.common.enums.MeasurementFileStatus;
import org.apache.seatunnel.web.dao.entity.MeasurementFileEntity;
import org.apache.seatunnel.web.dao.mapper.MeasurementFileMapper;
import org.apache.seatunnel.web.dao.repository.BaseDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileDao;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileQueryDTO;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class MeasurementFileDaoImpl
        extends BaseDao<MeasurementFileEntity, MeasurementFileMapper>
        implements MeasurementFileDao {

    private final MeasurementFileMapper mapper;

    public MeasurementFileDaoImpl(@NonNull MeasurementFileMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    @Override
    public IPage<MeasurementFileEntity> queryPage(MeasurementFileQueryDTO dto) {
        LambdaQueryWrapper<MeasurementFileEntity> wrapper = new LambdaQueryWrapper<MeasurementFileEntity>()
                .eq(dto.getTaskId() != null, MeasurementFileEntity::getTaskId, dto.getTaskId())
                .eq(dto.getFileStatus() != null, MeasurementFileEntity::getFileStatus, dto.getFileStatus())
                .eq(StringUtils.isNotBlank(dto.getBatchId()), MeasurementFileEntity::getBatchId, trim(dto.getBatchId()))
                .eq(StringUtils.isNotBlank(dto.getRunId()), MeasurementFileEntity::getRunId, trim(dto.getRunId()))
                .like(StringUtils.isNotBlank(dto.getFileName()), MeasurementFileEntity::getFileName, trim(dto.getFileName()))
                .like(StringUtils.isNotBlank(dto.getRelativePath()),
                        MeasurementFileEntity::getRelativePath,
                        trim(dto.getRelativePath()))
                .orderByDesc(MeasurementFileEntity::getDiscoverTime);
        return mapper.selectPage(new Page<>(dto.getPageNo(), dto.getPageSize()), wrapper);
    }

    @Override
    public MeasurementFileEntity findByPath(Long taskId, String fullPath) {
        if (taskId == null || StringUtils.isBlank(fullPath)) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<MeasurementFileEntity>()
                .eq(MeasurementFileEntity::getTaskId, taskId)
                .eq(MeasurementFileEntity::getFullPath, fullPath)
                .last("limit 1"));
    }

    @Override
    public MeasurementFileEntity findByPathSizeMtime(
            Long taskId,
            String fullPath,
            Long fileSize,
            Date lastModifiedTime
    ) {
        if (taskId == null || StringUtils.isBlank(fullPath) || fileSize == null || lastModifiedTime == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<MeasurementFileEntity>()
                .eq(MeasurementFileEntity::getTaskId, taskId)
                .eq(MeasurementFileEntity::getFullPath, fullPath)
                .eq(MeasurementFileEntity::getFileSize, fileSize)
                .eq(MeasurementFileEntity::getLastModifiedTime, lastModifiedTime)
                .last("limit 1"));
    }

    @Override
    public MeasurementFileEntity findByPathChecksum(Long taskId, String fullPath, String checksum) {
        if (taskId == null || StringUtils.isBlank(fullPath) || StringUtils.isBlank(checksum)) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<MeasurementFileEntity>()
                .eq(MeasurementFileEntity::getTaskId, taskId)
                .eq(MeasurementFileEntity::getFullPath, fullPath)
                .eq(MeasurementFileEntity::getChecksum, checksum)
                .last("limit 1"));
    }

    @Override
    public List<MeasurementFileEntity> listByTaskAndStatuses(
            Long taskId,
            Set<MeasurementFileStatus> statuses,
            List<Long> fileIds,
            Integer limit
    ) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<MeasurementFileEntity> wrapper = new LambdaQueryWrapper<MeasurementFileEntity>()
                .eq(MeasurementFileEntity::getTaskId, taskId)
                .in(statuses != null && !statuses.isEmpty(), MeasurementFileEntity::getFileStatus, statuses)
                .in(fileIds != null && !fileIds.isEmpty(), MeasurementFileEntity::getId, fileIds)
                .orderByAsc(MeasurementFileEntity::getDiscoverTime)
                .orderByAsc(MeasurementFileEntity::getId);
        if (limit != null && limit > 0) {
            wrapper.last("limit " + limit);
        }
        return mapper.selectList(wrapper);
    }

    @Override
    public List<MeasurementFileEntity> listStaleByTaskAndStatuses(
            Long taskId,
            Set<MeasurementFileStatus> statuses,
            Date cutoffTime,
            Integer limit
    ) {
        if (taskId == null || cutoffTime == null || statuses == null || statuses.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<MeasurementFileEntity> wrapper = new LambdaQueryWrapper<MeasurementFileEntity>()
                .eq(MeasurementFileEntity::getTaskId, taskId)
                .in(MeasurementFileEntity::getFileStatus, statuses)
                .le(MeasurementFileEntity::getUpdateTime, cutoffTime)
                .orderByAsc(MeasurementFileEntity::getUpdateTime)
                .orderByAsc(MeasurementFileEntity::getId);
        if (limit != null && limit > 0) {
            wrapper.last("limit " + limit);
        }
        return mapper.selectList(wrapper);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
