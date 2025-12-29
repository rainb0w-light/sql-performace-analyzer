package com.biz.sccba.sqlanalyzer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 列统计信息分析服务
 * 使用ANALYZE TABLE命令更新MySQL的直方图统计信息
 * 不再持久化到H2数据库，直接使用MySQL的information_schema.COLUMN_STATISTICS
 */
@Service
public class ColumnStatisticsCollectorService {

    private static final Logger logger = LoggerFactory.getLogger(ColumnStatisticsCollectorService.class);

    @Autowired
    private DataSourceManagerService dataSourceManagerService;

    /**
     * 执行ANALYZE TABLE更新指定表的列统计信息
     * 
     * @param tableName 表名
     * @param datasourceName 数据源名称
     * @param columns 要分析的列名列表（如果为null或空，则分析所有列）
     * @param bucketCount 直方图桶数量（默认100）
     * @return 执行结果信息
     */
    public AnalyzeTableResult analyzeTable(String tableName, String datasourceName, 
                                          List<String> columns, Integer bucketCount) {
        logger.info("开始执行ANALYZE TABLE: table={}, datasource={}, columns={}, buckets={}", 
                    tableName, datasourceName, columns, bucketCount);

        AnalyzeTableResult result = new AnalyzeTableResult();
        result.setTableName(tableName);
        result.setDatasourceName(datasourceName);
        result.setSuccess(false);

        try {
            JdbcTemplate jdbcTemplate = dataSourceManagerService.getJdbcTemplate(datasourceName);
            String databaseName = extractDatabaseName(datasourceName);

            // 如果没有指定列，获取所有列
            if (columns == null || columns.isEmpty()) {
                columns = getTableColumns(tableName, jdbcTemplate, databaseName);
            }

            // 构建ANALYZE TABLE语句
            String analyzeSql = buildAnalyzeTableSql(tableName, columns, bucketCount);
            
            // 执行ANALYZE TABLE并解析返回结果
            logger.info("执行ANALYZE TABLE: {}", analyzeSql);
            try {
                List<Map<String, Object>> analyzeResults = jdbcTemplate.queryForList(analyzeSql);
                parseAnalyzeTableResults(analyzeResults, tableName, columns, result);
                result.setSuccess(true);
            } catch (DataAccessException e) {
                logger.warn("ANALYZE TABLE执行失败（可能MySQL版本不支持或表不存在）: {}", e.getMessage());
                result.setErrorMessage(e.getMessage());
            }

            logger.info("ANALYZE TABLE执行完成: table={}, success={}", tableName, result.isSuccess());
            return result;

        } catch (Exception e) {
            logger.error("执行ANALYZE TABLE失败", e);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }
    
    /**
     * ANALYZE TABLE执行结果
     */
    public static class AnalyzeTableResult {
        private String tableName;
        private String datasourceName;
        private boolean success;
        private String errorMessage;
        private List<String> messages = new ArrayList<>();
        
        public String getTableName() {
            return tableName;
        }
        
        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
        
        public String getDatasourceName() {
            return datasourceName;
        }
        
        public void setDatasourceName(String datasourceName) {
            this.datasourceName = datasourceName;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public List<String> getMessages() {
            return messages;
        }
        
        public void setMessages(List<String> messages) {
            this.messages = messages;
        }
    }

    /**
     * 解析ANALYZE TABLE的返回结果，提取错误信息
     * ANALYZE TABLE返回的结果集包含：Table, Op, Msg_type, Msg_text
     */
    private void parseAnalyzeTableResults(List<Map<String, Object>> results, String tableName, 
                                         List<String> columns, AnalyzeTableResult result) {
        if (results == null || results.isEmpty()) {
            logger.info("ANALYZE TABLE未返回结果");
            return;
        }

        for (Map<String, Object> row : results) {
            String msgType = getStringValue(row, "Msg_type");
            String msgText = getStringValue(row, "Msg_text");
            String op = getStringValue(row, "Op");
            
            if (msgText != null) {
                result.getMessages().add(msgText);
            }
            
            if ("error".equalsIgnoreCase(msgType)) {
                logger.warn("ANALYZE TABLE执行错误: table={}, op={}, msg={}", tableName, op, msgText);
                result.setSuccess(false);
                if (result.getErrorMessage() == null) {
                    result.setErrorMessage(msgText);
                }
            } else if ("note".equalsIgnoreCase(msgType)) {
                logger.info("ANALYZE TABLE提示: table={}, op={}, msg={}", tableName, op, msgText);
            } else if ("status".equalsIgnoreCase(msgType)) {
                logger.debug("ANALYZE TABLE状态: table={}, op={}, msg={}", tableName, op, msgText);
            }
        }
    }

    /**
     * 安全获取Map中的字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
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
     * 批量执行ANALYZE TABLE更新多个表的统计信息
     */
    public List<AnalyzeTableResult> analyzeMultipleTables(
            List<String> tableNames, String datasourceName, Integer bucketCount) {
        List<AnalyzeTableResult> results = new ArrayList<>();
        
        for (String tableName : tableNames) {
            try {
                AnalyzeTableResult result = analyzeTable(tableName, datasourceName, null, bucketCount);
                results.add(result);
            } catch (Exception e) {
                logger.error("执行表 {} 的ANALYZE TABLE失败", tableName, e);
                AnalyzeTableResult errorResult = new AnalyzeTableResult();
                errorResult.setTableName(tableName);
                errorResult.setDatasourceName(datasourceName);
                errorResult.setSuccess(false);
                errorResult.setErrorMessage(e.getMessage());
                results.add(errorResult);
            }
        }
        
        return results;
    }
}
