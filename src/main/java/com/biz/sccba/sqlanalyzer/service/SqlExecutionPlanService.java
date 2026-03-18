package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.data.TableStructure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlExecutionPlanService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DataSourceManagerService dataSourceManagerService;

    /**
     * 获取 SQL 执行计划（JSON 格式）
     */
    public ExecutionPlan getExecutionPlan(String sql, String datasourceName) {
        JdbcTemplate jdbcTemplate = dataSourceManagerService.getJdbcTemplate(datasourceName);
        return getExecutionPlanInternal(sql, jdbcTemplate);
    }

    /**
     * 内部方法：使用指定的 JdbcTemplate 获取执行计划
     */
    private ExecutionPlan getExecutionPlanInternal(String sql, JdbcTemplate jdbcTemplate) {
        try {
            String explainSql = "EXPLAIN FORMAT=JSON " + sql;
            List<String> results = jdbcTemplate.query(explainSql,
                (rs, rowNum) -> rs.getString(1));

            if (!results.isEmpty()) {
                String jsonResult = results.get(0);
                ExecutionPlan plan = new ExecutionPlan();
                plan.setRawJson(jsonResult);

                try {
                    JsonNode jsonNode = objectMapper.readTree(jsonResult);
                    plan.parseFromJsonNode(jsonNode);
                } catch (Exception e) {
                    System.err.println("解析执行计划 JSON 失败：" + e.getMessage());
                }

                return plan;
            }
        } catch (DataAccessException e) {
            throw new RuntimeException("获取执行计划失败：" + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取 SQL 涉及的表结构信息
     */
    public List<TableStructure> getTableStructures(String sql, String datasourceName) {
        List<String> tableNames = parseTableNames(sql);
        return getTableStructuresByNames(tableNames, datasourceName);
    }

    /**
     * 按表名列表获取表结构信息
     */
    public List<TableStructure> getTableStructuresByNames(List<String> tableNames, String datasourceName) {
        JdbcTemplate jdbcTemplate = dataSourceManagerService.getJdbcTemplate(datasourceName);
        String databaseName = extractDatabaseName(datasourceName);
        List<TableStructure> structures = new ArrayList<>();

        for (String tableName : tableNames) {
            TableStructure structure = new TableStructure();
            structure.setTableName(tableName);
            structure.setColumns(getTableColumns(tableName, jdbcTemplate, databaseName));
            structure.setIndexes(getTableIndexes(tableName, jdbcTemplate, databaseName));
            structure.setStatistics(getTableStatistics(tableName, jdbcTemplate));
            structures.add(structure);
        }

        return structures;
    }

    /**
     * 解析 SQL 语句，提取表名
     */
    public List<String> parseTableNames(String sql) {
        List<String> tableNames = new ArrayList<>();
        Set<String> uniqueNames = new HashSet<>();

        sql = sql.replaceAll("--.*", "");
        sql = sql.replaceAll("/\\*.*?\\*/", "");

        Pattern pattern = Pattern.compile(
            "(?i)(?:FROM|JOIN|UPDATE|INTO)\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (tableName.contains(".")) {
                tableName = tableName.substring(tableName.indexOf(".") + 1);
            }
            if (!uniqueNames.contains(tableName.toLowerCase())) {
                uniqueNames.add(tableName.toLowerCase());
                tableNames.add(tableName);
            }
        }

        return tableNames;
    }

    /**
     * 获取表的列信息
     */
    private List<TableStructure.ColumnInfo> getTableColumns(String tableName, JdbcTemplate jdbcTemplate, String databaseName) {
        String sql = """
            SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_KEY,
                   COLUMN_DEFAULT, EXTRA
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> {
                TableStructure.ColumnInfo column = new TableStructure.ColumnInfo();
                column.setColumnName(rs.getString("COLUMN_NAME"));
                column.setDataType(rs.getString("DATA_TYPE"));
                column.setIsNullable(rs.getString("IS_NULLABLE"));
                column.setColumnKey(rs.getString("COLUMN_KEY"));
                column.setColumnDefault(rs.getString("COLUMN_DEFAULT"));
                column.setExtra(rs.getString("EXTRA"));
                return column;
            },
            databaseName, tableName);
    }

    /**
     * 获取表的索引信息
     */
    public List<TableStructure.IndexInfo> getTableIndexes(String tableName, JdbcTemplate jdbcTemplate, String databaseName) {
        String sql = """
            SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX, INDEX_TYPE
            FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY INDEX_NAME, SEQ_IN_INDEX
            """;

        return jdbcTemplate.query(sql,
            (rs, rowNum) -> {
                TableStructure.IndexInfo index = new TableStructure.IndexInfo();
                index.setIndexName(rs.getString("INDEX_NAME"));
                index.setColumnName(rs.getString("COLUMN_NAME"));
                index.setNonUnique(rs.getInt("NON_UNIQUE"));
                index.setSeqInIndex(rs.getInt("SEQ_IN_INDEX"));
                index.setIndexType(rs.getString("INDEX_TYPE"));
                return index;
            },
            databaseName, tableName);
    }

    /**
     * 获取表的统计信息
     */
    public TableStructure.TableStatistics getTableStatistics(String tableName, JdbcTemplate jdbcTemplate) {
        String sql = "SHOW TABLE STATUS WHERE Name = ?";

        List<TableStructure.TableStatistics> results = jdbcTemplate.query(sql,
            (rs, rowNum) -> {
                TableStructure.TableStatistics statistics = new TableStructure.TableStatistics();
                statistics.setRows(rs.getLong("Rows"));
                statistics.setDataLength(rs.getLong("Data_length"));
                statistics.setIndexLength(rs.getLong("Index_length"));
                statistics.setEngine(rs.getString("Engine"));
                return statistics;
            },
            tableName);

        return results.isEmpty() ? new TableStructure.TableStatistics() : results.get(0);
    }

    /**
     * 从数据源配置中提取数据库名称
     */
    private String extractDatabaseName(String datasourceName) {
        try {
            DataSourceManagerService.DataSourceInfo info =
                dataSourceManagerService.getAllDataSources().stream()
                    .filter(ds -> ds.getName().equals(datasourceName) ||
                            (datasourceName == null && ds.getName() != null))
                    .findFirst()
                    .orElse(null);

            if (info != null && info.getUrl() != null) {
                String url = info.getUrl();
                if (url.contains("/")) {
                    String[] parts = url.split("/");
                    if (parts.length > 1) {
                        String dbPart = parts[parts.length - 1];
                        if (dbPart.contains("?")) {
                            dbPart = dbPart.substring(0, dbPart.indexOf("?"));
                        }
                        return dbPart;
                    }
                }
            }
        } catch (Exception e) {
            // 如果获取失败，返回默认值
        }

        return "test_db";
    }
}
