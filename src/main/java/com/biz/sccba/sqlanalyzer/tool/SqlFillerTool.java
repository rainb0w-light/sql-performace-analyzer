package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.service.SqlFillerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * SQL 填充工具
 * 基于统计信息和业务语义填充 MyBatis test 标签
 */
@Component
@RequiredArgsConstructor
public class SqlFillerTool {

    private final SqlFillerService fillerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 填充测试条件
     *
     * @param sql            SQL 语句 (包含参数占位符)
     * @param datasourceName 数据源名称
     * @return 填充结果 JSON
     */
    @Tool(name = "fill_test_conditions", description = "基于统计信息填充 MyBatis test 标签条件，生成可执行的 SQL")
    public String fillTestConditions(
            @ToolParam(name = "sql", description = "SQL 语句 (包含参数占位符)", required = true) String sql,
            @ToolParam(name = "datasourceName", description = "数据源名称", required = true) String datasourceName) {
        System.out.println("[SqlFillerTool] 填充 SQL 测试条件 (数据源：" + datasourceName + ")");
        try {
            var result = fillerService.fillSql(datasourceName, sql, List.of(), "默认场景");
            FillResult fillResult = new FillResult(true, result.originalSql(), result.filledSql(), result.description(), null);
            return objectMapper.writeValueAsString(fillResult);
        } catch (Exception e) {
            System.out.println("[SqlFillerTool] 填充 SQL 测试条件失败：" + e.getMessage());
            FillResult fillResult = new FillResult(false, sql, null, null, e.getMessage());
            try {
                return objectMapper.writeValueAsString(fillResult);
            } catch (Exception ex) {
                return "{\"error\": \"" + ex.getMessage() + "\"}";
            }
        }
    }

    /**
     * 基于业务语义填充测试条件
     *
     * @param sql            SQL 语句
     * @param datasourceName 数据源名称
     * @param semantics      业务语义
     * @return 填充结果 JSON
     */
    public String fillWithSemantics(String sql, String datasourceName, Map<String, Object> semantics) {
        System.out.println("[SqlFillerTool] 基于业务语义填充 SQL 测试条件");
        // TODO: 实现基于业务语义的智能填充
        return fillTestConditions(sql, datasourceName);
    }

    /**
     * 生成多场景测试 SQL
     *
     * @param sql            SQL 语句
     * @param datasourceName 数据源名称
     * @return 多场景填充结果 JSON
     */
    public String generateMultipleScenarios(String sql, String datasourceName) {
        System.out.println("[SqlFillerTool] 生成多场景测试 SQL");
        // TODO: 实现多场景 SQL 生成
        return fillTestConditions(sql, datasourceName);
    }

    /**
     * 获取工具元数据
     */
    public Map<String, Object> getMetadata() {
        return Map.of(
            "name", "fill_test_conditions",
            "description", "基于统计信息填充 MyBatis test 标签条件，生成可执行的 SQL",
            "parameters", Map.of(
                "sql", Map.of("type", "string", "description", "SQL 语句 (包含参数占位符)", "required", true),
                "datasourceName", Map.of("type", "string", "description", "数据源名称", "required", true)
            )
        );
    }

    /**
     * 填充结果
     */
    public record FillResult(
        boolean success,
        String originalSql,
        String filledSql,
        String description,
        String errorMessage
    ) {}

    /**
     * 多场景填充结果
     */
    public record MultiScenarioResult(
        FillResult normalScenario,
        FillResult boundaryScenario,
        FillResult errorScenario
    ) {}
}
