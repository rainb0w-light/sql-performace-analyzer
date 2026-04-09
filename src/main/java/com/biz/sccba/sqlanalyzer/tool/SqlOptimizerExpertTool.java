package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.service.TestEnvironmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 优化专家工具
 *
 * 专注于 SQL 查询层面的优化：
 * - 覆盖索引建议
 * - JOIN 顺序优化
 * - 查询重写建议
 * - 子查询优化
 * - SELECT * 检测
 * - 分页查询优化
 *
 * 优先级：低于 InnoDB 和分布式专家（作为补充建议）
 */
@Component
public class SqlOptimizerExpertTool {

    private final TestEnvironmentService testEnvironmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     */
    public SqlOptimizerExpertTool(TestEnvironmentService testEnvironmentService) {
        this.testEnvironmentService = testEnvironmentService;
    }

    /**
     * 执行 SQL 优化专家分析
     */
    @Tool(name = "sql_optimizer_analyze", description = "SQL 优化专家分析工具，分析 SQL 查询语句本身，提供查询重写、索引覆盖、JOIN 优化等建议")
    public String execute(
            @ToolParam(name = "datasourceName", description = "数据源名称", required = true) String datasourceName,
            @ToolParam(name = "sql", description = "要分析的 SQL 语句", required = true) String sql,
            @ToolParam(name = "tables", description = "涉及的表名列表（可选）", required = false) List<String> tables) {

        System.out.println("SQL 优化专家分析：datasource=" + datasourceName + ", sql=" + sql);

        try {
            JdbcTemplate jdbcTemplate = testEnvironmentService.getJdbcTemplate(datasourceName);

            // 1. 分析 SELECT 子句
            List<OptimizationSuggestion> selectIssues = analyzeSelectClause(sql);

            // 2. 分析 JOIN 子句
            List<OptimizationSuggestion> joinIssues = analyzeJoinClause(sql, jdbcTemplate);

            // 3. 分析 WHERE 子句
            List<OptimizationSuggestion> whereIssues = analyzeWhereClause(sql);

            // 4. 分析 ORDER BY 和 LIMIT
            List<OptimizationSuggestion> orderByLimitIssues = analyzeOrderByLimit(sql);

            // 5. 分析子查询
            List<OptimizationSuggestion> subqueryIssues = analyzeSubquery(sql);

            // 6. 合并所有建议
            List<OptimizationSuggestion> allSuggestions = new ArrayList<>();
            allSuggestions.addAll(selectIssues);
            allSuggestions.addAll(joinIssues);
            allSuggestions.addAll(whereIssues);
            allSuggestions.addAll(orderByLimitIssues);
            allSuggestions.addAll(subqueryIssues);

            // 7. 生成重写的 SQL（如果有优化建议）
            String rewrittenSql = generateRewrittenSql(sql, allSuggestions);

            // 8. 计算指标
            Map<String, Object> metrics = calculateMetrics(sql, allSuggestions);

            // 9. 确定优先级和置信度
            int priority = determinePriority(allSuggestions);
            double confidence = determineConfidence(allSuggestions);

            SqlOptimizerAnalysisResult result = new SqlOptimizerAnalysisResult(
                sql,
                allSuggestions,
                rewrittenSql != null ? rewrittenSql : sql,
                priority,
                confidence,
                metrics
            );

            return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "result", result,
                "expertType", "SQLOptimizer",
                "priority", priority,
                "confidence", confidence,
                "suggestionCount", allSuggestions.size()
            ));

        } catch (Exception e) {
            System.out.println("SQL 优化专家分析失败：" + e.getMessage());
            e.printStackTrace();
            try {
                return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
            } catch (Exception ex) {
                return "{\"error\": \"" + ex.getMessage() + "\"}";
            }
        }
    }

    /**
     * 分析 SELECT 子句
     */
    private List<OptimizationSuggestion> analyzeSelectClause(String sql) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        String sqlUpper = sql.toUpperCase();

        // 1. 检测 SELECT *
        if (sqlUpper.contains("SELECT *") || sqlUpper.contains("SELECT  *")) {
            suggestions.add(new OptimizationSuggestion(
                "SELECT",
                "使用了 SELECT *，会导致：1. 无法使用覆盖索引 2. 增加网络传输 3. 增加内存消耗",
                "建议明确指定需要的字段，避免使用 SELECT *",
                "-- 示例：SELECT id, name, status FROM table WHERE ...",
                1
            ));
        }

        // 2. 检测 SELECT DISTINCT + ORDER BY
        if (sqlUpper.contains("SELECT DISTINCT") && sqlUpper.contains("ORDER BY")) {
            suggestions.add(new OptimizationSuggestion(
                "SELECT",
                "SELECT DISTINCT + ORDER BY 可能导致额外的排序操作",
                "如果可能，考虑使用 GROUP BY 替代 DISTINCT",
                null,
                2
            ));
        }

        // 3. 检测函数包裹列（导致索引失效）
        Pattern funcPattern = Pattern.compile(
            "(WHERE|AND|OR)\\s+[^=]+(NOW\\(|CURDATE\\(|DATE\\(|YEAR\\(|MONTH\\(|UPPER\\(|LOWER\\(|TRIM\\(|SUBSTRING\\(|LENGTH\\()",
            Pattern.CASE_INSENSITIVE
        );
        Matcher funcMatcher = funcPattern.matcher(sql);
        if (funcMatcher.find()) {
            suggestions.add(new OptimizationSuggestion(
                "SELECT",
                "WHERE 条件中对列使用了函数，会导致索引失效",
                "建议改写为范围查询或使用计算列",
                "-- 示例：将 WHERE DATE(create_time) = '2024-01-01' 改为 WHERE create_time >= '2024-01-01' AND create_time < '2024-01-02'",
                1
            ));
        }

        // 4. 检测类型转换
        if (sqlUpper.contains("CAST(") || sqlUpper.contains("CONVERT(")) {
            suggestions.add(new OptimizationSuggestion(
                "SELECT",
                "使用了类型转换函数，可能导致索引失效",
                "确保比较的两侧数据类型一致，避免隐式转换",
                null,
                2
            ));
        }

        return suggestions;
    }

    /**
     * 分析 JOIN 子句
     */
    private List<OptimizationSuggestion> analyzeJoinClause(String sql, JdbcTemplate jdbcTemplate) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        String sqlUpper = sql.toUpperCase();

        // 1. 检测是否有 JOIN
        if (!sqlUpper.contains(" JOIN ")) {
            return suggestions;
        }

        // 2. 检测 LEFT JOIN 的滥用
        if (sqlUpper.contains("LEFT JOIN") || sqlUpper.contains("RIGHT JOIN")) {
            suggestions.add(new OptimizationSuggestion(
                "JOIN",
                "使用了 LEFT/RIGHT JOIN，请确认是否需要保留左/右表的所有记录",
                "如果只需要匹配的记录，使用 INNER JOIN 性能更好",
                null,
                2
            ));
        }

        // 3. 检测多表 JOIN 数量
        int joinCount = sqlUpper.split(" JOIN ").length - 1;
        if (joinCount >= 3) {
            suggestions.add(new OptimizationSuggestion(
                "JOIN",
                "JOIN 表数量较多 (" + joinCount + ")，可能导致执行计划复杂",
                "建议：1. 检查是否可以拆分查询 2. 确保关联字段有索引 3. 考虑冗余字段减少 JOIN",
                null,
                1
            ));
        }

        // 4. 检测 ON 条件
        Pattern onPattern = Pattern.compile("ON\\s+([^\\s]+)\\s*=\\s*([^\\s,\\)]+)", Pattern.CASE_INSENSITIVE);
        Matcher onMatcher = onPattern.matcher(sql);
        while (onMatcher.find()) {
            String leftCol = onMatcher.group(1).toUpperCase();
            String rightCol = onMatcher.group(2).toUpperCase();

            // 检查是否是函数或表达式
            if (leftCol.contains("(") || rightCol.contains("(")) {
                suggestions.add(new OptimizationSuggestion(
                    "JOIN",
                    "JOIN 条件使用了函数或表达式，可能导致索引失效",
                    "建议直接在关联字段上建立索引",
                    null,
                    1
                ));
            }
        }

        // 5. 检测 CROSS JOIN（笛卡尔积）
        if (sqlUpper.contains("CROSS JOIN") || (sqlUpper.contains(",") && !sqlUpper.contains("WHERE"))) {
            suggestions.add(new OptimizationSuggestion(
                "JOIN",
                    "检测到可能的 CROSS JOIN（笛卡尔积），会产生大量中间结果",
                    "建议添加合适的 JOIN 条件",
                    null,
                    1
                ));
        }

        return suggestions;
    }

    /**
     * 分析 WHERE 子句
     */
    private List<OptimizationSuggestion> analyzeWhereClause(String sql) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        String sqlUpper = sql.toUpperCase();

        // 1. 检测 OR 条件
        if (sqlUpper.contains(" OR ")) {
            suggestions.add(new OptimizationSuggestion(
                "WHERE",
                "使用了 OR 条件，可能导致索引失效",
                "建议：1. 使用 UNION ALL 替代 OR 2. 或者在涉及的字段上都建立索引",
                "-- 示例：将 WHERE a = 1 OR b = 2 改为 SELECT ... WHERE a = 1 UNION ALL SELECT ... WHERE b = 2",
                2
            ));
        }

        // 2. 检测 NOT 条件
        if (sqlUpper.contains(" NOT ") || sqlUpper.contains("<>") || sqlUpper.contains("!=")) {
            suggestions.add(new OptimizationSuggestion(
                "WHERE",
                "使用了 NOT、<> 或 != 条件，可能导致索引失效",
                "考虑改写为正条件或使用 EXISTS/NOT EXISTS",
                null,
                2
            ));
        }

        // 3. 检测 LIKE '%...' 前缀通配符
        Pattern likePattern = Pattern.compile("LIKE\\s*'%[^']+", Pattern.CASE_INSENSITIVE);
        Matcher likeMatcher = likePattern.matcher(sql);
        if (likeMatcher.find()) {
            suggestions.add(new OptimizationSuggestion(
                "WHERE",
                "LIKE 使用了前缀通配符（%...），无法使用索引",
                "建议：1. 使用全文索引 2. 或者使用搜索引擎 3. 或者改写业务逻辑",
                null,
                1
            ));
        }

        // 4. 检测 IN 子句元素过多
        Pattern inPattern = Pattern.compile("\\bIN\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher inMatcher = inPattern.matcher(sql);
        while (inMatcher.find()) {
            String inContent = inMatcher.group(1);
            int elementCount = inContent.split(",").length;
            if (elementCount > 10) {
                suggestions.add(new OptimizationSuggestion(
                    "WHERE",
                    "IN 子句元素过多 (" + elementCount + ")，可能导致性能问题",
                    "建议：1. 使用临时表 2. 或使用 EXISTS 替代",
                    null,
                    2
                ));
            }
        }

        // 5. 检测 IS NULL / IS NOT NULL
        if (sqlUpper.contains(" IS NULL") || sqlUpper.contains(" IS NOT NULL")) {
            suggestions.add(new OptimizationSuggestion(
                "WHERE",
                "IS NULL / IS NOT NULL 条件在某些情况下无法使用索引",
                "确保 NULL 值的列也有合适的索引，或考虑使用默认值替代 NULL",
                null,
                3
            ));
        }

        return suggestions;
    }

    /**
     * 分析 ORDER BY 和 LIMIT
     */
    private List<OptimizationSuggestion> analyzeOrderByLimit(String sql) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        String sqlUpper = sql.toUpperCase();

        // 1. 检测 ORDER BY + LIMIT 深度分页
        Pattern limitPattern = Pattern.compile("LIMIT\\s+(\\d+)\\s*,\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher limitMatcher = limitPattern.matcher(sql);
        if (limitMatcher.find()) {
            int offset = Integer.parseInt(limitMatcher.group(1));
            int limit = Integer.parseInt(limitMatcher.group(2));

            if (offset > 1000) {
                suggestions.add(new OptimizationSuggestion(
                    "LIMIT",
                    "深度分页（OFFSET=" + offset + "），会导致扫描大量数据后丢弃",
                    "建议：1. 使用游标分页 2. 或记录上次查询的最大 ID 继续查询",
                    "-- 示例：将 LIMIT 10000, 10 改为 WHERE id > last_id LIMIT 10",
                    1
                ));
            }
        }

        // 2. 检测 ORDER BY 多字段
        Pattern orderByPattern = Pattern.compile("ORDER\\s+BY\\s+([^\\s]+(?:\\s*,\\s*[^\\s]+)*)", Pattern.CASE_INSENSITIVE);
        Matcher orderByMatcher = orderByPattern.matcher(sql);
        if (orderByMatcher.find()) {
            String orderByCols = orderByMatcher.group(1);
            int colCount = orderByCols.split(",").length;

            if (colCount >= 3) {
                suggestions.add(new OptimizationSuggestion(
                    "ORDER_BY",
                    "ORDER BY 字段较多 (" + colCount + ")，可能导致文件排序",
                    "建议在 ORDER BY 字段上建立联合索引，注意顺序和方向（ASC/DESC）",
                    "-- 示例：CREATE INDEX idx_order ON table(col1, col2, col3)",
                    2
                ));
            }

            // 检查是否有 DESC
            if (orderByCols.toUpperCase().contains("DESC") && orderByCols.toUpperCase().contains("ASC")) {
                suggestions.add(new OptimizationSuggestion(
                    "ORDER_BY",
                    "ORDER BY 混合使用了 ASC 和 DESC，无法使用索引排序",
                    "如果可能，统一排序方向",
                    null,
                    2
                ));
            }
        }

        // 3. 检测只有 LIMIT 没有 ORDER BY
        if (sqlUpper.contains("LIMIT") && !sqlUpper.contains("ORDER BY")) {
            suggestions.add(new OptimizationSuggestion(
                "LIMIT",
                "LIMIT 没有配合 ORDER BY，返回的记录顺序不确定",
                "如果需要确定的顺序，请添加 ORDER BY 子句",
                null,
                3
            ));
        }

        return suggestions;
    }

    /**
     * 分析子查询
     */
    private List<OptimizationSuggestion> analyzeSubquery(String sql) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        String sqlUpper = sql.toUpperCase();

        // 检测子查询
        int selectCount = sqlUpper.split("SELECT").length - 1;
        if (selectCount > 1) {
            // 检测 IN 子查询
            if (sqlUpper.contains("IN (SELECT") || sqlUpper.contains("IN  (SELECT")) {
                suggestions.add(new OptimizationSuggestion(
                    "SUBQUERY",
                    "IN 子查询可能导致性能问题，尤其是在 MySQL 8.0 之前",
                    "建议：1. 使用 EXISTS 替代 2. 或改为 JOIN",
                    "-- 示例：将 WHERE id IN (SELECT pid FROM ...) 改为 WHERE EXISTS (SELECT 1 FROM ... WHERE pid = id)",
                    2
                ));
            }

            // 检测标量子查询（SELECT 列表中）
            Pattern selectSubqueryPattern = Pattern.compile("SELECT\\s+[^FROM]+\\(\\s*SELECT", Pattern.CASE_INSENSITIVE);
            Matcher selectSubqueryMatcher = selectSubqueryPattern.matcher(sql);
            if (selectSubqueryMatcher.find()) {
                suggestions.add(new OptimizationSuggestion(
                    "SUBQUERY",
                    "SELECT 列表中的标量子查询会对每行执行一次，性能很差",
                    "建议：1. 改为 JOIN 2. 或使用派生表",
                    null,
                    1
                ));
            }

            // 检测 WHERE 中的相关子查询
            if (sqlUpper.contains("WHERE") && sqlUpper.contains("SELECT") && selectCount > 1) {
                suggestions.add(new OptimizationSuggestion(
                    "SUBQUERY",
                    "WHERE 条件中的相关子查询可能对每行执行，导致性能问题",
                    "建议：1. 使用 JOIN 2. 或使用 EXISTS/NOT EXISTS",
                    null,
                    2
                ));
            }
        }

        // 检测派生表（FROM 子句中的子查询）
        if (sqlUpper.contains("FROM (SELECT") || sqlUpper.contains("FROM  (SELECT")) {
            suggestions.add(new OptimizationSuggestion(
                "SUBQUERY",
                "FROM 子句中的派生表（子查询）可能导致物化，影响性能",
                "建议：1. 确保派生表有合适的 WHERE 条件 2. 或考虑使用 CTE（MySQL 8.0+）",
                null,
                2
            ));
        }

        return suggestions;
    }

    /**
     * 生成重写后的 SQL
     */
    private String generateRewrittenSql(String sql, List<OptimizationSuggestion> suggestions) {
        // 这里只生成简单的重写建议，复杂的重写需要更深入的语义分析
        String rewritten = sql;

        // 1. SELECT * 替换为具体字段（需要表结构信息，这里只生成占位符）
        if (suggestions.stream().anyMatch(s -> s.category().equals("SELECT") && s.issue().contains("SELECT *"))) {
            rewritten = rewritten.replaceAll("(?i)SELECT\\s+\\*", "SELECT /* 请替换为具体字段 */ field1, field2, field3");
        }

        // 2. 深度分页优化
        if (suggestions.stream().anyMatch(s -> s.category().equals("LIMIT") && s.issue().contains("深度分页"))) {
            // 这里需要更多上下文才能正确重写，只生成注释
            rewritten = "-- 建议改写为游标分页或记录最大 ID 方式\n" + rewritten;
        }

        return rewritten;
    }

    /**
     * 计算优化指标
     */
    private Map<String, Object> calculateMetrics(String sql, List<OptimizationSuggestion> suggestions) {
        Map<String, Object> metrics = new HashMap<>();

        metrics.put("sqlLength", sql.length());
        metrics.put("suggestionCount", suggestions.size());

        // 统计各级别问题数量
        long highImpact = suggestions.stream().filter(s -> s.impactLevel() == 1).count();
        long mediumImpact = suggestions.stream().filter(s -> s.impactLevel() == 2).count();
        long lowImpact = suggestions.stream().filter(s -> s.impactLevel() == 3).count();

        metrics.put("highImpactIssues", highImpact);
        metrics.put("mediumImpactIssues", mediumImpact);
        metrics.put("lowImpactIssues", lowImpact);

        // 优化潜力评分（0-100）
        int score = 100;
        score -= highImpact * 15;
        score -= mediumImpact * 8;
        score -= lowImpact * 3;
        score = Math.max(0, score);
        metrics.put("optimizationScore", score);

        return metrics;
    }

    /**
     * 确定优先级
     */
    private int determinePriority(List<OptimizationSuggestion> suggestions) {
        long highImpact = suggestions.stream().filter(s -> s.impactLevel() == 1).count();

        if (highImpact >= 2) {
            return 1;  // 高优先级
        } else if (highImpact >= 1 || suggestions.size() >= 3) {
            return 2;  // 中优先级
        }
        return 3;  // 低优先级
    }

    /**
     * 确定置信度
     */
    private double determineConfidence(List<OptimizationSuggestion> suggestions) {
        // 基于规则的分析，置信度较高
        double confidence = 0.8;

        // 问题越多，置信度越高
        if (suggestions.size() >= 3) {
            confidence += 0.1;
        }

        return Math.min(confidence, 0.95);
    }

    /**
     * SQL 优化专家分析结果
     */
    public record SqlOptimizerAnalysisResult(
        String sql,
        List<OptimizationSuggestion> suggestions,
        String rewrittenSql,
        int priority,
        double confidence,
        Map<String, Object> metrics
    ) {}

    /**
     * 优化建议
     */
    public record OptimizationSuggestion(
        String category,     // SELECT, JOIN, WHERE, ORDER_BY, LIMIT, INDEX
        String issue,        // 问题描述
        String suggestion,   // 优化建议
        String ddlOrRewrite, // 相关的 DDL 或重写后的 SQL
        int impactLevel      // 影响程度：1-高 2-中 3-低
    ) {}
}
