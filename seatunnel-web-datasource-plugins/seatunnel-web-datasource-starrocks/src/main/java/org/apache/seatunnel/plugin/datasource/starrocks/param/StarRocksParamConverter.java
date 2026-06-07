package org.apache.seatunnel.plugin.datasource.starrocks.param;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.plugin.datasource.api.constants.DataSourceConstants;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcParamConverter;
import org.apache.seatunnel.web.common.utils.JSONUtils;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.apache.seatunnel.web.spi.enums.DbType;

import java.util.Map;
import java.util.stream.Collectors;

public class StarRocksParamConverter implements JdbcParamConverter {

    @Override
    public BaseConnectionParam createConnectionParams(String connectionJson) {
        StarRocksConnectionParam param =
                JSONUtils.parseObject(connectionJson, StarRocksConnectionParam.class);

        if (param == null) {
            throw new IllegalArgumentException("StarRocks connection param must not be null");
        }

        normalizeUser(param);
        normalizePorts(param);
        normalizeDriver(param);
        normalizeUrl(param);
        param.setDbType(DbType.STARROCKS);

        return param;
    }

    @Override
    public void checkDatasourceParam(BaseConnectionParam baseConnectionParam) {
        StarRocksConnectionParam param = (StarRocksConnectionParam) baseConnectionParam;
        requireNonBlank(param.getHost(), "host");
        requireNonBlank(param.getDatabase(), "database");
        requireNonBlank(firstNonBlank(param.getUser(), param.getUsername()), "user");
        requireNonBlank(firstNonBlank(param.getQueryPort(), param.getPort()), "queryPort");
        requireNonBlank(param.getHttpPort(), "httpPort");
    }

    private void normalizeUser(StarRocksConnectionParam param) {
        if (StringUtils.isBlank(param.getUser()) && StringUtils.isNotBlank(param.getUsername())) {
            param.setUser(param.getUsername());
        }
        if (StringUtils.isBlank(param.getUsername()) && StringUtils.isNotBlank(param.getUser())) {
            param.setUsername(param.getUser());
        }
    }

    private void normalizePorts(StarRocksConnectionParam param) {
        String queryPort = firstNonBlank(
                param.getQueryPort(),
                param.getPort(),
                StarRocksConnectionParam.DEFAULT_QUERY_PORT);
        String httpPort = firstNonBlank(
                param.getHttpPort(),
                StarRocksConnectionParam.DEFAULT_HTTP_PORT);

        param.setQueryPort(queryPort);
        param.setPort(queryPort);
        param.setHttpPort(httpPort);
    }

    private void normalizeDriver(StarRocksConnectionParam param) {
        if (StringUtils.isBlank(param.getDriverLocation())) {
            param.setDriverLocation(StarRocksConnectionParam.DEFAULT_DRIVER_LOCATION);
        }
        if (StringUtils.isBlank(param.getDriver())) {
            param.setDriver(DataSourceConstants.COM_MYSQL_CJ_JDBC_DRIVER);
        }
    }

    private void normalizeUrl(StarRocksConnectionParam param) {
        String explicitUrl = firstNonBlank(param.getUrl(), param.getJdbcUrl());
        String url = StringUtils.isNotBlank(explicitUrl) ? explicitUrl : buildUrl(param);
        param.setUrl(url);
        param.setJdbcUrl(url);
    }

    private String buildUrl(StarRocksConnectionParam param) {
        String base = String.format("%s%s:%s/%s",
                DataSourceConstants.JDBC_MYSQL,
                param.getHost(),
                param.getQueryPort(),
                param.getDatabase());

        Map<String, String> other = param.getOtherAsMap();
        if (MapUtils.isEmpty(other)) {
            return base;
        }
        return base + "?" + buildQueryString(other);
    }

    private String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private void requireNonBlank(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("StarRocks connection param " + fieldName + " must not be blank");
        }
    }
}
