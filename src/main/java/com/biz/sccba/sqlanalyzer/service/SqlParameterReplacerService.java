package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.TableStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL参数替换服务
 * 将MyBatis SQL中的占位符（如#{id}）替换为数据库中的实际样本值
 */
@Service
public class SqlParameterReplacerService {

    private static final Logger logger = LoggerFactory.getLogger(SqlParameterReplacerService.class);

    @Autowired
    private SqlExecutionPlanService sqlExecutionPlanService;

    @Autowired
    private DataSourceManagerService dataSourceManagerService;

    // 匹配MyBatis占位符的正则表达式：#{paramName} 或 ${paramName}
    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{([^}]+)\\}|\\$\\{([^}]+)\\}");

    /**
     * 替换SQL中的占位符为实际值
     * @param sql 包含占位符的SQL
     * @param tableName 主表名（用于获取样本数据）
     * @param datasourceName 数据源名称
     * @return 替换后的可执行SQL
     */
    public String replaceParameters(String sql, String tableName, String datasourceName) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        try {
            // 获取表结构
            String testSql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(testSql, datasourceName);
            
            if (structures.isEmpty()) {
                logger.warn("无法获取表结构: {}", tableName);
                return sql;
            }

            TableStructure tableStructure = structures.get(0);
            Map<String, TableStructure.ColumnInfo> columnMap = new HashMap<>();
            if (tableStructure.getColumns() != null) {
                for (TableStructure.ColumnInfo col : tableStructure.getColumns()) {
                    columnMap.put(col.getColumnName().toLowerCase(), col);
                }
            }

            // 获取样本数据
            Map<String, Object> sampleData = getSampleData(tableName, datasourceName);

            // 替换占位符
            String replacedSql = replacePlaceholders(sql, columnMap, sampleData, tableName, datasourceName);

            logger.debug("SQL参数替换: {} -> {}", sql, replacedSql);
            return replacedSql;

        } catch (Exception e) {
            logger.error("替换SQL参数失败", e);
            // 如果替换失败，返回原SQL（可能导致执行计划获取失败，但不会影响其他功能）
            return sql;
        }
    }

    /**
     * 替换占位符
     */
    private String replacePlaceholders(String sql, Map<String, TableStructure.ColumnInfo> columnMap,
                                      Map<String, Object> sampleData, String tableName, String datasourceName) {
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();
        Set<String> processedParams = new HashSet<>();
        
        // 获取扩展样本数据（包含统计信息）
        Map<String, ColumnSampleData> extendedSampleData = getExtendedSampleData(tableName, datasourceName, columnMap);

        while (matcher.find()) {
            String paramName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String replacement = getReplacementValue(paramName, columnMap, sampleData, extendedSampleData, 
                                                    tableName, datasourceName, sql);
            
            // 转义特殊字符，避免在replaceReplacement中使用$等特殊字符
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            processedParams.add(paramName);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 获取替换值
     */
    private String getReplacementValue(String paramName, Map<String, TableStructure.ColumnInfo> columnMap,
                                      Map<String, Object> sampleData, Map<String, ColumnSampleData> extendedSampleData,
                                      String tableName, String datasourceName, String sql) {
        // 提取列名（去掉可能的表别名前缀）
        String columnName = paramName;
        if (paramName.contains(".")) {
            columnName = paramName.substring(paramName.lastIndexOf(".") + 1);
        }
        String lowerColumnName = columnName.toLowerCase();
        
        // 获取列信息
        TableStructure.ColumnInfo columnInfo = columnMap.get(lowerColumnName);
        if (columnInfo == null && paramName.contains(".")) {
            String baseColumnName = paramName.substring(paramName.lastIndexOf(".") + 1);
            columnInfo = columnMap.get(baseColumnName.toLowerCase());
        }
        
        // 获取扩展样本数据
        ColumnSampleData columnSamples = extendedSampleData.get(lowerColumnName);
        if (columnSamples == null && paramName.contains(".")) {
            columnSamples = extendedSampleData.get(columnName.toLowerCase());
        }
        
        // 根据参数名和SQL上下文智能选择值
        Object value = selectValueForParameter(paramName, columnName, columnInfo, columnSamples, sampleData, sql);
        
        // 如果还是找不到，使用默认值
        if (value == null) {
            if (columnInfo != null) {
                value = getDefaultValueByType(columnInfo.getDataType());
            } else {
                value = "1";
            }
        }

        // 根据数据类型格式化值
        return formatValue(value);
    }
    
    /**
     * 根据参数名和SQL上下文智能选择值
     */
    private Object selectValueForParameter(String paramName, String columnName, 
                                          TableStructure.ColumnInfo columnInfo,
                                          ColumnSampleData columnSamples,
                                          Map<String, Object> sampleData, String sql) {
        String lowerParamName = paramName.toLowerCase();
        String lowerColumnName = columnName.toLowerCase();
        
        // 1. 检查是否是范围查询参数
        RangeQueryType rangeType = detectRangeQueryType(paramName, columnName, sql);
        
        if (rangeType != RangeQueryType.NONE && columnSamples != null) {
            switch (rangeType) {
                case START:
                case MIN:
                    return columnSamples.getMinValue();
                case END:
                case MAX:
                    return columnSamples.getMaxValue();
                case BETWEEN_START:
                    // BETWEEN 的起始值，使用最小值或25分位数
                    if (columnSamples.getPercentile25() != null) {
                        return columnSamples.getPercentile25();
                    }
                    return columnSamples.getMinValue();
                case BETWEEN_END:
                    // BETWEEN 的结束值，使用最大值或75分位数
                    if (columnSamples.getPercentile75() != null) {
                        return columnSamples.getPercentile75();
                    }
                    return columnSamples.getMaxValue();
                default:
                    break;
            }
        }
        
        // 2. 尝试从扩展样本数据中获取随机样本
        if (columnSamples != null && !columnSamples.getRandomSamples().isEmpty()) {
            // 使用随机样本，增加多样性
            List<Object> randomSamples = columnSamples.getRandomSamples();
            return randomSamples.get(Math.abs(paramName.hashCode()) % randomSamples.size());
        }
        
        // 3. 尝试从基础样本数据中获取
        Object value = sampleData.get(lowerParamName);
        if (value == null) {
            value = sampleData.get(lowerColumnName);
        }
        
        // 4. 如果有中位数，优先使用中位数（更能代表数据分布）
        if (value == null && columnSamples != null && columnSamples.getMedian() != null) {
            return columnSamples.getMedian();
        }
        
        return value;
    }
    
    /**
     * 检测范围查询类型
     */
    private RangeQueryType detectRangeQueryType(String paramName, String columnName, String sql) {
        String lowerParamName = paramName.toLowerCase();
        String lowerColumnName = columnName.toLowerCase();
        String lowerSql = sql.toLowerCase();
        
        // 检查参数名模式
        if (lowerParamName.contains("start") || lowerParamName.contains("begin") || 
            lowerParamName.startsWith("min") || lowerParamName.endsWith("from")) {
            // 检查是否在 BETWEEN 子句中
            int paramIndex = lowerSql.indexOf("#{" + lowerParamName + "}");
            if (paramIndex > 0) {
                String beforeParam = lowerSql.substring(Math.max(0, paramIndex - 50), paramIndex);
                if (beforeParam.contains("between")) {
                    return RangeQueryType.BETWEEN_START;
                }
            }
            return RangeQueryType.START;
        }
        
        if (lowerParamName.contains("end") || lowerParamName.contains("finish") ||
            lowerParamName.startsWith("max") || lowerParamName.endsWith("to")) {
            // 检查是否在 BETWEEN 子句中
            int paramIndex = lowerSql.indexOf("#{" + lowerParamName + "}");
            if (paramIndex > 0) {
                String beforeParam = lowerSql.substring(Math.max(0, paramIndex - 50), paramIndex);
                if (beforeParam.contains("between")) {
                    return RangeQueryType.BETWEEN_END;
                }
            }
            return RangeQueryType.END;
        }
        
        // 检查SQL上下文中的操作符
        int paramIndex = lowerSql.indexOf("#{" + lowerParamName + "}");
        if (paramIndex > 0) {
            String context = lowerSql.substring(Math.max(0, paramIndex - 30), 
                                               Math.min(lowerSql.length(), paramIndex + 50));
            
            // 检查是否在 BETWEEN 子句中
            if (context.contains("between")) {
                // 判断是 BETWEEN 的第一个还是第二个参数
                String beforeBetween = lowerSql.substring(Math.max(0, paramIndex - 100), paramIndex);
                int betweenIndex = beforeBetween.lastIndexOf("between");
                if (betweenIndex >= 0) {
                    String betweenAndContext = lowerSql.substring(betweenIndex, 
                                                                 Math.min(lowerSql.length(), paramIndex + 50));
                    int andIndex = betweenAndContext.indexOf("and");
                    if (andIndex > 0 && paramIndex - betweenIndex < andIndex) {
                        return RangeQueryType.BETWEEN_START;
                    } else {
                        return RangeQueryType.BETWEEN_END;
                    }
                }
            }
            
            // 检查比较操作符
            if (context.contains(">=") || context.contains("> ")) {
                return RangeQueryType.MIN;
            }
            if (context.contains("<=") || context.contains("< ")) {
                return RangeQueryType.MAX;
            }
        }
        
        return RangeQueryType.NONE;
    }
    
    /**
     * 范围查询类型枚举
     */
    private enum RangeQueryType {
        NONE,       // 非范围查询
        START,      // 起始值（如 startId）
        END,        // 结束值（如 endId）
        MIN,        // 最小值（用于 >= 或 >）
        MAX,        // 最大值（用于 <= 或 <）
        BETWEEN_START,  // BETWEEN 的起始值
        BETWEEN_END     // BETWEEN 的结束值
    }
    
    /**
     * 列的样本数据（包含统计信息）
     */
    private static class ColumnSampleData {
        private Object minValue;
        private Object maxValue;
        private Object median;
        private Object average;
        private Object percentile25;
        private Object percentile75;
        private List<Object> randomSamples = new ArrayList<>();
        
        public Object getMinValue() { return minValue; }
        public void setMinValue(Object minValue) { this.minValue = minValue; }
        
        public Object getMaxValue() { return maxValue; }
        public void setMaxValue(Object maxValue) { this.maxValue = maxValue; }
        
        public Object getMedian() { return median; }
        public void setMedian(Object median) { this.median = median; }
        
        public Object getAverage() { return average; }
        public void setAverage(Object average) { this.average = average; }
        
        public Object getPercentile25() { return percentile25; }
        public void setPercentile25(Object percentile25) { this.percentile25 = percentile25; }
        
        public Object getPercentile75() { return percentile75; }
        public void setPercentile75(Object percentile75) { this.percentile75 = percentile75; }
        
        public List<Object> getRandomSamples() { return randomSamples; }
        public void setRandomSamples(List<Object> randomSamples) { this.randomSamples = randomSamples; }
    }

    /**
     * 根据数据类型获取默认值
     */
    private Object getDefaultValueByType(String dataType) {
        if (dataType == null) {
            return "1";
        }

        String type = dataType.toLowerCase();
        if (type.contains("int") || type.contains("bigint") || type.contains("smallint") || 
            type.contains("tinyint") || type.contains("mediumint")) {
            return 1;
        } else if (type.contains("decimal") || type.contains("numeric") || 
                   type.contains("float") || type.contains("double")) {
            return 1.0;
        } else if (type.contains("date") || type.contains("time")) {
            return "CURRENT_TIMESTAMP";
        } else if (type.contains("bool")) {
            return true;
        } else {
            // 字符串类型
            return "test";
        }
    }

    /**
     * 格式化值为SQL字符串
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        // 如果是数字类型，直接返回
        if (value instanceof Number) {
            return value.toString();
        }

        // 如果是布尔类型
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "1" : "0";
        }

        // 如果是特殊值（如CURRENT_TIMESTAMP），直接返回
        if (value instanceof String && 
            (value.toString().equals("CURRENT_TIMESTAMP") || 
             value.toString().startsWith("NOW()") ||
             value.toString().equals("CURRENT_DATE"))) {
            return value.toString();
        }

        // 字符串类型，需要加引号并转义
        String strValue = value.toString();
        // 转义单引号
        strValue = strValue.replace("'", "''");
        return "'" + strValue + "'";
    }

    /**
     * 获取表的样本数据（基础版本，保持向后兼容）
     */
    private Map<String, Object> getSampleData(String tableName, String datasourceName) {
        Map<String, Object> sampleData = new HashMap<>();
        
        try {
            JdbcTemplate jdbcTemplate = dataSourceManagerService.getJdbcTemplate(datasourceName);
            
            // 查询一条样本数据
            String sql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                // 将所有键转为小写，方便匹配
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    sampleData.put(entry.getKey().toLowerCase(), entry.getValue());
                }
                logger.debug("获取样本数据成功: table={}, data={}", tableName, sampleData);
            } else {
                logger.warn("表 {} 没有数据，将使用默认值", tableName);
            }
        } catch (Exception e) {
            logger.warn("获取样本数据失败: table={}, error={}", tableName, e.getMessage());
        }

        return sampleData;
    }
    
    /**
     * 获取扩展样本数据（包含统计信息和多个样本）
     * 基于数据库数据分布提供更广泛的样本
     */
    private Map<String, ColumnSampleData> getExtendedSampleData(String tableName, String datasourceName,
                                                                Map<String, TableStructure.ColumnInfo> columnMap) {
        Map<String, ColumnSampleData> extendedData = new HashMap<>();
        
        try {
            JdbcTemplate jdbcTemplate = dataSourceManagerService.getJdbcTemplate(datasourceName);
            
            // 对每个数值类型的列获取统计信息
            for (Map.Entry<String, TableStructure.ColumnInfo> entry : columnMap.entrySet()) {
                String columnName = entry.getKey();
                TableStructure.ColumnInfo columnInfo = entry.getValue();
                String dataType = columnInfo.getDataType().toLowerCase();
                
                ColumnSampleData sampleData = new ColumnSampleData();
                
                // 判断是否是数值类型
                boolean isNumeric = dataType.contains("int") || dataType.contains("bigint") || 
                                 dataType.contains("smallint") || dataType.contains("tinyint") ||
                                 dataType.contains("mediumint") || dataType.contains("decimal") ||
                                 dataType.contains("numeric") || dataType.contains("float") ||
                                 dataType.contains("double") || dataType.contains("real");
                
                // 判断是否是日期时间类型
                boolean isDateTime = dataType.contains("date") || dataType.contains("time") ||
                                   dataType.contains("timestamp") || dataType.contains("year");
                
                try {
                    if (isNumeric) {
                        // 获取数值列的统计信息
                        String statsSql = String.format(
                            "SELECT " +
                            "MIN(`%s`) as min_val, " +
                            "MAX(`%s`) as max_val, " +
                            "AVG(`%s`) as avg_val, " +
                            "COUNT(*) as cnt " +
                            "FROM `%s`",
                            columnName, columnName, columnName, tableName);
                        
                        List<Map<String, Object>> statsResults = jdbcTemplate.queryForList(statsSql);
                        if (!statsResults.isEmpty()) {
                            Map<String, Object> stats = statsResults.get(0);
                            sampleData.setMinValue(stats.get("min_val"));
                            sampleData.setMaxValue(stats.get("max_val"));
                            sampleData.setAverage(stats.get("avg_val"));
                            
                            Long count = ((Number) stats.get("cnt")).longValue();
                            
                            // 如果数据量足够，获取中位数和分位数
                            if (count > 0) {
                                // 获取中位数（50分位数）
                                long medianOffset = count / 2;
                                if (medianOffset > 0) {
                                    String medianSql = String.format(
                                        "SELECT `%s` as median_val FROM `%s` ORDER BY `%s` LIMIT 1 OFFSET %d",
                                        columnName, tableName, columnName, medianOffset);
                                    try {
                                        List<Map<String, Object>> medianResults = jdbcTemplate.queryForList(medianSql);
                                        if (!medianResults.isEmpty()) {
                                            sampleData.setMedian(medianResults.get(0).get("median_val"));
                                        }
                                    } catch (Exception e) {
                                        logger.debug("获取中位数失败: column={}, error={}", columnName, e.getMessage());
                                    }
                                }
                                
                                // 获取25分位数
                                if (count >= 4) {
                                    long p25Offset = count / 4;
                                    String p25Sql = String.format(
                                        "SELECT `%s` as p25_val FROM `%s` ORDER BY `%s` LIMIT 1 OFFSET %d",
                                        columnName, tableName, columnName, p25Offset);
                                    try {
                                        List<Map<String, Object>> p25Results = jdbcTemplate.queryForList(p25Sql);
                                        if (!p25Results.isEmpty()) {
                                            sampleData.setPercentile25(p25Results.get(0).get("p25_val"));
                                        }
                                    } catch (Exception e) {
                                        logger.debug("获取25分位数失败: column={}, error={}", columnName, e.getMessage());
                                    }
                                }
                                
                                // 获取75分位数
                                if (count >= 4) {
                                    long p75Offset = (count * 3) / 4;
                                    String p75Sql = String.format(
                                        "SELECT `%s` as p75_val FROM `%s` ORDER BY `%s` LIMIT 1 OFFSET %d",
                                        columnName, tableName, columnName, p75Offset);
                                    try {
                                        List<Map<String, Object>> p75Results = jdbcTemplate.queryForList(p75Sql);
                                        if (!p75Results.isEmpty()) {
                                            sampleData.setPercentile75(p75Results.get(0).get("p75_val"));
                                        }
                                    } catch (Exception e) {
                                        logger.debug("获取75分位数失败: column={}, error={}", columnName, e.getMessage());
                                    }
                                }
                            }
                            
                            // 获取多个随机样本（最多10个）
                            int sampleCount = Math.min(10, count.intValue());
                            if (sampleCount > 0) {
                                String randomSql = String.format(
                                    "SELECT `%s` as sample_val FROM `%s` ORDER BY RAND() LIMIT %d",
                                    columnName, tableName, sampleCount);
                                List<Map<String, Object>> randomResults = jdbcTemplate.queryForList(randomSql);
                                List<Object> randomSamples = new ArrayList<>();
                                for (Map<String, Object> row : randomResults) {
                                    randomSamples.add(row.get("sample_val"));
                                }
                                sampleData.setRandomSamples(randomSamples);
                            }
                        }
                    } else if (isDateTime) {
                        // 日期时间类型：获取最小值和最大值
                        String statsSql = String.format(
                            "SELECT " +
                            "MIN(`%s`) as min_val, " +
                            "MAX(`%s`) as max_val, " +
                            "COUNT(*) as cnt " +
                            "FROM `%s`",
                            columnName, columnName, tableName);
                        
                        List<Map<String, Object>> statsResults = jdbcTemplate.queryForList(statsSql);
                        if (!statsResults.isEmpty()) {
                            Map<String, Object> stats = statsResults.get(0);
                            sampleData.setMinValue(stats.get("min_val"));
                            sampleData.setMaxValue(stats.get("max_val"));
                            
                            Long count = ((Number) stats.get("cnt")).longValue();
                            // 获取随机样本
                            int sampleCount = Math.min(10, count.intValue());
                            if (sampleCount > 0) {
                                String randomSql = String.format(
                                    "SELECT `%s` as sample_val FROM `%s` ORDER BY RAND() LIMIT %d",
                                    columnName, tableName, sampleCount);
                                List<Map<String, Object>> randomResults = jdbcTemplate.queryForList(randomSql);
                                List<Object> randomSamples = new ArrayList<>();
                                for (Map<String, Object> row : randomResults) {
                                    randomSamples.add(row.get("sample_val"));
                                }
                                sampleData.setRandomSamples(randomSamples);
                            }
                        }
                    } else {
                        // 字符串类型：获取多个不同的随机样本
                        String countSql = String.format("SELECT COUNT(DISTINCT `%s`) as distinct_cnt FROM `%s`", 
                                                        columnName, tableName);
                        List<Map<String, Object>> countResults = jdbcTemplate.queryForList(countSql);
                        if (!countResults.isEmpty()) {
                            Long distinctCount = ((Number) countResults.get(0).get("distinct_cnt")).longValue();
                            int sampleCount = Math.min(10, distinctCount.intValue());
                            if (sampleCount > 0) {
                                String randomSql = String.format(
                                    "SELECT DISTINCT `%s` as sample_val FROM `%s` ORDER BY RAND() LIMIT %d",
                                    columnName, tableName, sampleCount);
                                List<Map<String, Object>> randomResults = jdbcTemplate.queryForList(randomSql);
                                List<Object> randomSamples = new ArrayList<>();
                                for (Map<String, Object> row : randomResults) {
                                    randomSamples.add(row.get("sample_val"));
                                }
                                sampleData.setRandomSamples(randomSamples);
                                
                                // 对于字符串，也可以获取最小值和最大值（按字典序）
                                String minMaxSql = String.format(
                                    "SELECT MIN(`%s`) as min_val, MAX(`%s`) as max_val FROM `%s`",
                                    columnName, columnName, tableName);
                                List<Map<String, Object>> minMaxResults = jdbcTemplate.queryForList(minMaxSql);
                                if (!minMaxResults.isEmpty()) {
                                    sampleData.setMinValue(minMaxResults.get(0).get("min_val"));
                                    sampleData.setMaxValue(minMaxResults.get(0).get("max_val"));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 如果获取统计信息失败，记录日志但继续处理其他列
                    logger.debug("获取列 {} 的统计信息失败: {}", columnName, e.getMessage());
                }
                
                // 如果获取到了任何数据，添加到结果中
                if (sampleData.getMinValue() != null || sampleData.getMaxValue() != null ||
                    sampleData.getMedian() != null || !sampleData.getRandomSamples().isEmpty()) {
                    extendedData.put(columnName, sampleData);
                }
            }
            
            logger.debug("获取扩展样本数据完成: table={}, columns={}", tableName, extendedData.keySet());
            
        } catch (Exception e) {
            logger.warn("获取扩展样本数据失败: table={}, error={}", tableName, e.getMessage());
        }
        
        return extendedData;
    }

    /**
     * 从SQL中提取所有占位符参数名
     */
    public Set<String> extractParameterNames(String sql) {
        Set<String> paramNames = new HashSet<>();
        if (sql == null || sql.trim().isEmpty()) {
            return paramNames;
        }

        Matcher matcher = PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            String paramName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            paramNames.add(paramName);
        }

        return paramNames;
    }

    /**
     * 智能替换：自动识别SQL中涉及的所有表，并替换参数
     */
    public String replaceParametersSmart(String sql, String datasourceName) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        try {
            // 提取SQL中涉及的表
            List<String> tableNames = sqlExecutionPlanService.parseTableNames(sql);
            
            if (tableNames.isEmpty()) {
                logger.warn("无法从SQL中提取表名: {}", sql);
                return sql;
            }

            // 使用第一个表作为主表来获取样本数据
            String primaryTable = tableNames.get(0);
            return replaceParameters(sql, primaryTable, datasourceName);

        } catch (Exception e) {
            logger.error("智能替换SQL参数失败", e);
            return sql;
        }
    }
}



