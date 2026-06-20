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
import org.apache.seatunnel.web.common.enums.MeasurementLoadBatchMode;
import org.apache.seatunnel.web.common.enums.MeasurementLoadMode;
import org.apache.seatunnel.web.common.enums.MeasurementParserType;
import org.apache.seatunnel.web.common.utils.JSONUtils;
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
    private static final String DEFAULT_PARSE_CHARSET = "UTF-8";
    private static final String DEFAULT_STAGING_FORMAT = "JSONL";
    private static final int DEFAULT_MAX_FILES_PER_RUN = 1000;
    private static final int DEFAULT_MAX_FILES_PER_PARSE_RUN = 100;
    private static final int DEFAULT_LOCK_TTL_MINUTES = 60;
    private static final int DEFAULT_PARSE_MAX_ERROR_ROWS = 100;
    private static final int DEFAULT_STAGING_RETENTION_DAYS = 7;

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
        if (StringUtils.isNotBlank(dto.getParserConfigJson()) && !JSONUtils.checkJsonValid(dto.getParserConfigJson(), false)) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "parserConfigJson");
        }
        if (dto.getTargetDatasourceId() != null && dto.getTargetDatasourceId() > 0) {
            DataSource target = dataSourceDao.queryById(dto.getTargetDatasourceId());
            if (target == null) {
                throw new ServiceException(Status.DATASOURCE_NOT_EXIST);
            }
            if (target.getDbType() != org.apache.seatunnel.web.spi.enums.DbType.STARROCKS) {
                throw new ServiceException("Measurement load target only supports StarRocks datasource");
            }
        }
    }

    private MeasurementFileSyncTaskEntity toEntity(MeasurementFileSyncTaskDTO dto) {
        return MeasurementFileSyncTaskEntity.builder()
                .taskName(dto.getTaskName().trim())
                .taskCode(dto.getTaskCode().trim())
                .parserType(defaultParserType(dto.getParserType()))
                .parserConfigJson(trimToNull(dto.getParserConfigJson()))
                .parseCharset(StringUtils.defaultIfBlank(dto.getParseCharset(), DEFAULT_PARSE_CHARSET))
                .parseMaxErrorRows(defaultPositive(dto.getParseMaxErrorRows(), DEFAULT_PARSE_MAX_ERROR_ROWS))
                .parseFailFast(Boolean.TRUE.equals(dto.getParseFailFast()))
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
                .stagingDir(trimToNull(dto.getStagingDir()))
                .stagingFormat(StringUtils.defaultIfBlank(dto.getStagingFormat(), DEFAULT_STAGING_FORMAT))
                .stagingRetentionDays(defaultPositive(dto.getStagingRetentionDays(), DEFAULT_STAGING_RETENTION_DAYS))
                .keepStagingFile(dto.getKeepStagingFile() == null || dto.getKeepStagingFile())
                .targetDatasourceId(dto.getTargetDatasourceId())
                .targetDatabase(trimToNull(dto.getTargetDatabase()))
                .targetTable(trimToNull(dto.getTargetTable()))
                .loadMode(defaultLoadMode(dto.getLoadMode()))
                .loadBatchMode(defaultLoadBatchMode(dto.getLoadBatchMode()))
                .starrocksNodeUrls(trimToNull(dto.getStarrocksNodeUrls()))
                .starrocksBaseUrl(trimToNull(dto.getStarrocksBaseUrl()))
                .maxFilesPerParseRun(defaultPositive(dto.getMaxFilesPerParseRun(), DEFAULT_MAX_FILES_PER_PARSE_RUN))
                .retryParseFailed(Boolean.TRUE.equals(dto.getRetryParseFailed()))
                .retryLoadFailed(Boolean.TRUE.equals(dto.getRetryLoadFailed()))
                .cleanupBeforeReload(Boolean.TRUE.equals(dto.getCleanupBeforeReload()))
                .seatunnelClientId(dto.getSeatunnelClientId())
                .description(trimToNull(dto.getDescription()))
                .build();
    }

    private MeasurementFileSyncTaskVO toVO(MeasurementFileSyncTaskEntity entity) {
        MeasurementFileSyncTaskVO vo = new MeasurementFileSyncTaskVO();
        vo.setId(entity.getId());
        vo.setTaskName(entity.getTaskName());
        vo.setTaskCode(entity.getTaskCode());
        vo.setParserType(entity.getParserType());
        vo.setParserConfigJson(entity.getParserConfigJson());
        vo.setParseCharset(entity.getParseCharset());
        vo.setParseMaxErrorRows(entity.getParseMaxErrorRows());
        vo.setParseFailFast(entity.getParseFailFast());
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
        vo.setStagingDir(entity.getStagingDir());
        vo.setStagingFormat(entity.getStagingFormat());
        vo.setStagingRetentionDays(entity.getStagingRetentionDays());
        vo.setKeepStagingFile(entity.getKeepStagingFile());
        vo.setTargetDatasourceId(entity.getTargetDatasourceId());
        if (entity.getTargetDatasourceId() != null) {
            DataSource target = dataSourceDao.queryById(entity.getTargetDatasourceId());
            if (target != null) {
                vo.setTargetDatasourceName(target.getName());
            }
        }
        vo.setTargetDatabase(entity.getTargetDatabase());
        vo.setTargetTable(entity.getTargetTable());
        vo.setLoadMode(entity.getLoadMode());
        vo.setLoadBatchMode(entity.getLoadBatchMode());
        vo.setStarrocksNodeUrls(entity.getStarrocksNodeUrls());
        vo.setStarrocksBaseUrl(entity.getStarrocksBaseUrl());
        vo.setMaxFilesPerParseRun(entity.getMaxFilesPerParseRun());
        vo.setRetryParseFailed(entity.getRetryParseFailed());
        vo.setRetryLoadFailed(entity.getRetryLoadFailed());
        vo.setCleanupBeforeReload(entity.getCleanupBeforeReload());
        vo.setSeatunnelClientId(entity.getSeatunnelClientId());
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

    private MeasurementLoadMode defaultLoadMode(MeasurementLoadMode loadMode) {
        return loadMode == null ? MeasurementLoadMode.APPEND : loadMode;
    }

    private MeasurementLoadBatchMode defaultLoadBatchMode(MeasurementLoadBatchMode loadBatchMode) {
        return loadBatchMode == null ? MeasurementLoadBatchMode.ONE_FILE_ONE_JOB : loadBatchMode;
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
