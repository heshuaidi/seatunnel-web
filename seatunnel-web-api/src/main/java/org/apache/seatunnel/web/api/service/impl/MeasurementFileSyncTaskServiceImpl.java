package org.apache.seatunnel.web.api.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.web.api.service.MeasurementFileSyncTaskService;
import org.apache.seatunnel.web.api.service.file.FileSourceClient;
import org.apache.seatunnel.web.api.service.model.FileDataSourceConfig;
import org.apache.seatunnel.web.common.enums.MeasurementDedupStrategy;
import org.apache.seatunnel.web.common.enums.MeasurementDiscoveryMode;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.DataSource;
import org.apache.seatunnel.web.dao.entity.MeasurementFileSyncTaskEntity;
import org.apache.seatunnel.web.dao.repository.DataSourceDao;
import org.apache.seatunnel.web.dao.repository.MeasurementFileSyncTaskDao;
import org.apache.seatunnel.web.spi.bean.dto.MeasurementFileSyncTaskDTO;
import org.apache.seatunnel.web.spi.bean.entity.PaginationResult;
import org.apache.seatunnel.web.spi.bean.vo.MeasurementFileSyncTaskVO;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MeasurementFileSyncTaskServiceImpl implements MeasurementFileSyncTaskService {

    private static final String DEFAULT_WATERMARK_KEY = "default";
    private static final int DEFAULT_MAX_FILES_PER_RUN = 1000;
    private static final int DEFAULT_LOCK_TTL_MINUTES = 60;

    @Resource
    private MeasurementFileSyncTaskDao taskDao;

    @Resource
    private DataSourceDao dataSourceDao;

    @Resource
    private FileSourceClient fileSourceClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeasurementFileSyncTaskVO create(MeasurementFileSyncTaskDTO dto) {
        validateDto(dto, null);
        MeasurementFileSyncTaskEntity entity = toEntity(dto);
        Date now = new Date();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        taskDao.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MeasurementFileSyncTaskVO update(Long id, MeasurementFileSyncTaskDTO dto) {
        requireId(id);
        MeasurementFileSyncTaskEntity existing = loadTask(id);
        validateDto(dto, id);
        MeasurementFileSyncTaskEntity entity = toEntity(dto);
        entity.setId(existing.getId());
        entity.setCreateTime(existing.getCreateTime());
        entity.setUpdateTime(new Date());
        taskDao.updateById(entity);
        return toVO(loadTask(id));
    }

    @Override
    public MeasurementFileSyncTaskVO get(Long id) {
        requireId(id);
        return toVO(loadTask(id));
    }

    @Override
    public PaginationResult<MeasurementFileSyncTaskVO> page(MeasurementFileSyncTaskDTO dto) {
        MeasurementFileSyncTaskDTO safeDto = dto == null ? new MeasurementFileSyncTaskDTO() : dto;
        IPage<MeasurementFileSyncTaskEntity> page = taskDao.queryPage(safeDto);
        List<MeasurementFileSyncTaskVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PaginationResult.buildSuc(records, page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        requireId(id);
        loadTask(id);
        return taskDao.deleteById(id);
    }

    private void validateDto(MeasurementFileSyncTaskDTO dto, Long excludeId) {
        if (dto == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "measurementFileSyncTask");
        }
        if (StringUtils.isBlank(dto.getTaskName())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskName");
        }
        if (StringUtils.isBlank(dto.getTaskCode())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "taskCode");
        }
        if (dto.getParserType() == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "parserType");
        }
        if (dto.getSourceDatasourceId() == null || dto.getSourceDatasourceId() <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "sourceDatasourceId");
        }
        if (taskDao.existsByTaskCode(dto.getTaskCode(), excludeId)) {
            throw new ServiceException("Measurement file sync task code already exists: " + dto.getTaskCode());
        }
        DataSource dataSource = dataSourceDao.queryById(dto.getSourceDatasourceId());
        if (dataSource == null) {
            throw new ServiceException(Status.DATASOURCE_NOT_EXIST);
        }
        if (!fileSourceClient.supports(dataSource.getDbType())) {
            throw new ServiceException("Measurement file sync only supports LOCAL_FILE/NAS/FTP/SFTP datasource");
        }
        FileDataSourceConfig config = fileSourceClient.parseConfig(
                dataSource.getDbType(),
                dataSource.getConnectionParams()
        );
        if (Boolean.FALSE.equals(config.getEnabled())) {
            throw new ServiceException("Datasource is disabled: " + dataSource.getName());
        }
        if (StringUtils.isBlank(dto.getSourceRootPath()) && StringUtils.isBlank(config.getRootPath())) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "sourceRootPath");
        }
    }

    private MeasurementFileSyncTaskEntity toEntity(MeasurementFileSyncTaskDTO dto) {
        return MeasurementFileSyncTaskEntity.builder()
                .taskName(dto.getTaskName().trim())
                .taskCode(dto.getTaskCode().trim())
                .parserType(defaultParserType(dto.getParserType()))
                .sourceDatasourceId(dto.getSourceDatasourceId())
                .sourceRootPath(trimToNull(dto.getSourceRootPath()))
                .includePatterns(trimToNull(dto.getIncludePatterns()))
                .excludePatterns(trimToNull(dto.getExcludePatterns()))
                .recursive(Boolean.TRUE.equals(dto.getRecursive()))
                .maxDepth(dto.getMaxDepth())
                .minLastModifiedTime(dto.getMinLastModifiedTime())
                .fileStableSeconds(defaultNonNegative(dto.getFileStableSeconds(), 0))
                .enabled(dto.getEnabled() == null || dto.getEnabled())
                .discoveryMode(defaultDiscoveryMode(dto.getDiscoveryMode()))
                .watermarkKey(StringUtils.defaultIfBlank(dto.getWatermarkKey(), DEFAULT_WATERMARK_KEY))
                .currentWatermark(trimToNull(dto.getCurrentWatermark()))
                .dedupStrategy(defaultDedupStrategy(dto.getDedupStrategy()))
                .checksumEnabled(Boolean.TRUE.equals(dto.getChecksumEnabled()))
                .maxFilesPerRun(defaultPositive(dto.getMaxFilesPerRun(), DEFAULT_MAX_FILES_PER_RUN))
                .lockTtlMinutes(defaultPositive(dto.getLockTtlMinutes(), DEFAULT_LOCK_TTL_MINUTES))
                .scheduleCron(trimToNull(dto.getScheduleCron()))
                .description(trimToNull(dto.getDescription()))
                .build();
    }

    private MeasurementFileSyncTaskVO toVO(MeasurementFileSyncTaskEntity entity) {
        MeasurementFileSyncTaskVO vo = new MeasurementFileSyncTaskVO();
        vo.setId(entity.getId());
        vo.setTaskName(entity.getTaskName());
        vo.setTaskCode(entity.getTaskCode());
        vo.setParserType(entity.getParserType());
        vo.setSourceDatasourceId(entity.getSourceDatasourceId());
        DataSource dataSource = dataSourceDao.queryById(entity.getSourceDatasourceId());
        if (dataSource != null) {
            vo.setSourceDatasourceName(dataSource.getName());
            vo.setSourceType(dataSource.getDbType() == null ? null : dataSource.getDbType().name());
        }
        vo.setSourceRootPath(entity.getSourceRootPath());
        vo.setIncludePatterns(entity.getIncludePatterns());
        vo.setExcludePatterns(entity.getExcludePatterns());
        vo.setRecursive(entity.getRecursive());
        vo.setMaxDepth(entity.getMaxDepth());
        vo.setMinLastModifiedTime(entity.getMinLastModifiedTime());
        vo.setFileStableSeconds(entity.getFileStableSeconds());
        vo.setEnabled(entity.getEnabled());
        vo.setDiscoveryMode(entity.getDiscoveryMode());
        vo.setWatermarkKey(entity.getWatermarkKey());
        vo.setCurrentWatermark(entity.getCurrentWatermark());
        vo.setDedupStrategy(entity.getDedupStrategy());
        vo.setChecksumEnabled(entity.getChecksumEnabled());
        vo.setMaxFilesPerRun(entity.getMaxFilesPerRun());
        vo.setLockTtlMinutes(entity.getLockTtlMinutes());
        vo.setScheduleCron(entity.getScheduleCron());
        vo.setDescription(entity.getDescription());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private MeasurementFileSyncTaskEntity loadTask(Long id) {
        MeasurementFileSyncTaskEntity entity = taskDao.queryById(id);
        if (entity == null) {
            throw new ServiceException("Measurement file sync task not found, id=" + id);
        }
        return entity;
    }

    private MeasurementParserType defaultParserType(MeasurementParserType parserType) {
        return parserType == null ? MeasurementParserType.CUSTOM : parserType;
    }

    private MeasurementDiscoveryMode defaultDiscoveryMode(MeasurementDiscoveryMode discoveryMode) {
        return discoveryMode == null ? MeasurementDiscoveryMode.FULL_SCAN : discoveryMode;
    }

    private MeasurementDedupStrategy defaultDedupStrategy(MeasurementDedupStrategy dedupStrategy) {
        return dedupStrategy == null ? MeasurementDedupStrategy.PATH_SIZE_MTIME : dedupStrategy;
    }

    private int defaultNonNegative(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }

    private int defaultPositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "id");
        }
    }
}
