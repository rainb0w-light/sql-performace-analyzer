package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ColumnStatistics;
import com.biz.sccba.sqlanalyzer.repository.ColumnStatisticsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 列统计信息收集服务
 * 使用ANALYZE TABLE收集直方图数据，并解析information_schema.column_statistics
 */
@Service
public class ColumnStatisticsCollectorService {

    private static final Logger logger = LoggerFactory.getLogger(ColumnStatisticsCollectorService.class);

    @Autowired
    private DataSourceManagerService dataSourceManagerService;

    @Autowired
    private ColumnStatisticsRepository columnStatisticsRepository;

    @Autowired
    private ColumnStatisticsParserService parserService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 收集指定表的所有列的统计信息
     * 
     * @param tableName 表名
     * @param datasourceName 数据源名称
     * @param columns 要收集的列名列表（如果为null或空，则收集所有列）
     * @param bucketCount 直方图桶数量（默认100）
     * @return 收集到的统计信息列表
     */
    @Transactional
    public List<ColumnStatistics> collectTableStatistics(String tableName, String datasourceName, 
                                                          List<String> columns, Integer bucketCount) {
        logger.info("开始收集表统计信息: table={}, datasource={}, columns={}, buckets={}", 
                    tableName, datasourceName, columns, bucketCount);

        try {
            JdbcTemplate jdbcTemplate = dataSourceManagerService.getJdbcTemplate(datasourceName);
            String databaseName = extractDatabaseName(datasourceName);

            // 如果没有指定列，获取所有列
            if (columns == null || columns.isEmpty()) {
                columns = getTableColumns(tableName, jdbcTemplate, databaseName);
            }

            // 构建ANALYZE TABLE语句
            String analyzeSql = buildAnalyzeTableSql(tableName, columns, bucketCount);
            
            // 执行ANALYZE TABLE
            logger.info("执行ANALYZE TABLE: {}", analyzeSql);
            try {
                jdbcTemplate.execute(analyzeSql);
                logger.info("ANALYZE TABLE执行成功");
            } catch (DataAccessException e) {
                logger.warn("ANALYZE TABLE执行失败（可能MySQL版本不支持或表不存在）: {}", e.getMessage());
                // 继续尝试从information_schema获取已有统计信息
            }

            // 从information_schema.column_statistics获取统计信息
            List<ColumnStatistics> statistics = fetchColumnStatisticsFromInformationSchema(
                tableName, datasourceName, databaseName, jdbcTemplate, columns);

            // 保存到H2数据库
            for (ColumnStatistics stat : statistics) {
                // 检查是否已存在
                columnStatisticsRepository
                    .findByDatasourceNameAndTableNameAndColumnName(
                        stat.getDatasourceName(), stat.getTableName(), stat.getColumnName())
                    .ifPresent(existing -> {
                        stat.setId(existing.getId());
                        stat.setCreatedAt(existing.getCreatedAt());
                    });
                
                columnStatisticsRepository.save(stat);
            }

            logger.info("收集完成，共收集 {} 个列的统计信息", statistics.size());
            return statistics;

        } catch (Exception e) {
            logger.error("收集表统计信息失败", e);
            throw new RuntimeException("收集表统计信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建ANALYZE TABLE语句
     */
    private String buildAnalyzeTableSql(String tableName, List<String> columns, Integer bucketCount) {
        if (bucketCount == null || bucketCount <= 0) {
            bucketCount = 100; // 默认100个桶
        }

        StringBuilder sql = new StringBuilder("ANALYZE TABLE `");
        sql.append(tableName).append("` UPDATE HISTOGRAM ON ");

        // 添加列名
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("`").append(columns.get(i)).append("`");
        }

        sql.append(" WITH ").append(bucketCount).append(" BUCKETS");

        return sql.toString();
    }

    /**
     * 从information_schema.column_statistics获取列统计信息
     */
    private List<ColumnStatistics> fetchColumnStatisticsFromInformationSchema(
            String tableName, String datasourceName, String databaseName,
            JdbcTemplate jdbcTemplate, List<String> columns) {
        
        List<ColumnStatistics> statistics = new ArrayList<>();

        try {
            // 查询information_schema.column_statistics
            String sql = """
                SELECT 
                    SCHEMA_NAME,
                    TABLE_NAME,
                    COLUMN_NAME,
                    HISTOGRAM
                FROM information_schema.COLUMN_STATISTICS
                WHERE SCHEMA_NAME = ? AND TABLE_NAME = ?
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, databaseName, tableName);

            for (Map<String, Object> row : results) {
                String columnName = (String) row.get("COLUMN_NAME");
                
                // 如果指定了列列表，只处理指定的列
                if (columns != null && !columns.isEmpty() && !columns.contains(columnName)) {
                    continue;
                }

                String histogramJson = (String) row.get("HISTOGRAM");
                if (histogramJson == null || histogramJson.trim().isEmpty()) {
                    logger.debug("列 {} 没有直方图数据", columnName);
                    continue;
                }

                // 使用解析服务解析JSON数据
                ColumnStatistics stat = parserService.parseHistogramJson(
                    histogramJson, datasourceName, databaseName, tableName, columnName);
                
                if (stat != null) {
                    statistics.add(stat);
                }
            }

        } catch (DataAccessException e) {
            logger.warn("查询information_schema.column_statistics失败: {}", e.getMessage());
            // 如果表不存在或MySQL版本不支持，返回空列表
        }

        return statistics;
    }

    /**
     * 获取表的所有列名
     */
    private List<String> getTableColumns(String tableName, JdbcTemplate jdbcTemplate, String databaseName) {
        String sql = """
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;

        return jdbcTemplate.queryForList(sql, String.class, databaseName, tableName);
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
                // jdbc:mysql://localhost:3306/test_db?...
                if (url.contains("/")) {
                    String[] parts = url.split("/");
                    if (parts.length > 1) {
                        String dbPart = parts[parts.length - 1];
                        // 移除查询参数
                        if (dbPart.contains("?")) {
                            dbPart = dbPart.substring(0, dbPart.indexOf("?"));
                        }
                        return dbPart;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("提取数据库名失败: {}", e.getMessage());
        }
        
        return "test_db"; // 默认值
    }

    /**
     * 批量收集多个表的统计信息
     */
    @Transactional
    public List<ColumnStatistics> collectMultipleTablesStatistics(
            List<String> tableNames, String datasourceName, Integer bucketCount) {
        List<ColumnStatistics> allStatistics = new ArrayList<>();
        
        for (String tableName : tableNames) {
            try {
                List<ColumnStatistics> stats = collectTableStatistics(
                    tableName, datasourceName, null, bucketCount);
                allStatistics.addAll(stats);
            } catch (Exception e) {
                logger.error("收集表 {} 的统计信息失败", tableName, e);
            }
        }
        
        return allStatistics;
    }
}
