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
 * SQL 查询复杂度分析工具
 */
@Component
public class SqlQueryComplexityAnalyzer {

    private final TestEnvironmentService testEnvironmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqlQueryComplexityAnalyzer(TestEnvironmentService testEnvironmentService) {
        this.testEnvironmentService = testEnvironmentService;
    }

    /**
     * 分析 SQL 查询复杂度
     */
    @Tool(name = "analyze_sql_complexity", description = "分析 SQL 查询的复杂度，提供复杂度评分和优化建议")
    public String analyzeSqlComplexity(
            @ToolParam(name = "sql", description = "SQL 语句", required = true) String sql,
            @ToolParam(name = "datasourceName", description = "数据源名称（可选）", required = false) String datasourceName) {
        System.out.println("[SqlQueryComplexityAnalyzer] 分析 SQL 复杂度 (数据源：" + datasourceName + ")");
        try {
            ComplexityAnalysisResult result = analyzeComplexity(sql, datasourceName);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public ComplexityAnalysisResult analyzeComplexity(String sql, String datasourceName) {
        if (sql == null || sql.trim().isEmpty()) {
            return ComplexityAnalysisResult.error("SQL 语句不能为空");
        }

        String normalizedSql = normalizeSql(sql);
        ComplexityMetrics metrics = calculateComplexityMetrics(normalizedSql);
        DeepAnalysis deepAnalysis = performDeepAnalysis(normalizedSql, datasourceName);
        double overallScore = calculateOverallScore(metrics);
        List<String> recommendations = generateRecommendations(metrics, deepAnalysis);
        String priority = determinePriority(overallScore);
        
        return new ComplexityAnalysisResult(
            sql, normalizedSql, metrics, deepAnalysis, overallScore, priority, recommendations
        );
    }

    private String normalizeSql(String sql) {
        return sql.replaceAll("--.*?(?=\\n|$)", "")  // 移除单行注释
                  .replaceAll("/\\*.*?\\*/", "")      // 移除多行注释
                  .replaceAll("\\s+", " ")            // 多个空白替换为一个
                  .trim()
                  .toUpperCase();
    }

    private ComplexityMetrics calculateComplexityMetrics(String sql) {
        return new ComplexityMetrics(
            countTables(sql),
            analyzeJoins(sql),
            analyzeSubqueries(sql),
            analyzeWhereClause(sql),
            analyzeSelectList(sql),
            analyzeGroupBy(sql),
            analyzeOrderBy(sql),
            countSetOperations(sql),
            countCTEs(sql),
            Math.min(sql.length() / 1000.0, 10.0)
        );
    }

    private int countTables(String sql) {
        int count = 0;
        
        Pattern fromPattern = Pattern.compile("\\bFROM\\s+([A-Z_][A-Z0-9_$]*|\\([^)]+\\))", Pattern.CASE_INSENSITIVE);
        Matcher fromMatcher = fromPattern.matcher(sql);
        while (fromMatcher.find()) {
            String tableName = fromMatcher.group(1);
            if (!tableName.startsWith("(")) {
                count++;
            }
        }
        
        Pattern joinPattern = Pattern.compile("\\bJOIN\\s+([A-Z_][A-Z0-9_$]*)", Pattern.CASE_INSENSITIVE);
        Matcher joinMatcher = joinPattern.matcher(sql);
        while (joinMatcher.find()) {
            count++;
        }
        
        return count;
    }

    private JoinMetrics analyzeJoins(String sql) {
        int innerJoinCount = countOccurrences(sql, "\\bINNER\\s+JOIN\\b");
        int leftJoinCount = countOccurrences(sql, "\\bLEFT\\s+(?:OUTER\\s+)?JOIN\\b");
        int rightJoinCount = countOccurrences(sql, "\\bRIGHT\\s+(?:OUTER\\s+)?JOIN\\b");
        int fullJoinCount = countOccurrences(sql, "\\bFULL\\s+(?:OUTER\\s+)?JOIN\\b");
        int crossJoinCount = countOccurrences(sql, "\\bCROSS\\s+JOIN\\b");
        int implicitJoinCount = countImplicitJoins(sql);
        int totalJoins = innerJoinCount + leftJoinCount + rightJoinCount + fullJoinCount + crossJoinCount + implicitJoinCount;
        
        return new JoinMetrics(
            innerJoinCount, leftJoinCount, rightJoinCount, fullJoinCount,
            crossJoinCount, implicitJoinCount, totalJoins, analyzeJoinConditions(sql)
        );
    }

    /**
     * 统计匹配次数
     */
    private int countOccurrences(String sql, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 统计隐式 JOIN 数量
     */
    private int countImplicitJoins(String sql) {
        // 查找 FROM 子句中逗号分隔的表（排除 SELECT 列表中的逗号）
        Pattern fromPattern = Pattern.compile("\\bFROM\\s+([^WHERE]+?)(?:\\bWHERE\\b|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = fromPattern.matcher(sql);
        if (matcher.find()) {
            String fromClause = matcher.group(1);
            // 统计逗号数量（假设没有子查询）
            return (int) fromClause.chars().filter(ch -> ch == ',').count();
        }
        return 0;
    }

    /**
     * 分析 JOIN 条件复杂度
     */
    private int analyzeJoinConditions(String sql) {
        int complexity = 0;
        
        // 每个 ON 子句
        Pattern onPattern = Pattern.compile("\\bON\\s+([^JOIN]+?)(?=(?:\\bJOIN\\b|$))", Pattern.CASE_INSENSITIVE);
        Matcher onMatcher = onPattern.matcher(sql);
        while (onMatcher.find()) {
            String condition = onMatcher.group(1);
            // 每个 AND/OR 增加复杂度
            complexity += 1 + countOccurrences(condition, "\\bAND\\b") + countOccurrences(condition, "\\bOR\\b");
            // 函数调用增加复杂度
            complexity += countOccurrences(condition, "\\b[A-Z_]+\\s*\\(");
        }
        
        return complexity;
    }

    /**
     * 分析子查询
     */
    private SubqueryMetrics analyzeSubqueries(String sql) {
        int whereSubqueryCount = countOccurrences(sql, "\\bWHERE\\s+[^WHERE]*\\bSELECT\\b");
        int fromSubqueryCount = countOccurrences(sql, "\\bFROM\\s*\\(");
        int joinSubqueryCount = countOccurrences(sql, "\\bJOIN\\s*\\(");
        int selectSubqueryCount = countOccurrences(sql, "SELECT\\s+\\([^)]*\\bSELECT\\b");
        int totalSubqueries = whereSubqueryCount + fromSubqueryCount + joinSubqueryCount + selectSubqueryCount;
        
        return new SubqueryMetrics(
            whereSubqueryCount,
            fromSubqueryCount,
            joinSubqueryCount,
            selectSubqueryCount,
            totalSubqueries,
            estimateNestingDepth(sql)
        );
    }

    /**
     * 估算子查询嵌套深度
     */
    private int estimateNestingDepth(String sql) {
        int maxDepth = 0;
        int currentDepth = 0;
        
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                currentDepth++;
                if (i + 1 < sql.length() && sql.substring(i).matches("(?i).*\\s*SELECT.*")) {
                    maxDepth = Math.max(maxDepth, currentDepth);
                }
            } else if (c == ')') {
                currentDepth--;
            }
        }
        
        return maxDepth;
    }

    /**
     * 分析 WHERE 条件复杂度
     */
    private WhereComplexity analyzeWhereClause(String sql) {
        int conditionCount = 0, notConditionCount = 0, inClauseCount = 0, betweenCount = 0;
        int likeCount = 0, wildcardLikeCount = 0, regexCount = 0, functionCount = 0;
        int existsCount = 0, nullCheckCount = 0, caseCount = 0;
        List<Integer> inClauseElements = new ArrayList<>();
        
        Pattern wherePattern = Pattern.compile("\\bWHERE\\s+(.+?)(?=\\bGROUP\\s+BY\\b|\\bORDER\\s+BY\\b|\\bLIMIT\\b|$)", 
            Pattern.CASE_INSENSITIVE);
        Matcher matcher = wherePattern.matcher(sql);
        
        if (matcher.find()) {
            String whereClause = matcher.group(1);
            
            conditionCount = 1 + countOccurrences(whereClause, "\\bAND\\b") + 
                             countOccurrences(whereClause, "\\bOR\\b");
            notConditionCount = countOccurrences(whereClause, "\\bNOT\\b|!=|<>");
            inClauseCount = countOccurrences(whereClause, "\\bIN\\s*\\(");
            inClauseElements = countInClauseElements(whereClause);
            betweenCount = countOccurrences(whereClause, "\\bBETWEEN\\b");
            likeCount = countOccurrences(whereClause, "\\bLIKE\\b");
            wildcardLikeCount = countOccurrences(whereClause, "LIKE\\s*'%|LIKE\\s*'%.*%'");
            regexCount = countOccurrences(whereClause, "\\bREGEXP\\b|\\bRLIKE\\b");
            functionCount = countOccurrences(whereClause, "\\b[A-Z_]+\\s*\\(");
            existsCount = countOccurrences(whereClause, "\\bEXISTS\\s*\\(");
            nullCheckCount = countOccurrences(whereClause, "\\bIS\\s+(?:NOT\\s+)?NULL\\b");
            caseCount = countOccurrences(whereClause, "\\bCASE\\b");
        }
        
        return new WhereComplexity(
            conditionCount, notConditionCount, inClauseCount, inClauseElements,
            betweenCount, likeCount, wildcardLikeCount, regexCount,
            functionCount, existsCount, nullCheckCount, caseCount
        );
    }

    /**
     * 统计 IN 子句元素数量
     */
    private List<Integer> countInClauseElements(String whereClause) {
        List<Integer> counts = new ArrayList<>();
        Pattern inPattern = Pattern.compile("\\bIN\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = inPattern.matcher(whereClause);
        while (matcher.find()) {
            String elements = matcher.group(1);
            int count = (int) elements.chars().filter(ch -> ch == ',').count() + 1;
            counts.add(count);
        }
        return counts;
    }

    /**
     * 分析 SELECT 列表复杂度
     */
    private SelectComplexity analyzeSelectList(String sql) {
        Pattern selectPattern = Pattern.compile("\\bSELECT\\s+(.+?)\\s+FROM\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = selectPattern.matcher(sql);
        
        int columnCount = 0, aggregateCount = 0, aliasCount = 0, caseCount = 0;
        int subqueryCount = 0, functionCount = 0, computedColumnCount = 0;
        boolean isDistinct = false;
        int distinctCount = 0;
        
        if (matcher.find()) {
            String selectList = matcher.group(1);
            
            columnCount = (int) selectList.chars().filter(ch -> ch == ',').count() + 1;
            
            if (selectList.contains("DISTINCT")) {
                isDistinct = true;
                distinctCount = 1;
            }
            
            aggregateCount = countOccurrences(selectList, 
                "\\b(COUNT|SUM|AVG|MIN|MAX|GROUP_CONCAT|STRING_AGG)\\s*\\(");
            aliasCount = countOccurrences(selectList, "\\bAS\\b|\\s+[A-Z_][A-Z0-9_]*\\s*(?:,|$)");
            caseCount = countOccurrences(selectList, "\\bCASE\\b");
            subqueryCount = countOccurrences(selectList, "\\(\\s*SELECT\\b");
            functionCount = countOccurrences(selectList, "\\b[A-Z_]+\\s*\\(");
            computedColumnCount = countOccurrences(selectList, "[+\\-*/]");
        }
        
        return new SelectComplexity(
            columnCount, isDistinct, distinctCount, aggregateCount,
            aliasCount, caseCount, subqueryCount, functionCount, computedColumnCount
        );
    }

    /**
     * 分析 GROUP BY
     */
    private GroupByComplexity analyzeGroupBy(String sql) {
        int groupingColumnCount = 1, havingConditionCount = 0;
        boolean hasGroupingSets = false, hasRollup = false, hasCube = false;
        
        Pattern groupByPattern = Pattern.compile("\\bGROUP\\s+BY\\s+(.+?)(?=\\bHAVING\\b|\\bORDER\\s+BY\\b|\\bLIMIT\\b|$)", 
            Pattern.CASE_INSENSITIVE);
        Matcher matcher = groupByPattern.matcher(sql);
        
        if (matcher.find()) {
            String groupByClause = matcher.group(1);
            groupingColumnCount = (int) groupByClause.chars().filter(ch -> ch == ',').count() + 1;
            hasGroupingSets = groupByClause.contains("GROUPING SETS");
            hasRollup = groupByClause.contains("WITH ROLLUP") || groupByClause.contains("ROLLUP");
            hasCube = groupByClause.contains("WITH CUBE") || groupByClause.contains("CUBE");
        }
        
        Pattern havingPattern = Pattern.compile("\\bHAVING\\s+(.+?)(?=\\bORDER\\s+BY\\b|\\bLIMIT\\b|$)", 
            Pattern.CASE_INSENSITIVE);
        Matcher havingMatcher = havingPattern.matcher(sql);
        if (havingMatcher.find()) {
            havingConditionCount = 1 + countOccurrences(havingMatcher.group(1), "\\bAND\\b") + 
                                   countOccurrences(havingMatcher.group(1), "\\bOR\\b");
        }
        
        return new GroupByComplexity(
            groupingColumnCount, hasGroupingSets, hasRollup, hasCube, havingConditionCount
        );
    }

    /**
     * 分析 ORDER BY
     */
    private OrderByComplexity analyzeOrderBy(String sql) {
        int sortColumnCount = 1, functionSortCount = 0;
        int limitValue = 0, offsetValue = 0;
        boolean hasAsc = false, hasDesc = false, hasMixedSort = false;
        boolean hasOffset = false, isDeepPagination = false;
        
        Pattern orderByPattern = Pattern.compile("\\bORDER\\s+BY\\s+(.+?)(?=\\bLIMIT\\b|\\bFOR\\s+UPDATE\\b|$)", 
            Pattern.CASE_INSENSITIVE);
        Matcher matcher = orderByPattern.matcher(sql);
        
        if (matcher.find()) {
            String orderByClause = matcher.group(1);
            sortColumnCount = (int) orderByClause.chars().filter(ch -> ch == ',').count() + 1;
            hasAsc = orderByClause.contains("ASC");
            hasDesc = orderByClause.contains("DESC");
            hasMixedSort = hasAsc && hasDesc;
            functionSortCount = countOccurrences(orderByClause, "\\b[A-Z_]+\\s*\\(");
        }
        
        Pattern limitPattern = Pattern.compile("\\bLIMIT\\s+(\\d+)(?:\\s*,\\s*(\\d+))?", Pattern.CASE_INSENSITIVE);
        Matcher limitMatcher = limitPattern.matcher(sql);
        if (limitMatcher.find()) {
            String offset = limitMatcher.group(2);
            String limit = limitMatcher.group(1);
            limitValue = Integer.parseInt(limit);
            offsetValue = offset != null ? Integer.parseInt(offset) : 0;
            hasOffset = offsetValue > 0;
            isDeepPagination = offsetValue > 1000;
        }
        
        return new OrderByComplexity(
            sortColumnCount, hasAsc, hasDesc, hasMixedSort, functionSortCount,
            limitValue, offsetValue, hasOffset, isDeepPagination
        );
    }

    /**
     * 统计集合操作
     */
    private int countSetOperations(String sql) {
        return countOccurrences(sql, "\\bUNION\\b") + 
               countOccurrences(sql, "\\bINTERSECT\\b") + 
               countOccurrences(sql, "\\bEXCEPT\\b");
    }

    /**
     * 统计 CTE 数量
     */
    private int countCTEs(String sql) {
        Pattern ctePattern = Pattern.compile("\\bWITH\\s+.*?\\bSELECT\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = ctePattern.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 执行深度分析
     */
    private DeepAnalysis performDeepAnalysis(String sql, String datasourceName) {
        return new DeepAnalysis(
            detectFullScanRisk(sql),
            analyzeIndexUsagePotential(sql),
            analyzeLockContention(sql),
            analyzeTempTableUsage(sql),
            analyzeFileSortRisk(sql)
        );
    }

    private boolean detectFullScanRisk(String sql) {
        // 如果没有 WHERE 条件，且表不大，可能存在全表扫描
        return !sql.contains("WHERE") && sql.contains("FROM");
    }

    private String analyzeIndexUsagePotential(String sql) {
        if (sql.contains("WHERE")) {
            // 检查 WHERE 条件是否适合索引
            if (sql.matches(".*WHERE\\s+[A-Z_]+\\s*=.*")) {
                return "HIGH";
            } else if (sql.contains("LIKE '%")) {
                return "LOW";
            }
        }
        return "MEDIUM";
    }

    private String analyzeLockContention(String sql) {
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("FOR UPDATE") || upperSql.contains("LOCK")) {
            return "HIGH";
        }
        if (upperSql.contains("UPDATE") || upperSql.contains("DELETE")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean analyzeTempTableUsage(String sql) {
        return sql.contains("GROUP BY") || sql.contains("DISTINCT") || 
               sql.contains("ORDER BY") && sql.contains("JOIN");
    }

    private boolean analyzeFileSortRisk(String sql) {
        return sql.contains("ORDER BY") && sql.contains("LIMIT") && 
               !sql.contains("WHERE");
    }

    /**
     * 计算总体复杂度分数
     */
    private double calculateOverallScore(ComplexityMetrics metrics) {
        double score = 0.0;
        
        // 基础分数
        score += Math.pow(metrics.tableCount, 1.5) * 2;
        
        // JOIN 复杂度
        score += metrics.joinMetrics.totalJoins * 3;
        score += metrics.joinMetrics.joinConditionsComplexity * 1.5;
        
        // 子查询
        score += metrics.subqueryMetrics.totalSubqueries * 4;
        score += Math.pow(metrics.subqueryMetrics.maxNestingDepth, 2) * 2;
        
        // WHERE 条件
        score += metrics.whereComplexity.conditionCount;
        score += metrics.whereComplexity.functionCount * 2;
        
        // SELECT 列表
        score += metrics.selectComplexity.columnCount * 0.5;
        score += metrics.selectComplexity.aggregateCount * 2;
        
        // CTE
        score += metrics.cteCount * 2;
        
        // 长度惩罚
        score += metrics.lengthPenalty;
        
        return Math.min(Math.round(score * 10.0) / 10.0, 100.0);
    }

    /**
     * 生成优化建议
     */
    private List<String> generateRecommendations(ComplexityMetrics metrics, DeepAnalysis deepAnalysis) {
        List<String> recommendations = new ArrayList<>();
        
        if (metrics.tableCount > 5) {
            recommendations.add("考虑将查询拆分为多个较小的查询，减少表连接数量");
        }
        
        if (metrics.joinMetrics.totalJoins > 3) {
            recommendations.add("过多的 JOIN 可能导致性能下降，考虑物化中间结果");
        }
        
        if (metrics.subqueryMetrics.totalSubqueries > 2) {
            recommendations.add("子查询过多，考虑使用 CTE 或临时表");
        }
        
        if (metrics.subqueryMetrics.maxNestingDepth > 2) {
            recommendations.add("子查询嵌套过深，建议使用 CTE  flatten 结构");
        }
        
        if (metrics.whereComplexity.wildcardLikeCount > 0) {
            recommendations.add("避免在 LIKE 模式开头使用通配符，这会导致索引失效");
        }
        
        if (metrics.selectComplexity.isDistinct && metrics.joinMetrics.totalJoins > 0) {
            recommendations.add("DISTINCT 可能隐藏重复数据问题，检查 JOIN 条件是否正确");
        }
        
        if (metrics.orderByComplexity.isDeepPagination) {
            recommendations.add("深度分页性能差，考虑使用游标分页或延迟关联");
        }
        
        if (deepAnalysis.fullScanRisk) {
            recommendations.add("可能触发全表扫描，考虑添加合适的索引");
        }
        
        if (deepAnalysis.tempTableRisk) {
            recommendations.add("可能使用临时表，注意内存和磁盘使用情况");
        }
        
        if (deepAnalysis.fileSortRisk) {
            recommendations.add("可能使用文件排序，考虑添加覆盖索引");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("查询复杂度合理，无明显优化建议");
        }
        
        return recommendations;
    }

    /**
     * 确定优化优先级
     */
    private String determinePriority(double score) {
        if (score >= 70) {
            return "CRITICAL";
        } else if (score >= 50) {
            return "HIGH";
        } else if (score >= 30) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * 复杂度分析结果
     */
    public record ComplexityAnalysisResult(
        String originalSql,
        String normalizedSql,
        ComplexityMetrics metrics,
        DeepAnalysis deepAnalysis,
        double overallScore,
        String priority,
        List<String> recommendations
    ) {
        public static ComplexityAnalysisResult error(String message) {
            return new ComplexityAnalysisResult(
                null, null, null, null, 0.0, "ERROR", List.of(message)
            );
        }
    }

    /**
     * 复杂度指标
     */
    public record ComplexityMetrics(
        int tableCount,
        JoinMetrics joinMetrics,
        SubqueryMetrics subqueryMetrics,
        WhereComplexity whereComplexity,
        SelectComplexity selectComplexity,
        GroupByComplexity groupByComplexity,
        OrderByComplexity orderByComplexity,
        int setOperations,
        int cteCount,
        double lengthPenalty
    ) {
        public ComplexityMetrics() {
            this(0, new JoinMetrics(), new SubqueryMetrics(), new WhereComplexity(),
                 new SelectComplexity(), new GroupByComplexity(), new OrderByComplexity(),
                 0, 0, 0.0);
        }
    }

    /**
     * JOIN 指标
     */
    public record JoinMetrics(
        int innerJoinCount,
        int leftJoinCount,
        int rightJoinCount,
        int fullJoinCount,
        int crossJoinCount,
        int implicitJoinCount,
        int totalJoins,
        int joinConditionsComplexity
    ) {
        public JoinMetrics() {
            this(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * 子查询指标
     */
    public record SubqueryMetrics(
        int whereSubqueryCount,
        int fromSubqueryCount,
        int joinSubqueryCount,
        int selectSubqueryCount,
        int totalSubqueries,
        int maxNestingDepth
    ) {
        public SubqueryMetrics() {
            this(0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * WHERE 条件复杂度
     */
    public record WhereComplexity(
        int conditionCount,
        int notConditionCount,
        int inClauseCount,
        List<Integer> inClauseElements,
        int betweenCount,
        int likeCount,
        int wildcardLikeCount,
        int regexCount,
        int functionCount,
        int existsCount,
        int nullCheckCount,
        int caseCount
    ) {
        public WhereComplexity() {
            this(0, 0, 0, new ArrayList<>(), 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * SELECT 列表复杂度
     */
    public record SelectComplexity(
        int columnCount,
        boolean isDistinct,
        int distinctCount,
        int aggregateCount,
        int aliasCount,
        int caseCount,
        int subqueryCount,
        int functionCount,
        int computedColumnCount
    ) {
        public SelectComplexity() {
            this(0, false, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * GROUP BY 复杂度
     */
    public record GroupByComplexity(
        int groupingColumnCount,
        boolean hasGroupingSets,
        boolean hasRollup,
        boolean hasCube,
        int havingConditionCount
    ) {
        public GroupByComplexity() {
            this(1, false, false, false, 0);
        }
    }

    /**
     * ORDER BY 复杂度
     */
    public record OrderByComplexity(
        int sortColumnCount,
        boolean hasAsc,
        boolean hasDesc,
        boolean hasMixedSort,
        int functionSortCount,
        int limitValue,
        int offsetValue,
        boolean hasOffset,
        boolean isDeepPagination
    ) {
        public OrderByComplexity() {
            this(1, false, false, false, 0, 0, 0, false, false);
        }
    }

    /**
     * 深度分析结果
     */
    public record DeepAnalysis(
        boolean fullScanRisk,
        String indexUsagePotential,
        String lockContentionRisk,
        boolean tempTableRisk,
        boolean fileSortRisk
    ) {
        public DeepAnalysis() {
            this(false, "MEDIUM", "LOW", false, false);
        }
    }

    /**
     * 获取工具元数据
     */
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "analyze_sql_complexity");
        metadata.put("description", "分析 SQL 查询的复杂度，提供复杂度评分和优化建议");
        metadata.put("parameters", Map.of(
            "sql", Map.of("type", "string", "description", "SQL 语句", "required", true),
            "datasourceName", Map.of("type", "string", "description", "数据源名称（可选）", "required", false)
        ));
        return metadata;
    }
}
