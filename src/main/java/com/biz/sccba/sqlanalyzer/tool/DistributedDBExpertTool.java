package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.service.TestEnvironmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 分布式数据库专家工具
 *
 * 专注于 GoldenDB 分布式特性相关的分析：
 * - 分片分布分析
 * - 跨分片查询检测
 * - 路由效率分析
 * - 全局索引 vs 局部索引建议
 *
 * 优先级：最高（GoldenDB 场景下）
 */
@Component
public class DistributedDBExpertTool {

    private final TestEnvironmentService testEnvironmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     */
    public DistributedDBExpertTool(TestEnvironmentService testEnvironmentService) {
        this.testEnvironmentService = testEnvironmentService;
    }

    /**
     * 执行分布式专家分析
     */
    @Tool(name = "distributed_db_expert_analyze", description = "分布式数据库专家分析工具，分析分片分布、跨分片查询、路由效率等")
    public String execute(
            @ToolParam(name = "datasourceName", description = "数据源名称", required = true) String datasourceName,
            @ToolParam(name = "sql", description = "要分析的 SQL 语句", required = true) String sql,
            @ToolParam(name = "tables", description = "涉及的表名列表", required = true) List<String> tables) {

        System.out.println("分布式专家分析：datasource=" + datasourceName + ", sql=" + sql);

        try {
            // 1. 分析 SQL 中的 WHERE 条件，判断是否包含分片键
            List<String> shardAnalysis = analyzeShardUsage(sql, null);

            // 2. 检测跨分片问题
            List<String> crossShardIssues = detectCrossShardIssues(sql);

            // 3. 生成分布式角度的建议
            List<String> suggestions = generateDistributedSuggestions(
                sql, shardAnalysis, crossShardIssues);

            // 4. 确定优先级和置信度
            int priority = 1;  // 分布式问题通常优先级较高
            double confidence = determineConfidence(shardAnalysis, crossShardIssues);

            DistributedAnalysisResult result = new DistributedAnalysisResult(
                sql,
                shardAnalysis,
                crossShardIssues,
                suggestions,
                priority,
                confidence
            );

            return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "result", result,
                "expertType", "DistributedDB",
                "priority", priority,
                "confidence", confidence
            ));

        } catch (Exception e) {
            System.out.println("分布式专家分析失败：" + e.getMessage());
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
     * 分析分片键使用情况
     */
    private List<String> analyzeShardUsage(String sql, String shardKey) {
        List<String> analysis = new ArrayList<>();

        String sqlLower = sql.toLowerCase();

        // 检查 WHERE 子句
        if (!sqlLower.contains("where")) {
            analysis.add("⚠️ SQL 没有 WHERE 条件，将导致全分片扫描");
            return analysis;
        }

        // 如果已知分片键，检查是否在 WHERE 中使用
        if (shardKey != null && !shardKey.isEmpty()) {
            if (sqlLower.contains(shardKey.toLowerCase())) {
                analysis.add("✅ SQL 包含分片键字段：" + shardKey);

                // 检查是否是等值查询（最优）
                if (sqlLower.contains(shardKey.toLowerCase() + " =") ||
                    sqlLower.contains("=" + shardKey.toLowerCase())) {
                    analysis.add("✅ 分片键使用等值查询，路由效率高");
                } else if (sqlLower.contains(" in ") ||
                           sqlLower.contains("between")) {
                    analysis.add("⚠️ 分片键使用范围查询，可能涉及多个分片");
                } else {
                    analysis.add("⚠️ 分片键使用了函数或表达式，可能导致路由失效");
                }
            } else {
                analysis.add("⚠️ WHERE 条件未包含分片键：" + shardKey);
                analysis.add("   这将导致跨分片查询，影响性能");
            }
        } else {
            // 未知分片键，尝试从 SQL 中推测
            analysis.add("ℹ️ 未指定分片键，无法精确分析分片路由");
            analysis.add("   建议提供 shardKey 参数以获取更准确的分析");
        }

        return analysis;
    }

    /**
     * 检测跨分片问题
     */
    private List<String> detectCrossShardIssues(String sql) {
        List<String> issues = new ArrayList<>();

        String sqlUpper = sql.toUpperCase();

        // 1. 检测 JOIN
        if (sqlUpper.contains(" JOIN ")) {
            issues.add("⚠️ 检测到 JOIN 操作，在分布式环境下可能是跨分片 JOIN");
            issues.add("   建议：确保 JOIN 条件包含分片键，或使用绑定表");
        }

        // 2. 检测子查询
        if (sqlUpper.contains("SELECT") && sqlUpper.contains("FROM") &&
            sqlUpper.indexOf("SELECT") != sqlUpper.lastIndexOf("SELECT")) {
            issues.add("⚠️ 检测到子查询，可能导致跨分片数据传输");
        }

        // 3. 检测 ORDER BY + LIMIT
        if (sqlUpper.contains("ORDER BY") && sqlUpper.contains("LIMIT")) {
            issues.add("⚠️ ORDER BY + LIMIT 在分布式环境下需要全局排序");
            issues.add("   建议：确保 ORDER BY 字段在单个分片内有序");
        }

        // 4. 检测聚合函数
        if (sqlUpper.contains("GROUP BY") ||
            sqlUpper.contains("SUM(") || sqlUpper.contains("COUNT(") ||
            sqlUpper.contains("AVG(") || sqlUpper.contains("MAX(") ||
            sqlUpper.contains("MIN(")) {
            issues.add("⚠️ 聚合查询需要在多个分片上执行后汇总");
            issues.add("   建议：确保 GROUP BY 字段包含分片键");
        }

        // 5. 检测 DISTINCT
        if (sqlUpper.contains("DISTINCT")) {
            issues.add("⚠️ DISTINCT 需要跨分片去重，影响性能");
        }

        // 6. 检测 UNION
        if (sqlUpper.contains("UNION")) {
            issues.add("⚠️ UNION 操作在分布式环境下开销较大");
        }

        return issues;
    }

    /**
     * 生成分布式角度的建议
     */
    private List<String> generateDistributedSuggestions(String sql,
                                                         List<String> shardAnalysis,
                                                         List<String> crossShardIssues) {
        List<String> suggestions = new ArrayList<>();

        // 根据问题生成具体建议
        boolean hasFullShardScan = shardAnalysis.stream()
            .anyMatch(a -> a.contains("全分片扫描"));

        if (hasFullShardScan) {
            suggestions.add("🔧 优化建议：在 WHERE 条件中添加分片键过滤");
        }

        boolean hasCrossShardJoin = crossShardIssues.stream()
            .anyMatch(i -> i.contains("JOIN"));

        if (hasCrossShardJoin) {
            suggestions.add("🔧 优化建议：使用绑定表或将 JOIN 改为应用层组装");
            suggestions.add("🔧 或者：确保关联字段在相同分片上（使用相同分片键）");
        }

        boolean hasAggregation = crossShardIssues.stream()
            .anyMatch(i -> i.contains("聚合"));

        if (hasAggregation) {
            suggestions.add("🔧 优化建议：使用本地聚合 + 全局聚合的两阶段聚合策略");
        }

        boolean hasOrderByLimit = crossShardIssues.stream()
            .anyMatch(i -> i.contains("ORDER BY"));

        if (hasOrderByLimit) {
            suggestions.add("🔧 优化建议：在分片键字段上排序，避免全局排序");
        }

        // 如果没有发现问题
        if (suggestions.isEmpty() && shardAnalysis.stream().anyMatch(a -> a.startsWith("✅"))) {
            suggestions.add("✅ SQL 在分布式环境下执行效率较好");
        }

        return suggestions;
    }

    /**
     * 确定置信度
     */
    private double determineConfidence(List<String> shardAnalysis,
                                        List<String> crossShardIssues) {
        double confidence = 0.6;

        // 有分片键信息时置信度更高
        if (shardAnalysis.stream().anyMatch(a -> a.contains("分片键"))) {
            confidence += 0.2;
        }

        // 检测到明确问题时置信度高
        if (!crossShardIssues.isEmpty()) {
            confidence += 0.1;
        }

        return Math.min(confidence, 0.95);
    }

    /**
     * 分析表的分片分布（额外功能）
     */
    public Map<String, Object> analyzeShardDistribution(String datasourceName,
                                                         String tableName,
                                                         String shardKey) {
        Map<String, Object> result = new HashMap<>();

        try {
            JdbcTemplate jdbcTemplate = testEnvironmentService.getJdbcTemplate(datasourceName);

            // 查询分片键的值分布
            String distributionSql = String.format("""
                SELECT `%1$s`, COUNT(*) as cnt
                FROM `%2$s`
                GROUP BY `%1$s`
                ORDER BY cnt DESC
                LIMIT 100
                """, shardKey, tableName);

            List<Map<String, Object>> distribution = jdbcTemplate.queryForList(distributionSql);
            result.put("distribution", distribution);
            result.put("status", "success");

        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 分布式专家分析结果
     */
    public record DistributedAnalysisResult(
        String sql,
        List<String> shardAnalysis,
        List<String> crossShardIssues,
        List<String> suggestions,
        int priority,
        double confidence
    ) {}
}
