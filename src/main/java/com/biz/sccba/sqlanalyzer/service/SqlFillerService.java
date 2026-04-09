package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.memory.BusinessSemanticsMemoryService;
import com.biz.sccba.sqlanalyzer.model.agent.BusinessSemantics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 填充服务
 *
 * 根据数据分布和业务语义，为 SQL 占位符生成测试参数值
 *
 * 填充策略:
 * - 日期类型：数据驱动，根据实际数据范围填充
 * - 模糊匹配：数据采样，从数据库中采样实际值
 * - 枚举类型：基于业务语义，从语义库中获取有效值
 * - 数值类型：根据统计信息生成合理值
 */
@Service
public class SqlFillerService {

    private final TestEnvironmentService testEnvironmentService;
    private final BusinessSemanticsMemoryService semanticsMemoryService;

    public SqlFillerService(TestEnvironmentService testEnvironmentService,
                           BusinessSemanticsMemoryService semanticsMemoryService) {
        this.testEnvironmentService = testEnvironmentService;
        this.semanticsMemoryService = semanticsMemoryService;
    }

    /**
     * 填充结果
     */
    public record FillResult(
        String originalSql,
        String filledSql,
        Map<String, Object> parameters,
        String description,
        List<String> warnings  // 填充过程中的警告信息
    ) {}

    /**
     * 字段统计信息
     */
    public record ColumnStats(
        String columnName,
        String dataType,
        Long nullCount,
        Long distinctCount,
        Object minValue,
        Object maxValue,
        List<Object> sampleValues  // 采样值
    ) {}

    /**
     * 日期范围统计
     */
    public record DateRangeStats(
        LocalDate minDate,
        LocalDate maxDate,
        Map<String, Long> distribution,  // 按月/日分布
        List<LocalDate> denseDates  // 数据密集的日期
    ) {}

    /**
     * 填充 SQL 参数
     *
     * @param datasourceName 数据源名称
     * @param sql 待填充的 SQL（占位符为 ?）
     * @param involvedTables 涉及的表名列表
     * @param scenarioName 场景名称（用于生成描述）
     * @return 填充结果
     */
    public FillResult fillSql(String datasourceName, String sql,
                               List<String> involvedTables, String scenarioName) {
        System.out.println("[SqlFillerService] 开始填充 SQL 参数，datasource=" + datasourceName + ", tables=" + involvedTables);

        List<String> warnings = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();

        try {
            JdbcTemplate jdbcTemplate = testEnvironmentService.getJdbcTemplate(datasourceName);

            // 1. 收集所有涉及表的列统计信息
            Map<String, ColumnStats> columnStatsMap = new HashMap<>();
            for (String tableName : involvedTables) {
                Map<String, ColumnStats> stats = collectColumnStats(jdbcTemplate, tableName);
                columnStatsMap.putAll(stats);
            }

            // 2. 获取业务语义信息
            Map<String, BusinessSemantics> semanticsMap = new HashMap<>();
            for (String tableName : involvedTables) {
                var semantics = semanticsMemoryService.getSemantics(tableName);
                if (semantics != null) {
                    semanticsMap.put(tableName, semantics);
                }
            }

            // 3. 分析 SQL 中的占位符，生成参数值
            List<Object> paramValues = generateParamValues(sql, columnStatsMap, semanticsMap, warnings);

            // 4. 替换占位符
            String filledSql = replacePlaceholders(sql, paramValues);

            // 5. 构建参数映射
            for (int i = 0; i < paramValues.size(); i++) {
                parameters.put("param" + (i + 1), paramValues.get(i));
            }

            String description = String.format("场景：%s, 涉及表：%s, 参数数量：%d",
                scenarioName != null ? scenarioName : "默认",
                String.join(", ", involvedTables),
                paramValues.size());

            return new FillResult(sql, filledSql, parameters, description, warnings);

        } catch (Exception e) {
            System.out.println("[SqlFillerService] 填充 SQL 参数失败：" + e.getMessage());
            warnings.add("填充失败：" + e.getMessage());
            return new FillResult(sql, sql, parameters, "填充失败", warnings);
        }
    }

    /**
     * 收集表的列统计信息
     */
    private Map<String, ColumnStats> collectColumnStats(JdbcTemplate jdbcTemplate, String tableName) {
        Map<String, ColumnStats> statsMap = new HashMap<>();

        try {
            // 1. 获取表的所有列名
            String columnsSql = """
                SELECT COLUMN_NAME, DATA_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """;
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnsSql, tableName);

            for (Map<String, Object> col : columns) {
                String columnName = (String) col.get("COLUMN_NAME");
                String dataType = (String) col.get("DATA_TYPE");

                // 2. 收集每列的统计信息
                ColumnStats stats = collectSingleColumnStats(jdbcTemplate, tableName, columnName, dataType);
                statsMap.put(columnName, stats);
            }

        } catch (Exception e) {
            System.err.println("[SqlFillerService] 收集列统计信息失败：" + tableName + " - " + e.getMessage());
        }

        return statsMap;
    }

    /**
     * 收集单列的统计信息
     */
    private ColumnStats collectSingleColumnStats(JdbcTemplate jdbcTemplate,
                                                  String tableName, String columnName, String dataType) {
        try {
            // 查询统计信息
            String statsSql = String.format("""
                SELECT
                    COUNT(*) as total_count,
                    SUM(CASE WHEN `%1$s` IS NULL THEN 1 ELSE 0 END) as null_count,
                    COUNT(DISTINCT `%1$s`) as distinct_count,
                    MIN(`%1$s`) as min_value,
                    MAX(`%1$s`) as max_value
                FROM `%2$s`
                """, columnName, tableName);

            Map<String, Object> stats = jdbcTemplate.queryForMap(statsSql);

            // 采样值（最多 10 个）
            String sampleSql = String.format("""
                SELECT DISTINCT `%1$s`
                FROM `%2$s`
                WHERE `%1$s` IS NOT NULL
                ORDER BY RAND()
                LIMIT 10
                """, columnName, tableName);
            List<Map<String, Object>> samples = jdbcTemplate.queryForList(sampleSql);
            List<Object> sampleValues = samples.stream()
                .map(m -> m.get(columnName))
                .filter(Objects::nonNull)
                .toList();

            return new ColumnStats(
                columnName,
                dataType,
                (Number) stats.get("null_count") != null ? ((Number) stats.get("null_count")).longValue() : 0L,
                (Number) stats.get("distinct_count") != null ? ((Number) stats.get("distinct_count")).longValue() : 0L,
                stats.get("min_value"),
                stats.get("max_value"),
                sampleValues
            );

        } catch (Exception e) {
            System.err.println("[SqlFillerService] 收集列统计信息失败：" + tableName + "." + columnName + " - " + e.getMessage());
            return new ColumnStats(columnName, dataType, 0L, 0L, null, null, Collections.emptyList());
        }
    }

    /**
     * 生成参数值列表
     */
    private List<Object> generateParamValues(String sql,
                                              Map<String, ColumnStats> columnStatsMap,
                                              Map<String, BusinessSemantics> semanticsMap,
                                              List<String> warnings) {
        List<Object> paramValues = new ArrayList<>();

        // 统计占位符数量
        int placeholderCount = countPlaceholders(sql);

        // 从 SQL 中提取可能的字段线索（简化版，实际应该更复杂）
        List<String> fieldHints = extractFieldHintsFromSql(sql);

        for (int i = 0; i < placeholderCount; i++) {
            Object value = generateSingleParamValue(i, sql, columnStatsMap, semanticsMap, fieldHints, warnings);
            paramValues.add(value);
        }

        return paramValues;
    }

    /**
     * 统计 SQL 中占位符数量
     */
    private int countPlaceholders(String sql) {
        // 简单统计 ? 的数量（排除转义的）
        int count = 0;
        boolean inString = false;
        char prevChar = 0;

        for (char c : sql.toCharArray()) {
            if (c == '\'' && prevChar != '\\') {
                inString = !inString;
            } else if (c == '?' && !inString) {
                count++;
            }
            prevChar = c;
        }

        return count;
    }

    /**
     * 从 SQL 中提取字段提示
     */
    private List<String> extractFieldHintsFromSql(String sql) {
        List<String> hints = new ArrayList<>();

        // 提取 WHERE 子句中的字段名
        Pattern pattern = Pattern.compile("WHERE.*?(?=ORDER|GROUP|HAVING|LIMIT|$)", Pattern.CASE_INSENSITIVE);
        Matcher whereMatcher = pattern.matcher(sql);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group();

            // 提取字段名（简化版）
            Pattern fieldPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*[=<>]");
            Matcher fieldMatcher = fieldPattern.matcher(whereClause);
            while (fieldMatcher.find()) {
                String fieldName = fieldMatcher.group(1);
                if (!fieldName.equalsIgnoreCase("AND") && !fieldName.equalsIgnoreCase("OR")
                    && !fieldName.equalsIgnoreCase("NOT") && !fieldName.equalsIgnoreCase("NULL")) {
                    hints.add(fieldName);
                }
            }
        }

        return hints;
    }

    /**
     * 生成单个参数值
     */
    private Object generateSingleParamValue(int paramIndex, String sql,
                                             Map<String, ColumnStats> columnStatsMap,
                                             Map<String, BusinessSemantics> semanticsMap,
                                             List<String> fieldHints,
                                             List<String> warnings) {
        // 1. 尝试根据字段提示匹配列统计信息
        for (String fieldHint : fieldHints) {
            if (columnStatsMap.containsKey(fieldHint)) {
                ColumnStats stats = columnStatsMap.get(fieldHint);
                return generateValueByDataType(stats, warnings);
            }
        }

        // 2. 没有明确匹配时，根据 SQL 上下文推测
        // 检测是否是日期相关的占位符
        if (sql.toLowerCase().contains("date") || sql.toLowerCase().contains("time")) {
            return generateDateValue(columnStatsMap, warnings);
        }

        // 检测是否是金额相关的占位符
        if (sql.toLowerCase().contains("amount") || sql.toLowerCase().contains("balance")) {
            return generateAmountValue(columnStatsMap, warnings);
        }

        // 检测是否是状态相关的占位符
        if (sql.toLowerCase().contains("status")) {
            return generateStatusValue(semanticsMap, warnings);
        }

        // 3. 默认值：返回一个合理的默认值
        return generateDefaultValue(warnings);
    }

    /**
     * 根据数据类型生成值
     */
    private Object generateValueByDataType(ColumnStats stats, List<String> warnings) {
        if (stats == null || stats.sampleValues().isEmpty()) {
            return generateDefaultValue(warnings);
        }

        String dataType = stats.dataType().toLowerCase();

        // 从采样值中随机选择一个
        Random rand = new Random();
        Object sampleValue = stats.sampleValues().get(rand.nextInt(stats.sampleValues().size()));

        return convertValueByType(sampleValue, dataType);
    }

    /**
     * 生成日期值（数据驱动）
     */
    private Object generateDateValue(Map<String, ColumnStats> columnStatsMap, List<String> warnings) {
        // 查找日期类型的列
        for (ColumnStats stats : columnStatsMap.values()) {
            if (stats.dataType().equalsIgnoreCase("date") ||
                stats.dataType().equalsIgnoreCase("datetime") ||
                stats.dataType().equalsIgnoreCase("timestamp")) {

                if (stats.minValue() != null) {
                    // 从采样值中选择一个日期
                    if (!stats.sampleValues().isEmpty()) {
                        return stats.sampleValues().get(0);
                    }
                    return stats.minValue();
                }
            }
        }

        // 默认返回最近 30 天的日期
        String defaultDate = LocalDate.now().minusDays(15).format(DateTimeFormatter.ISO_LOCAL_DATE);
        warnings.add("未找到日期列统计，使用默认日期：" + defaultDate);
        return defaultDate;
    }

    /**
     * 生成金额值
     */
    private Object generateAmountValue(Map<String, ColumnStats> columnStatsMap, List<String> warnings) {
        for (ColumnStats stats : columnStatsMap.values()) {
            if (stats.dataType().equalsIgnoreCase("decimal") ||
                stats.dataType().equalsIgnoreCase("numeric")) {

                if (stats.sampleValues().isEmpty()) {
                    return BigDecimal.valueOf(1000.00);
                }

                // 选择一个中等的金额
                Object sample = stats.sampleValues().get(stats.sampleValues().size() / 2);
                return sample;
            }
        }

        warnings.add("未找到金额列统计，使用默认金额：1000.00");
        return BigDecimal.valueOf(1000.00);
    }

    /**
     * 生成状态值（基于业务语义）
     */
    private Object generateStatusValue(Map<String, BusinessSemantics> semanticsMap, List<String> warnings) {
        // 从业务语义中获取状态字段的有效值
        for (BusinessSemantics semantics : semanticsMap.values()) {
            if (semantics.getFieldSemanticsMap() != null) {
                for (var fieldSemantics : semantics.getFieldSemanticsMap().values()) {
                    if (fieldSemantics.getFieldName().toLowerCase().contains("status")) {
                        // 返回正常状态值（通常是 1）
                        return 1;
                    }
                }
            }
        }

        warnings.add("未找到状态语义信息，使用默认状态：1");
        return 1;
    }

    /**
     * 生成默认值
     */
    private Object generateDefaultValue(List<String> warnings) {
        // 根据 SQL 上下文推测最可能的类型
        if (warnings.size() < 3) {
            return "test_value";  // 字符串默认值
        }
        return 1;  // 数字默认值
    }

    /**
     * 根据类型转换值
     */
    private Object convertValueByType(Object value, String dataType) {
        if (value == null) {
            return null;
        }

        String type = dataType.toLowerCase();

        if (type.contains("int") || type.contains("tinyint") || type.contains("smallint")) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return 1;
            }
        }

        if (type.contains("bigint")) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return 1L;
            }
        }

        if (type.contains("decimal") || type.contains("numeric") || type.contains("money")) {
            if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                return BigDecimal.valueOf(100.00);
            }
        }

        if (type.contains("date") || type.contains("time")) {
            return value.toString();
        }

        // 默认返回字符串
        return value.toString();
    }

    /**
     * 替换占位符为实际值
     */
    private String replacePlaceholders(String sql, List<Object> paramValues) {
        String result = sql;

        for (Object value : paramValues) {
            String formattedValue = formatValue(value);
            result = result.replaceFirst("\\?", formattedValue);
        }

        return result;
    }

    /**
     * 格式化参数值为 SQL 字面量
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof String) {
            // 字符串需要加引号，并转义单引号
            String escaped = ((String) value).replace("'", "''");
            return "'" + escaped + "'";
        }

        if (value instanceof BigDecimal) {
            return value.toString();
        }

        if (value instanceof Number) {
            return value.toString();
        }

        // 其他类型当作字符串处理
        String escaped = value.toString().replace("'", "''");
        return "'" + escaped + "'";
    }

    /**
     * 为指定字段生成特定类型的测试值
     * 用于更精确的填充
     */
    public Object generateValueForField(JdbcTemplate jdbcTemplate, String tableName,
                                         String columnName, String valueType) {
        ColumnStats stats = collectSingleColumnStats(jdbcTemplate, tableName, columnName,
            jdbcTemplate.queryForObject(
                "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                String.class, tableName, columnName
            ));

        return switch (valueType.toLowerCase()) {
            case "common" -> stats.sampleValues().isEmpty() ? null : stats.sampleValues().get(0);
            case "max" -> stats.maxValue();
            case "min" -> stats.minValue();
            case "null" -> null;
            case "random" -> stats.sampleValues().isEmpty() ? null :
                    stats.sampleValues().get(new Random().nextInt(stats.sampleValues().size()));
            default -> generateValueByDataType(stats, new ArrayList<>());
        };
    }
}
