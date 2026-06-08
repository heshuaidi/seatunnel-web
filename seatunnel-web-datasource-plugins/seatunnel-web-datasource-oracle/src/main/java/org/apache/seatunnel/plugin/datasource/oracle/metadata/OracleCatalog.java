package org.apache.seatunnel.plugin.datasource.oracle.metadata;

import lombok.extern.slf4j.Slf4j;
import org.apache.seatunnel.plugin.datasource.api.jdbc.AbstractJdbcCatalog;
import org.apache.seatunnel.plugin.datasource.api.jdbc.JdbcConnectionProvider;
import org.apache.seatunnel.plugin.datasource.api.jdbc.TablePath;
import org.apache.seatunnel.plugin.datasource.api.modal.DataSourceTableColumn;
import org.apache.seatunnel.web.spi.bean.vo.OptionVO;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class OracleCatalog extends AbstractJdbcCatalog {

    private static final String SELECT_COLUMNS_SQL_TEMPLATE =
            "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE, COLUMN_ID " +
                    "FROM ALL_TAB_COLUMNS " +
                    "WHERE OWNER = '%s' AND TABLE_NAME = '%s' ORDER BY COLUMN_ID ASC";

    private static final String SELECT_SPECIFIED_COLUMNS_SQL_TEMPLATE =
            "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE, COLUMN_ID " +
                    "FROM ALL_TAB_COLUMNS " +
                    "WHERE OWNER = '%s' AND TABLE_NAME = '%s' AND COLUMN_NAME IN (%s) ORDER BY COLUMN_ID ASC";

    private static final String LIST_TABLES_BY_OWNER_SQL =
            "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ? ORDER BY TABLE_NAME";

    private static final String LIST_CURRENT_USER_TABLES_SQL =
            "SELECT TABLE_NAME FROM USER_TABLES ORDER BY TABLE_NAME";

    private final BaseConnectionParam param;

    public OracleCatalog(BaseConnectionParam param, JdbcConnectionProvider connectionManager) {
        super(param, connectionManager);
        this.param = param;
    }

    @Override
    protected String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    protected String formatDateTimeLiteral(LocalDateTime dateTime) {
        return "TO_TIMESTAMP('" +
                dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) +
                "', 'YYYY-MM-DD HH24:MI:SS')";
    }

    @Override
    protected String applyLimit(String sql, int limit) {
        return sql + " FETCH NEXT " + limit + " ROWS ONLY";
    }

    @Override
    protected String getTableName(ResultSet rs) throws SQLException {
        return rs.getString(1);
    }

    @Override
    public List<String> listTables() {
        try {
            List<String> tables = queryTableNames();
            logListTables(tables.size());
            return tables;
        } catch (Exception e) {
            throw new RuntimeException("Failed listing database in catalog ORACLE", e);
        }
    }

    @Override
    public List<OptionVO> listTableOptions() {
        try {
            List<String> tables = queryTableNames();
            logListTables(tables.size());

            List<OptionVO> options = new ArrayList<>(tables.size());
            for (String table : tables) {
                OptionVO option = new OptionVO();
                option.setValue(table);
                option.setLabel(table);
                option.setDescription(null);
                options.add(option);
            }
            return options;
        } catch (Exception e) {
            throw new RuntimeException("Failed listing database in catalog ORACLE", e);
        }
    }

    @Override
    protected DataSourceTableColumn buildColumn(Map<String, Object> item) {
        String columnName = item.get("COLUMN_NAME").toString();
        String columnType = buildColumnType(item);
        String isNullable = item.get("NULLABLE").toString();
        String columnComment = item.getOrDefault("COMMENTS", "").toString();
        String columnKey = item.getOrDefault("COLUMN_KEY", "").toString();
        int ordinalPosition = Integer.parseInt(item.get("COLUMN_ID").toString());

        return DataSourceTableColumn.builder()
                .isNullable(isNullable)
                .columnComment(columnComment)
                .columnKey(columnKey)
                .columnName(columnName)
                .sourceType(columnType)
                .ordinalPosition(ordinalPosition)
                .build();
    }

    @Override
    protected String getSelectColumnsSql(TablePath tablePath) {
        OracleTablePath oracleTablePath = resolveTablePath(tablePath);
        return String.format(
                SELECT_COLUMNS_SQL_TEMPLATE,
                escapeSqlLiteral(oracleTablePath.owner),
                escapeSqlLiteral(oracleTablePath.tableName));
    }

    @Override
    protected String getSpecifiedColumnSql(TablePath tablePath, List<DataSourceTableColumn> columns) {
        List<String> columnNames = columns.stream()
                .map(DataSourceTableColumn::getColumnName)
                .collect(Collectors.toList());

        String quotedColumnNames = columnNames.stream()
                .map(this::normalizeOracleIdentifier)
                .map(name -> "'" + escapeSqlLiteral(name) + "'")
                .collect(Collectors.joining(", "));

        OracleTablePath oracleTablePath = resolveTablePath(tablePath);
        return String.format(SELECT_SPECIFIED_COLUMNS_SQL_TEMPLATE,
                escapeSqlLiteral(oracleTablePath.owner),
                escapeSqlLiteral(oracleTablePath.tableName),
                quotedColumnNames);
    }

    @Override
    protected String getListTableSql(String databaseName) {
        String owner = resolveOwner(databaseName);
        if (StringUtils.isBlank(owner)) {
            return LIST_CURRENT_USER_TABLES_SQL;
        }
        return "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = '" + escapeSqlLiteral(owner) + "' ORDER BY TABLE_NAME";
    }

    List<String> queryTableNames() throws SQLException {
        String owner = resolveOwner();
        String sql = StringUtils.isBlank(owner) ? LIST_CURRENT_USER_TABLES_SQL : LIST_TABLES_BY_OWNER_SQL;

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            if (StringUtils.isNotBlank(owner)) {
                ps.setString(1, owner);
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(getTableName(rs));
                }
                return result;
            }
        }
    }

    String resolveOwner() {
        return resolveOwner(null);
    }

    String resolveOwner(String requestedOwner) {
        return normalizeOracleIdentifier(
                firstNonBlank(
                        requestedOwner,
                        param.getSchemaName(),
                        param.getUser(),
                        param.getDatabase()));
    }

    OracleTablePath resolveTablePath(TablePath tablePath) {
        String rawTableName = tablePath.getTableName();
        String[] parts = rawTableName.split("\\.");

        if (parts.length >= 2) {
            return new OracleTablePath(
                    normalizeOracleIdentifier(parts[parts.length - 2]),
                    normalizeOracleIdentifier(parts[parts.length - 1]));
        }

        return new OracleTablePath(
                resolveOwner(tablePath.getSchemaName()),
                normalizeOracleIdentifier(rawTableName));
    }

    String normalizeOracleIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return "";
        }

        String trimmed = identifier.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String buildColumnType(Map<String, Object> item) {
        String dataType = item.get("DATA_TYPE").toString();
        if ("NUMBER".equalsIgnoreCase(dataType)) {
            Object precision = item.get("DATA_PRECISION");
            Object scale = item.get("DATA_SCALE");
            if (precision != null) {
                if (scale != null && !"0".equals(scale.toString())) {
                    return dataType + "(" + precision + "," + scale + ")";
                }
                return dataType + "(" + precision + ")";
            }
            return dataType;
        }

        if (List.of("CHAR", "NCHAR", "VARCHAR2", "NVARCHAR2").contains(dataType.toUpperCase(Locale.ROOT))) {
            Object length = item.get("DATA_LENGTH");
            if (length != null) {
                return dataType + "(" + length + ")";
            }
        }

        return dataType;
    }

    private String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private void logListTables(int tableCount) {
        log.info(
                "Oracle catalog list tables, url={}, username={}, owner={}, tableCount={}",
                param.getUrl(),
                param.getUser(),
                resolveOwner(),
                tableCount);
    }

    static class OracleTablePath {
        private final String owner;
        private final String tableName;

        OracleTablePath(String owner, String tableName) {
            this.owner = owner;
            this.tableName = tableName;
        }

        String getOwner() {
            return owner;
        }

        String getTableName() {
            return tableName;
        }
    }
}
