package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.TableStructure;
import com.biz.sccba.sqlanalyzer.model.dto.ColumnStatisticsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL参数替换服务
 */
@Service
public class SqlParameterReplacerService {

    private static final Logger logger = LoggerFactory.getLogger(SqlParameterReplacerService.class);

    @Autowired
    private SqlExecutionPlanService sqlExecutionPlanService;

    @Autowired
    private ColumnStatisticsParserService columnStatisticsParserService;
    
    @Autowired
    private DataSourceManagerService dataSourceManagerService;


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
            // 只支持?占位符
            if (!sql.contains("?")) {
                // 没有占位符，直接返回
                return sql;
            }
            
            // 提取SQL中实际使用的列名
            Set<String> columnNames = extractColumnNamesFromQuestionMarks(sql, tableName);

            if (columnNames.isEmpty()) {
                logger.warn("无法从SQL中提取列名: {}", sql);
                return sql;
            }
            Map<String, TableStructure.ColumnInfo> columnMap = new HashMap<>();

            try {
                // 构建只查询指定列的SQL
                String testSql = "SELECT * FROM " + tableName + " LIMIT 1";

                List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(testSql, datasourceName);

                if (!structures.isEmpty()) {
                    TableStructure tableStructure = structures.get(0);
                    if (tableStructure.getColumns() != null) {
                        for (TableStructure.ColumnInfo col : tableStructure.getColumns() ) {
                            if(columnNames.contains(col.getColumnName())){
                                columnMap.put(col.getColumnName().toLowerCase(), col);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("获取指定列的表结构失败: {}", e.getMessage());
            }

            // 只获取这些列的表结构信息

            // 替换?占位符
            String replacedSql = replaceQuestionMarkPlaceholders(sql, columnMap, tableName, datasourceName);

            logger.debug("SQL参数替换: {} -> {}", sql, replacedSql);
            return replacedSql;

        } catch (Exception e) {
            logger.error("替换SQL参数失败", e);
            // 如果替换失败，返回原SQL（可能导致执行计划获取失败，但不会影响其他功能）
            return sql;
        }
    }

    /**
     * 替换?占位符
     */
    private String replaceQuestionMarkPlaceholders(String sql, Map<String, TableStructure.ColumnInfo> columnMap,
                                                   String tableName, String datasourceName) {
        // 获取扩展样本数据（包含统计信息）
        Map<String, ColumnSampleData> extendedSampleData = getExtendedSampleData(tableName, datasourceName, columnMap);
        
        StringBuffer result = new StringBuffer(sql);
        
        // 1. 处理 BETWEEN ? AND ? 的情况（先处理，因为包含两个占位符）
        Pattern betweenPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s+(?:BETWEEN|between)\\s+\\?\\s+(?:AND|and)\\s+\\?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher betweenMatcher = betweenPattern.matcher(result);
        while (betweenMatcher.find()) {
            String fullColumnName = betweenMatcher.group(1);
            if (fullColumnName != null) {
                // 提取列名（去掉表别名前缀）
                String columnName = fullColumnName;
                if (columnName.contains(".")) {
                    columnName = columnName.substring(columnName.lastIndexOf(".") + 1);
                }
                String lowerColumnName = columnName.toLowerCase();
                
                // 获取列信息和样本数据
                TableStructure.ColumnInfo columnInfo = columnMap.get(lowerColumnName);
                ColumnSampleData columnSamples = extendedSampleData.get(lowerColumnName);
                
                // 选择起始值和结束值
                Object startValue = selectValueForQuestionMark(lowerColumnName, columnInfo, columnSamples, "BETWEEN_START");
                Object endValue = selectValueForQuestionMark(lowerColumnName, columnInfo, columnSamples, "BETWEEN_END");
                
                // 如果找不到，使用默认值
                if (startValue == null) {
                    startValue = columnInfo != null ? getDefaultValueByType(columnInfo.getDataType()) : "1";
                }
                if (endValue == null) {
                    endValue = columnInfo != null ? getDefaultValueByType(columnInfo.getDataType()) : "1";
                }
                
                // 格式化值
                String startReplacement = formatValue(startValue);
                String endReplacement = formatValue(endValue);
                
                // 构建替换字符串
                String replacement = fullColumnName + " BETWEEN " + startReplacement + " AND " + endReplacement;
                result.replace(betweenMatcher.start(), betweenMatcher.end(), replacement);
                
                // 重置匹配器，因为字符串已改变
                betweenMatcher = betweenPattern.matcher(result);
            }
        }
        
        // 2. 处理 IN (?, ?, ?) 的情况
        Pattern inPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s+(?:IN|in)\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher inMatcher = inPattern.matcher(result);
        while (inMatcher.find()) {
            String fullColumnName = inMatcher.group(1);
            String inClause = inMatcher.group(2);
            if (fullColumnName != null && inClause != null && inClause.contains("?")) {
                // 提取列名（去掉表别名前缀）
                String columnName = fullColumnName;
                if (columnName.contains(".")) {
                    columnName = columnName.substring(columnName.lastIndexOf(".") + 1);
                }
                String lowerColumnName = columnName.toLowerCase();
                
                // 获取列信息和样本数据
                TableStructure.ColumnInfo columnInfo = columnMap.get(lowerColumnName);
                ColumnSampleData columnSamples = extendedSampleData.get(lowerColumnName);
                
                // 为每个占位符选择值
                StringBuilder newInClause = new StringBuilder();
                int lastIndex = 0;
                for (int i = 0; i < inClause.length(); i++) {
                    if (inClause.charAt(i) == '?') {
                        newInClause.append(inClause.substring(lastIndex, i));
                        
                        // 选择值
                        Object value = selectValueForQuestionMark(lowerColumnName, columnInfo, columnSamples, "IN");
                        if (value == null) {
                            value = columnInfo != null ? getDefaultValueByType(columnInfo.getDataType()) : "1";
                        }
                        newInClause.append(formatValue(value));
                        
                        lastIndex = i + 1;
                    }
                }
                newInClause.append(inClause.substring(lastIndex));
                
                // 替换整个IN子句（保留原始列名，可能包含表别名）
                String replacement = fullColumnName + " IN (" + newInClause.toString() + ")";
                result.replace(inMatcher.start(), inMatcher.end(), replacement);
                
                // 重置匹配器
                inMatcher = inPattern.matcher(result);
            }
        }
        
        // 3. 处理列名 LIKE ? 的情况
        Pattern likePattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s+(?:LIKE|like)\\s+\\?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher likeMatcher = likePattern.matcher(result);
        while (likeMatcher.find()) {
            String fullColumnName = likeMatcher.group(1);
            if (fullColumnName != null) {
                // 提取列名（去掉表别名前缀）
                String columnName = fullColumnName;
                if (columnName.contains(".")) {
                    columnName = columnName.substring(columnName.lastIndexOf(".") + 1);
                }
                String lowerColumnName = columnName.toLowerCase();
                
                // 获取列信息和样本数据
                TableStructure.ColumnInfo columnInfo = columnMap.get(lowerColumnName);
                ColumnSampleData columnSamples = extendedSampleData.get(lowerColumnName);
                
                // 选择值（对于LIKE，通常使用字符串值）
                Object value = selectValueForQuestionMark(lowerColumnName, columnInfo, columnSamples, "LIKE");
                if (value == null) {
                    // 如果没有找到，使用默认字符串值
                    value = columnInfo != null ? getDefaultValueByType(columnInfo.getDataType()) : "test";
                }
                
                // 对于LIKE，如果值是字符串，添加通配符（如果还没有）
                String likeValue = formatValueForLike(value);
                
                // 替换占位符
                result.replace(likeMatcher.end() - 1, likeMatcher.end(), likeValue);
                
                // 重置匹配器
                likeMatcher = likePattern.matcher(result);
            }
        }
        
        // 4. 处理函数调用中的占位符，如 substr(1,3,?), concat(?, %), A = substr(1,3,?), A LIKE concat(?, %)
        // 匹配模式：列名 操作符 函数名(参数包含?)
        Pattern functionPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s*([=<>!]+|(?:LIKE|like))\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\?\\)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher functionMatcher = functionPattern.matcher(result);
        while (functionMatcher.find()) {
            String fullColumnName = functionMatcher.group(1);
            String functionName = functionMatcher.group(3);
            String beforeQuestion = functionMatcher.group(4);
            
            if (fullColumnName != null && functionName != null) {
                // 提取列名（去掉表别名前缀）
                String columnName = fullColumnName;
                if (columnName.contains(".")) {
                    columnName = columnName.substring(columnName.lastIndexOf(".") + 1);
                }
                String lowerColumnName = columnName.toLowerCase();
                
                // 获取列信息和样本数据
                TableStructure.ColumnInfo columnInfo = columnMap.get(lowerColumnName);
                ColumnSampleData columnSamples = extendedSampleData.get(lowerColumnName);
                
                // 选择值
                Object value = selectValueForQuestionMark(lowerColumnName, columnInfo, columnSamples, "FUNCTION");
                if (value == null) {
                    value = columnInfo != null ? getDefaultValueByType(columnInfo.getDataType()) : "1";
                }
                
                // 格式化值
                String formattedValue = formatValue(value);
                
                // 构建替换后的函数调用
                String newFunctionCall = functionName + "(" + 
                    (beforeQuestion != null ? beforeQuestion : "") + 
                    formattedValue + ")";
                
                // 替换整个函数调用部分（从函数名开始到结束）
                int startPos = result.indexOf(functionName + "(", functionMatcher.start());
                if (startPos >= 0) {
                    // 找到匹配的右括号
                    int parenCount = 0;
                    int endPos = startPos;
                    for (int i = startPos; i < result.length(); i++) {
                        char c = result.charAt(i);
                        if (c == '(') parenCount++;
                        if (c == ')') {
                            parenCount--;
                            if (parenCount == 0) {
                                endPos = i + 1;
                                break;
                            }
                        }
                    }
                    if (endPos > startPos) {
                        // 替换函数调用
                        result.replace(startPos, endPos, newFunctionCall);
                        // 重置匹配器
                        functionMatcher = functionPattern.matcher(result);
                    }
                }
            }
        }
        
        // 4.1 处理函数调用中 ? 后面还有参数的情况，如 concat(?, %)
        Pattern functionPattern2 = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s*([=<>!]+|(?:LIKE|like))\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\?([^)]*)\\)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher functionMatcher2 = functionPattern2.matcher(result);
        while (functionMatcher2.find()) {
            String fullColumnName = functionMatcher2.group(1);
            String functionName = functionMatcher2.group(3);
            String beforeQuestion = functionMatcher2.group(4);
            String afterQuestion = functionMatcher2.group(5);
            
            if (fullColumnName != null && functionName != null && afterQuestion != null && !afterQuestion.trim().isEmpty()) {
                // 提取列名（去掉表别名前缀）
                String columnName = fullColumnName;
                if (columnName.contains(".")) {
                    columnName = columnName.substring(columnName.lastIndexOf(".") + 1);
                }
                String lowerColumnName = columnName.toLowerCase();
                
                // 获取列信息和样本数据
                TableStructure.ColumnInfo columnInfo = columnMap.get(lowerColumnName);
                ColumnSampleData columnSamples = extendedSampleData.get(lowerColumnName);
                
                // 选择值
                Object value = selectValueForQuestionMark(lowerColumnName, columnInfo, columnSamples, "FUNCTION");
                if (value == null) {
                    value = columnInfo != null ? getDefaultValueByType(columnInfo.getDataType()) : "1";
                }
                
                // 格式化值
                String formattedValue = formatValue(value);
                
                // 构建替换后的函数调用
                String newFunctionCall = functionName + "(" + 
                    (beforeQuestion != null ? beforeQuestion : "") + 
                    formattedValue + 
                    afterQuestion + ")";
                
                // 替换整个函数调用部分（从函数名开始到结束）
                int startPos = result.indexOf(functionName + "(", functionMatcher2.start());
                if (startPos >= 0) {
                    // 找到匹配的右括号
                    int parenCount = 0;
                    int endPos = startPos;
                    for (int i = startPos; i < result.length(); i++) {
                        char c = result.charAt(i);
                        if (c == '(') parenCount++;
                        if (c == ')') {
                            parenCount--;
                            if (parenCount == 0) {
                                endPos = i + 1;
                                break;
                            }
                        }
                    }
                    if (endPos > startPos) {
                        // 替换函数调用
                        result.replace(startPos, endPos, newFunctionCall);
                        // 重置匹配器
                        functionMatcher2 = functionPattern2.matcher(result);
                    }
                }
            }
        }
        
        // 5. 处理列名 = ?, > ?, < ?, >= ?, <= ?, != ? 等情况
        Pattern comparisonPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s*([=<>!]+)\\s*\\?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher comparisonMatcher = comparisonPattern.matcher(result);
        while (comparisonMatcher.find()) {
            String fullColumnName = comparisonMatcher.group(1);
            String operator = comparisonMatcher.group(2);
            if (fullColumnName != null && operator != null) {
                // 提取列名（去掉表别名前缀）
                String columnName = fullColumnName;
                if (columnName.contains(".")) {
                    columnName = columnName.substring(columnName.lastIndexOf(".") + 1);
                }
                String lowerColumnName = columnName.toLowerCase();
                
                // 获取列信息和样本数据
                TableStructure.ColumnInfo columnInfo = columnMap.get(lowerColumnName);
                ColumnSampleData columnSamples = extendedSampleData.get(lowerColumnName);
                
                // 选择值
                Object value = selectValueForQuestionMark(lowerColumnName, columnInfo, columnSamples, operator);
                if (value == null) {
                    value = columnInfo != null ? getDefaultValueByType(columnInfo.getDataType()) : "1";
                }
                
                // 格式化值并替换（保留原始列名，可能包含表别名）
                String replacement = formatValue(value);
                result.replace(comparisonMatcher.end() - 1, comparisonMatcher.end(), replacement);
                
                // 重置匹配器
                comparisonMatcher = comparisonPattern.matcher(result);
            }
        }
        
        return result.toString();
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
     * 格式化值为LIKE操作符使用的字符串（添加通配符）
     */
    private String formatValueForLike(Object value) {
        if (value == null) {
            return "'%'";
        }

        // 转换为字符串
        String strValue = value.toString();
        
        // 如果已经是特殊值，直接返回
        if (strValue.equals("CURRENT_TIMESTAMP") || 
            strValue.startsWith("NOW()") ||
            strValue.equals("CURRENT_DATE")) {
            return strValue;
        }
        
        // 如果已经包含通配符，直接格式化
        if (strValue.contains("%") || strValue.contains("_")) {
            strValue = strValue.replace("'", "''");
            return "'" + strValue + "'";
        }
        
        // 否则添加通配符（前后都加，实现模糊匹配）
        strValue = strValue.replace("'", "''");
        return "'%" + strValue + "%'";
    }

    /**
     * 获取扩展样本数据（包含统计信息和多个样本）
     * 直接从MySQL的information_schema.COLUMN_STATISTICS读取统计信息
     */
    private Map<String, ColumnSampleData> getExtendedSampleData(String tableName, String datasourceName,
                                                                Map<String, TableStructure.ColumnInfo> columnMap) {
        Map<String, ColumnSampleData> extendedData = new HashMap<>();
        
        try {
            String databaseName = extractDatabaseName(datasourceName);
            
            // 从MySQL直接读取列统计信息
            List<ColumnStatisticsDTO> statisticsList = columnStatisticsParserService
                .getStatisticsFromMysql(datasourceName, databaseName, tableName);
            
            // 将统计信息转换为Map，方便查找
            Map<String, ColumnStatisticsDTO> statisticsMap = new HashMap<>();
            for (ColumnStatisticsDTO dto : statisticsList) {
                statisticsMap.put(dto.getColumnName().toLowerCase(), dto);
            }

            
            // 对每个列，从统计信息中构建ColumnSampleData
            for (Map.Entry<String, TableStructure.ColumnInfo> entry : columnMap.entrySet()) {
                String columnName = entry.getKey();
                ColumnStatisticsDTO dto = statisticsMap.get(columnName);
                
                if (dto == null) {
                    // 如果没有统计信息，跳过
                    logger.debug("列 {} 没有统计信息，跳过", columnName);
                    continue;
                }
                
                ColumnSampleData sampleData = new ColumnSampleData();
                
                // 从DTO中提取数据
                // 最小值和最大值（需要转换为Object类型）
                if (dto.getMinValue() != null) {
                    sampleData.setMinValue(parseValue(dto.getMinValue()));
                }
                if (dto.getMaxValue() != null) {
                    sampleData.setMaxValue(parseValue(dto.getMaxValue()));
                }
                
                // 从采样值中获取随机样本
                List<Object> sampleValues = columnStatisticsParserService.getSampleValues(dto);
                if (!sampleValues.isEmpty()) {
                    sampleData.setRandomSamples(sampleValues);
                    
                    // 如果有采样值，可以计算中位数和分位数（如果采样值足够多）
                    if (sampleValues.size() >= 2) {
                        // 简单处理：使用采样值的中位数作为中位数
                        List<Object> sortedSamples = new ArrayList<>(sampleValues);
                        sortedSamples.sort((a, b) -> compareValues(a, b));
                        
                        int midIndex = sortedSamples.size() / 2;
                        sampleData.setMedian(sortedSamples.get(midIndex));
                        
                        // 25分位数
                        if (sortedSamples.size() >= 4) {
                            int p25Index = sortedSamples.size() / 4;
                            sampleData.setPercentile25(sortedSamples.get(p25Index));
                        }
                        
                        // 75分位数
                        if (sortedSamples.size() >= 4) {
                            int p75Index = (sortedSamples.size() * 3) / 4;
                            sampleData.setPercentile75(sortedSamples.get(p75Index));
                        }
                    }
                }
                
                // 如果获取到了任何数据，添加到结果中
                if (sampleData.getMinValue() != null || sampleData.getMaxValue() != null ||
                    sampleData.getMedian() != null || !sampleData.getRandomSamples().isEmpty()) {
                    extendedData.put(columnName, sampleData);
                }
            }
            
            logger.info("获取扩展样本数据完成: table={}, columns={}", tableName, extendedData.keySet());
            
        } catch (Exception e) {
            logger.warn("获取扩展样本数据失败: table={}, error={}", tableName, e.getMessage());
        }
        
        return extendedData;
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
     * 解析字符串值为Object类型
     */
    private Object parseValue(String valueStr) {
        if (valueStr == null || valueStr.trim().isEmpty()) {
            return null;
        }
        
        // 尝试解析为数字
        try {
            if (valueStr.contains(".")) {
                return Double.parseDouble(valueStr);
            } else {
                return Long.parseLong(valueStr);
            }
        } catch (NumberFormatException e) {
            // 不是数字，返回字符串
            return valueStr;
        }
    }
    
    /**
     * 比较两个值的大小（用于排序）
     */
    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        
        if (a instanceof Number && b instanceof Number) {
            double da = ((Number) a).doubleValue();
            double db = ((Number) b).doubleValue();
            return Double.compare(da, db);
        }
        
        return a.toString().compareTo(b.toString());
    }


    /**
     * 从SQL中提取?占位符前的列名
     * @param sql SQL语句
     * @param tableName 表名（用于处理表别名）
     * @return 列名集合
     */
    private Set<String> extractColumnNamesFromQuestionMarks(String sql, String tableName) {
        Set<String> columnNames = new HashSet<>();
        if (sql == null || sql.trim().isEmpty()) {
            return columnNames;
        }
        
        String lowerSql = sql.toLowerCase();
        
        // 使用正则表达式匹配列名 = ? 的模式
        // 匹配: column = ?, column >= ?, column <= ?, column != ?, column IN (?, ?, ?)
        Pattern pattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s*[=<>!]+\\s*\\?|" +
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s+(?:IN|in)\\s*\\([^)]*\\?",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String columnName = matcher.group(1);
            if (columnName == null) {
                columnName = matcher.group(2);
            }
            if (columnName != null) {
                // 去掉表别名前缀
                if (columnName.contains(".")) {
                    columnName = columnName.substring(columnName.lastIndexOf(".") + 1);
                }
                columnNames.add(columnName.toLowerCase());
            }
        }
        
        // 处理 BETWEEN ? AND ? 的情况
        Pattern betweenPattern = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s+(?:BETWEEN|between)\\s+\\?",
            Pattern.CASE_INSENSITIVE
        );
        Matcher betweenMatcher = betweenPattern.matcher(sql);
        while (betweenMatcher.find()) {
            String columnName = betweenMatcher.group(1);
            if (columnName != null) {
                // 去掉表别名前缀
                if (columnName.contains(".")) {
                    columnName = columnName.substring(columnName.lastIndexOf(".") + 1);
                }
                columnNames.add(columnName.toLowerCase());
            }
        }
        
        return columnNames;
    }
    
    /**
     * 为?占位符选择值
     */
    private Object selectValueForQuestionMark(String columnName, TableStructure.ColumnInfo columnInfo,
                                              ColumnSampleData columnSamples, String operator) {
        // 检查是否是范围查询
        RangeQueryType rangeType = RangeQueryType.NONE;
        
        if ("BETWEEN_START".equals(operator)) {
            rangeType = RangeQueryType.BETWEEN_START;
        } else if ("BETWEEN_END".equals(operator)) {
            rangeType = RangeQueryType.BETWEEN_END;
        } else if (operator != null) {
            if (operator.contains(">")) {
                rangeType = RangeQueryType.MIN;
            } else if (operator.contains("<")) {
                rangeType = RangeQueryType.MAX;
            }
        }
        
        if (rangeType != RangeQueryType.NONE && columnSamples != null) {
            switch (rangeType) {
                case MIN:
                    return columnSamples.getMinValue();
                case MAX:
                    return columnSamples.getMaxValue();
                case BETWEEN_START:
                    if (columnSamples.getPercentile25() != null) {
                        return columnSamples.getPercentile25();
                    }
                    return columnSamples.getMinValue();
                case BETWEEN_END:
                    if (columnSamples.getPercentile75() != null) {
                        return columnSamples.getPercentile75();
                    }
                    return columnSamples.getMaxValue();
                default:
                    break;
            }
        }
        
        // 对于LIKE操作符，优先使用字符串类型的样本值
        if ("LIKE".equals(operator) && columnSamples != null) {
            // 优先从随机样本中选择字符串值
            if (!columnSamples.getRandomSamples().isEmpty()) {
                for (Object sample : columnSamples.getRandomSamples()) {
                    if (sample instanceof String) {
                        return sample;
                    }
                }
                // 如果没有字符串样本，使用第一个样本并转换为字符串
                Object sample = columnSamples.getRandomSamples().get(0);
                return sample != null ? sample.toString() : "test";
            }
            // 如果有中位数，转换为字符串
            if (columnSamples.getMedian() != null) {
                return columnSamples.getMedian().toString();
            }
        }
        
        // 对于函数调用，使用合适的值类型
        if ("FUNCTION".equals(operator) && columnSamples != null) {
            // 优先使用随机样本
            if (!columnSamples.getRandomSamples().isEmpty()) {
                List<Object> randomSamples = columnSamples.getRandomSamples();
                return randomSamples.get(Math.abs(columnName.hashCode()) % randomSamples.size());
            }
            // 如果有中位数，使用中位数
            if (columnSamples.getMedian() != null) {
                return columnSamples.getMedian();
            }
        }
        
        // 尝试从扩展样本数据中获取随机样本
        if (columnSamples != null && !columnSamples.getRandomSamples().isEmpty()) {
            List<Object> randomSamples = columnSamples.getRandomSamples();
            return randomSamples.get(Math.abs(columnName.hashCode()) % randomSamples.size());
        }
        
        // 如果有中位数，使用中位数
        if (columnSamples != null && columnSamples.getMedian() != null) {
            return columnSamples.getMedian();
        }
        
        return null;
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



