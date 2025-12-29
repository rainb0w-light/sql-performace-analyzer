package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.dto.ColumnStatisticsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于数据分布的SQL填充服务
 * 直接从MySQL的information_schema.COLUMN_STATISTICS读取统计信息，针对性地填充SQL参数，生成不同场景的SQL
 */
@Service
public class DistributionBasedSqlFillerService {

    private static final Logger logger = LoggerFactory.getLogger(DistributionBasedSqlFillerService.class);

    @Autowired
    private ColumnStatisticsParserService parserService;

    @Autowired
    private SqlExecutionPlanService sqlExecutionPlanService;
    
    @Autowired
    private DataSourceManagerService dataSourceManagerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 匹配MyBatis占位符的正则表达式
    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{([^}]+)\\}|\\$\\{([^}]+)\\}");

    /**
     * 生成多个不同场景的SQL（基于数据分布）
     * 
     * @param sql 原始SQL（包含占位符）
     * @param datasourceName 数据源名称
     * @return 不同场景的SQL列表（包含场景描述和填充后的SQL）
     */
    public List<SqlScenario> generateSqlScenarios(String sql, String datasourceName) {
        logger.info("生成SQL场景: sql={}, datasource={}", sql, datasourceName);

        List<SqlScenario> scenarios = new ArrayList<>();

        try {
            // 提取SQL中的表名和参数
            List<String> tableNames = sqlExecutionPlanService.parseTableNames(sql);
            Set<String> paramNames = extractParameterNames(sql);

            if (tableNames.isEmpty()) {
                logger.warn("无法从SQL中提取表名");
                return scenarios;
            }

            // 为每个表从MySQL读取列统计信息
            String databaseName = extractDatabaseName(datasourceName);
            Map<String, Map<String, ColumnStatisticsDTO>> tableColumnStats = new HashMap<>();
            for (String tableName : tableNames) {
                List<ColumnStatisticsDTO> stats = parserService.getStatisticsFromMysql(
                    datasourceName, databaseName, tableName);
                
                Map<String, ColumnStatisticsDTO> columnMap = new HashMap<>();
                for (ColumnStatisticsDTO dto : stats) {
                    columnMap.put(dto.getColumnName().toLowerCase(), dto);
                }
                tableColumnStats.put(tableName.toLowerCase(), columnMap);
            }

            // 生成不同场景
            scenarios.addAll(generateScenarios(sql, paramNames, tableColumnStats));

            logger.info("生成了 {} 个SQL场景", scenarios.size());
            return scenarios;

        } catch (Exception e) {
            logger.error("生成SQL场景失败", e);
            return scenarios;
        }
    }

    /**
     * 生成多个场景
     */
    private List<SqlScenario> generateScenarios(String sql, Set<String> paramNames,
                                                Map<String, Map<String, ColumnStatisticsDTO>> tableColumnStats) {
        List<SqlScenario> scenarios = new ArrayList<>();

        // 场景1：最小值场景
        scenarios.add(createScenario(sql, paramNames, tableColumnStats, "最小值场景", 
            (dto) -> dto.getMinValue()));

        // 场景2：最大值场景
        scenarios.add(createScenario(sql, paramNames, tableColumnStats, "最大值场景", 
            (dto) -> dto.getMaxValue()));

        // 场景3：中位数场景（从采样值中取中间值）
        scenarios.add(createScenario(sql, paramNames, tableColumnStats, "中位数场景", 
            (dto) -> {
                List<Object> samples = parserService.getSampleValues(dto);
                if (!samples.isEmpty()) {
                    return samples.get(samples.size() / 2).toString();
                }
                return null;
            }));

        // 场景4-6：25分位数、50分位数、75分位数场景
        scenarios.add(createPercentileScenario(sql, paramNames, tableColumnStats, "25分位数场景", 0.25));
        scenarios.add(createPercentileScenario(sql, paramNames, tableColumnStats, "50分位数场景", 0.50));
        scenarios.add(createPercentileScenario(sql, paramNames, tableColumnStats, "75分位数场景", 0.75));

        // 场景7-10：随机采样场景（使用不同的随机样本）
        for (int i = 0; i < 4; i++) {
            final int index = i;
            scenarios.add(createScenario(sql, paramNames, tableColumnStats, 
                "随机采样场景" + (i + 1), 
                (dto) -> {
                    List<Object> samples = parserService.getSampleValues(dto);
                    if (!samples.isEmpty()) {
                        int sampleIndex = (index * samples.size()) / 4;
                        if (sampleIndex >= samples.size()) {
                            sampleIndex = samples.size() - 1;
                        }
                        return samples.get(sampleIndex).toString();
                    }
                    return null;
                }));
        }

        // 过滤掉无效的场景
        scenarios.removeIf(s -> s.getFilledSql() == null || s.getFilledSql().trim().isEmpty());

        return scenarios;
    }

    /**
     * 创建场景
     */
    private SqlScenario createScenario(String sql, Set<String> paramNames,
                                       Map<String, Map<String, ColumnStatisticsDTO>> tableColumnStats,
                                       String scenarioName,
                                       ValueExtractor extractor) {
        SqlScenario scenario = new SqlScenario();
        scenario.setScenarioName(scenarioName);
        scenario.setOriginalSql(sql);

        Map<String, Object> sampleValues = new HashMap<>();
        String filledSql = fillSqlWithStatistics(sql, paramNames, tableColumnStats, extractor, sampleValues);

        scenario.setFilledSql(filledSql);
        scenario.setSampleValues(sampleValues);

        return scenario;
    }

    /**
     * 创建分位数场景
     */
    private SqlScenario createPercentileScenario(String sql, Set<String> paramNames,
                                                  Map<String, Map<String, ColumnStatisticsDTO>> tableColumnStats,
                                                  String scenarioName, double percentile) {
        return createScenario(sql, paramNames, tableColumnStats, scenarioName, (dto) -> {
            List<Object> samples = parserService.getSampleValues(dto);
            if (!samples.isEmpty()) {
                int index = (int) (samples.size() * percentile);
                if (index >= samples.size()) {
                    index = samples.size() - 1;
                }
                return samples.get(index).toString();
            }
            return null;
        });
    }

    /**
     * 使用统计信息填充SQL
     */
    private String fillSqlWithStatistics(String sql, Set<String> paramNames,
                                         Map<String, Map<String, ColumnStatisticsDTO>> tableColumnStats,
                                         ValueExtractor extractor,
                                         Map<String, Object> sampleValues) {
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String paramName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String replacement = getReplacementFromStatistics(paramName, tableColumnStats, extractor, sampleValues);
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 从统计信息中获取替换值
     */
    private String getReplacementFromStatistics(String paramName,
                                                 Map<String, Map<String, ColumnStatisticsDTO>> tableColumnStats,
                                                 ValueExtractor extractor,
                                                 Map<String, Object> sampleValues) {
        // 提取列名（去掉可能的表别名前缀）
        String columnName = paramName;
        if (paramName.contains(".")) {
            columnName = paramName.substring(paramName.lastIndexOf(".") + 1);
        }
        String lowerColumnName = columnName.toLowerCase();

        // 在所有表的列统计信息中查找
        ColumnStatisticsDTO dto = null;
        for (Map<String, ColumnStatisticsDTO> columnMap : tableColumnStats.values()) {
            dto = columnMap.get(lowerColumnName);
            if (dto != null) {
                break;
            }
        }

        if (dto == null) {
            // 如果找不到统计信息，使用默认值
            String defaultValue = "1";
            sampleValues.put(paramName, defaultValue);
            return defaultValue;
        }

        // 使用提取器获取值
        String value = extractor.extract(dto);
        if (value == null) {
            value = "1"; // 默认值
        }

        sampleValues.put(paramName, value);
        return formatValue(value);
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
     * 格式化值为SQL字符串
     */
    private String formatValue(String value) {
        if (value == null || value.equals("NULL")) {
            return "NULL";
        }

        // 尝试解析为数字
        try {
            Double.parseDouble(value);
            return value; // 数字类型，直接返回
        } catch (NumberFormatException e) {
            // 字符串类型，需要加引号并转义
            String strValue = value.replace("'", "''");
            return "'" + strValue + "'";
        }
    }

    /**
     * 提取SQL中的参数名
     */
    private Set<String> extractParameterNames(String sql) {
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
     * 值提取器接口
     */
    @FunctionalInterface
    private interface ValueExtractor {
        String extract(ColumnStatisticsDTO dto);
    }

    /**
     * SQL场景数据类
     */
    public static class SqlScenario {
        private String scenarioName;
        private String originalSql;
        private String filledSql;
        private Map<String, Object> sampleValues;

        public String getScenarioName() {
            return scenarioName;
        }

        public void setScenarioName(String scenarioName) {
            this.scenarioName = scenarioName;
        }

        public String getOriginalSql() {
            return originalSql;
        }

        public void setOriginalSql(String originalSql) {
            this.originalSql = originalSql;
        }

        public String getFilledSql() {
            return filledSql;
        }

        public void setFilledSql(String filledSql) {
            this.filledSql = filledSql;
        }

        public Map<String, Object> getSampleValues() {
            return sampleValues;
        }

        public void setSampleValues(Map<String, Object> sampleValues) {
            this.sampleValues = sampleValues;
        }
    }
}
