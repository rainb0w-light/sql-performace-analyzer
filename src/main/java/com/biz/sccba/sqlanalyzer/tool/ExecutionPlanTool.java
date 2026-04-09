package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.service.SqlExecutionPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 执行计划工具
 * 获取和分析 SQL 执行计划
 */
@Component
public class ExecutionPlanTool {

    private final SqlExecutionPlanService executionPlanService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数
     */
    public ExecutionPlanTool(SqlExecutionPlanService executionPlanService) {
        this.executionPlanService = executionPlanService;
    }

    /**
     * 获取 SQL 执行计划
     *
     * @param sql            SQL 语句
     * @param datasourceName 数据源名称 (可选)
     * @return 执行计划 JSON
     */
    @Tool(name = "get_execution_plan", description = "获取 SQL 执行计划，分析查询性能和优化空间")
    public String getExecutionPlan(
            @ToolParam(name = "sql", description = "SQL 语句", required = true) String sql,
            @ToolParam(name = "datasourceName", description = "数据源名称 (可选)", required = false) String datasourceName) {
        System.out.println("[ExecutionPlanTool] 获取执行计划 (数据源：" + datasourceName + ")");
        try {
            ExecutionPlan plan = executionPlanService.getExecutionPlan(sql, datasourceName);
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 解析 SQL 中的表名
     *
     * @param sql SQL 语句
     * @return 表名列表
     */
    public List<String> parseTableNames(String sql) {
        System.out.println("[ExecutionPlanTool] 解析 SQL 表名");
        return executionPlanService.parseTableNames(sql);
    }

    /**
     * 获取工具元数据
     */
    public Map<String, Object> getMetadata() {
        return Map.of(
            "name", "get_execution_plan",
            "description", "获取 SQL 执行计划，分析查询性能和优化空间",
            "parameters", Map.of(
                "sql", Map.of("type", "string", "description", "SQL 语句", "required", true),
                "datasourceName", Map.of("type", "string", "description", "数据源名称 (可选)", "required", false)
            )
        );
    }
}
