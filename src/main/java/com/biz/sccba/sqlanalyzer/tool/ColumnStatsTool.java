package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.model.ColumnStatistics;
import com.biz.sccba.sqlanalyzer.service.TestEnvironmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 列统计信息工具
 * 收集和查询数据库列统计信息
 */
@Component
public class ColumnStatsTool {

    private final TestEnvironmentService testEnvironmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     */
    public ColumnStatsTool(TestEnvironmentService testEnvironmentService) {
        this.testEnvironmentService = testEnvironmentService;
    }

    /**
     * 收集列统计信息
     */
    @Tool(name = "collect_column_stats", description = "收集数据库表的列统计信息")
    public String collectColumnStatsTool(
            @ToolParam(name = "tableName", description = "表名", required = true) String tableName,
            @ToolParam(name = "datasourceName", description = "数据源名称 (可选)", required = false) String datasourceName) {
        System.out.println("[ColumnStatsTool] 收集表 " + tableName + " 的列统计信息 (数据源：" + datasourceName + ")");
        try {
            List<ColumnStatistics> statistics = collectTableStatistics(tableName, datasourceName, null, 100);
            return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "tableName", tableName,
                "datasourceName", datasourceName,
                "statistics", statistics,
                "count", statistics.size()
            ));
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 收集表的列统计信息
     */
    public List<ColumnStatistics> collectTableStatistics(
            String tableName,
            String datasourceName,
            List<String> columns,
            int sampleSize) {

        List<ColumnStatistics> results = new ArrayList<>();

        try {
            JdbcTemplate jdbcTemplate = testEnvironmentService.getJdbcTemplate(datasourceName);

            // 1. 获取表的所有列名
            String columnsSql = """
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """;

            List<Map<String, Object>> columnRows = jdbcTemplate.queryForList(columnsSql, tableName);

            for (Map<String, Object> col : columnRows) {
                String columnName = (String) col.get("COLUMN_NAME");
                String dataType = (String) col.get("DATA_TYPE");
                String isNullable = (String) col.get("IS_NULLABLE");

                // 如果指定了 columns 列表，只处理匹配的列
                if (columns != null && !columns.contains(columnName)) {
                    continue;
                }

                ColumnStatistics stats = collectColumnStats(jdbcTemplate, tableName, columnName, dataType, "YES".equals(isNullable), sampleSize);
                results.add(stats);
            }

        } catch (Exception e) {
            System.out.println("[ColumnStatsTool] 收集列统计信息失败：" + tableName + ", 错误：" + e.getMessage());
        }

        return results;
    }

    /**
     * 收集单列统计信息
     */
    private ColumnStatistics collectColumnStats(JdbcTemplate jdbcTemplate, String tableName,
                                                  String columnName, String dataType, boolean nullable, int sampleSize) {
        try {
            // 查询统计信息
            String statsSql = String.format("""
                SELECT
                    COUNT(*) as total_count,
                    SUM(CASE WHEN `%1$s` IS NULL THEN 1 ELSE 0 END) as null_count,
                    COUNT(DISTINCT `%1$s`) as distinct_count,
                    MIN(`%1$s`) as min_value,
                    MAX(`%1$s`) as max_value,
                    AVG(CASE WHEN `%1$s` IS NUMERIC THEN `%1$s` END) as avg_value
                FROM `%2$s`
                """, columnName, tableName);

            Map<String, Object> stats = jdbcTemplate.queryForMap(statsSql);

            // 采样值
            String sampleSql = String.format("""
                SELECT DISTINCT `%1$s`
                FROM `%2$s`
                WHERE `%1$s` IS NOT NULL
                ORDER BY RAND()
                LIMIT %3$d
                """, columnName, tableName, sampleSize);
            List<Map<String, Object>> samples = jdbcTemplate.queryForList(sampleSql);
            List<Object> sampleValues = samples.stream()
                .map(m -> m.get(columnName))
                .filter(java.util.Objects::nonNull)
                .toList();

            return new ColumnStatistics(
                tableName,
                columnName,
                dataType,
                nullable,
                stats.get("total_count") != null ? ((Number) stats.get("total_count")).longValue() : 0L,
                stats.get("null_count") != null ? ((Number) stats.get("null_count")).longValue() : 0L,
                stats.get("distinct_count") != null ? ((Number) stats.get("distinct_count")).longValue() : 0L,
                stats.get("min_value"),
                stats.get("max_value"),
                stats.get("avg_value") != null ? ((Number) stats.get("avg_value")).doubleValue() : null,
                sampleValues,
                null,
                ""
            );

        } catch (Exception e) {
            System.out.println("[ColumnStatsTool] 收集列统计信息失败：" + tableName + "." + columnName + ", 错误：" + e.getMessage());
            return new ColumnStatistics(
                tableName,
                columnName,
                dataType,
                nullable,
                0L, 0L, 0L,
                null, null, null,
                new ArrayList<>(),
                null,
                ""
            );
        }
    }

    /**
     * 收集表的列统计信息（便捷方法）
     *
     * @param tableName      表名
     * @param datasourceName 数据源名称 (可选)
     * @return 列统计信息列表
     */
    public List<ColumnStatistics> collectStatistics(String tableName, String datasourceName) {
        System.out.println("[ColumnStatsTool] 收集表 " + tableName + " 的列统计信息 (数据源：" + datasourceName + ")");
        return collectTableStatistics(tableName, datasourceName, null, 100);
    }

    /**
     * 收集指定列的统计信息（便捷方法）
     *
     * @param tableName      表名
     * @param datasourceName 数据源名称 (可选)
     * @param columns        列名列表
     * @return 列统计信息列表
     */
    public List<ColumnStatistics> collectStatisticsForColumns(
            String tableName,
            String datasourceName,
            List<String> columns) {
        System.out.println("[ColumnStatsTool] 收集表 " + tableName + " 的指定列统计信息：" + columns + " (数据源：" + datasourceName + ")");
        return collectTableStatistics(tableName, datasourceName, columns, 100);
    }

    /**
     * 获取已缓存的列统计信息（便捷方法）
     *
     * @param tableName      表名
     * @param datasourceName 数据源名称
     * @return 列统计信息列表
     */
    public List<ColumnStatistics> getCachedStatistics(String tableName, String datasourceName) {
        System.out.println("[ColumnStatsTool] 获取表 " + tableName + " 的缓存列统计信息 (数据源：" + datasourceName + ")");
        // Caching not directly available, return empty list
        return new ArrayList<>();
    }

    /**
     * 获取指定列的已缓存统计信息（便捷方法）
     *
     * @param tableName      表名
     * @param columnName     列名
     * @param datasourceName 数据源名称
     * @return 列统计信息
     */
    public ColumnStatistics getCachedColumnStatistics(
            String tableName,
            String columnName,
            String datasourceName) {
        System.out.println("[ColumnStatsTool] 获取列 " + tableName + "." + columnName + " 的缓存统计信息");
        // Caching not directly available, return null
        return null;
    }
}
