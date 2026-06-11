package org.apache.seatunnel.web.api.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.plugin.datasource.api.utils.DataSourceUtils;
import org.apache.seatunnel.web.api.service.DataSourceService;
import org.apache.seatunnel.web.api.service.SyncCheckSqlExecutor;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.DataSource;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SyncCheckSqlExecutorImpl implements SyncCheckSqlExecutor {

    private static final Pattern FORBIDDEN_SQL_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|REPLACE|MERGE|CALL)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Resource
    private DataSourceService dataSourceService;

    @Resource
    private SyncRunProperties syncRunProperties;

    @Override
    public Object executeScalar(SyncCheckConfigEntity config, String renderedSql) {
        if (config == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "syncCheckConfig");
        }
        validateReadOnlySql(renderedSql);
        if (config.getDatasourceId() == null) {
            throw new ServiceException("Sync check datasourceId is required, checkCode=" + config.getCheckCode());
        }

        DataSource dataSource = dataSourceService.selectById(config.getDatasourceId());
        try {
            BaseConnectionParam connectionParam = DataSourceUtils.buildConnectionParams(
                    dataSource.getDbType(),
                    dataSource.getConnectionParams()
            );
            try (Connection connection = DataSourceUtils
                    .getDatasourceProcessor(dataSource.getDbType())
                    .getConnectionManager()
                    .getConnection(connectionParam);
                 Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(syncRunProperties.getCheckSqlTimeoutSeconds());
                try (ResultSet resultSet = statement.executeQuery(renderedSql)) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    if (metaData.getColumnCount() != 1) {
                        throw new ServiceException("Sync check SQL must return exactly one column, checkCode="
                                + config.getCheckCode());
                    }
                    if (!resultSet.next()) {
                        return null;
                    }
                    Object value = resultSet.getObject(1);
                    if (resultSet.next()) {
                        throw new ServiceException("Sync check SQL must return at most one row, checkCode="
                                + config.getCheckCode());
                    }
                    return value;
                }
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Execute sync check SQL failed, checkCode={}", config.getCheckCode(), e);
            throw new ServiceException("Execute sync check SQL failed, checkCode="
                    + config.getCheckCode()
                    + ", error="
                    + e.getMessage(), e);
        }
    }

    private void validateReadOnlySql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "sqlText");
        }
        String normalized = stripLeadingSqlComments(sql).trim();
        String upper = normalized.toUpperCase();
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            throw new ServiceException("Sync check SQL only supports SELECT or WITH statements");
        }
        if (FORBIDDEN_SQL_PATTERN.matcher(normalized).find()) {
            throw new ServiceException("Sync check SQL contains forbidden write or DDL keyword");
        }
    }

    private String stripLeadingSqlComments(String sql) {
        String value = sql == null ? "" : sql.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (value.startsWith("--")) {
                int end = value.indexOf('\n');
                value = end < 0 ? "" : value.substring(end + 1).trim();
                changed = true;
            } else if (value.startsWith("/*")) {
                int end = value.indexOf("*/");
                value = end < 0 ? "" : value.substring(end + 2).trim();
                changed = true;
            }
        }
        return value;
    }
}
