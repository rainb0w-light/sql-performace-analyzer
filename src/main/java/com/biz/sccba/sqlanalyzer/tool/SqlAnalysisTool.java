package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL 分析工具
 * 分析 SQL 性能，提供优化建议
 */
@Component
public class SqlAnalysisTool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分析 SQL 性能
     *
     * @param sql            SQL 语句
     * @param datasourceName 数据源名称 (可选)
     * @param llmName        LLM 名称 (可选)
     * @return 分析结果 JSON
     */
    @Tool(name = "analyze_sql", description = "分析 SQL 性能，提供优化建议")
    public String analyzeSql(
            @ToolParam(name = "sql", description = "SQL 语句", required = true) String sql,
            @ToolParam(name = "datasourceName", description = "数据源名称 (可选)", required = false) String datasourceName,
            @ToolParam(name = "llmName", description = "LLM 名称 (可选)", required = false) String llmName) {
        System.out.println("[SqlAnalysisTool] 分析 SQL 性能 (数据源：" + datasourceName + ", LLM: " + llmName + ")");
        // 简化实现，实际应该调用分析服务
        try {
            return objectMapper.writeValueAsString(Map.of(
                "sql", sql,
                "datasource", datasourceName,
                "status", "not_implemented",
                "message", "SQL 分析功能待实现"
            ));
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 仅获取执行计划分析 (不调用 LLM)
     *
     * @param sql            SQL 语句
     * @param datasourceName 数据源名称
     * @return 执行计划分析
     */
    public ExecutionPlanAnalysis analyzeExecutionPlan(String sql, String datasourceName) {
        System.out.println("[SqlAnalysisTool] 分析执行计划 (数据源：" + datasourceName + ")");
        return new ExecutionPlanAnalysis(sql, null, new ArrayList<>());
    }

    /**
     * 执行计划分析结果
     */
    public record ExecutionPlanAnalysis(
        String sql,
        ExecutionPlan executionPlan,
        List<Object> tableStructures
    ) {}

    /**
     * 获取工具元数据
     */
    public Map<String, Object> getMetadata() {
        return Map.of(
            "name", "analyze_sql",
            "description", "分析 SQL 性能，提供优化建议",
            "parameters", Map.of(
                "sql", Map.of("type", "string", "description", "SQL 语句", "required", true),
                "datasourceName", Map.of("type", "string", "description", "数据源名称 (可选)", "required", false),
                "llmName", Map.of("type", "string", "description", "LLM 名称 (可选)", "required", false)
            )
        );
    }
}
