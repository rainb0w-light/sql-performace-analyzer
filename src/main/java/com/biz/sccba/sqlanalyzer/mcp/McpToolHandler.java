package com.biz.sccba.sqlanalyzer.mcp;

import com.biz.sccba.sqlanalyzer.service.*;
import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.TableStructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP工具处理器
 * 处理MCP工具调用请求
 */
@Component
public class McpToolHandler {

    private static final Logger logger = LoggerFactory.getLogger(McpToolHandler.class);

    @Autowired
    private SqlExecutionPlanService sqlExecutionPlanService;

    @Autowired(required = false)
    private MyBatisConfigurationParserService myBatisConfigurationParserService;

    @Autowired(required = false)
    private SqlParameterReplacerService sqlParameterReplacerService;

    @Autowired(required = false)
    private AiClientService aiClientService;

    @Autowired(required = false)
    private LlmManagerService llmManagerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理工具调用
     */
    public McpProtocolModels.ToolCallResult handleToolCall(String toolName, Map<String, Object> params) {
        logger.info("处理MCP工具调用: {}, 参数: {}", toolName, params);

        try {
            switch (toolName) {
                case "get_table_structure":
                    return handleGetTableStructure(params);
                case "get_execution_plan":
                    return handleGetExecutionPlan(params);
                case "get_table_indexes":
                    return handleGetTableIndexes(params);
                case "get_table_queries":
                    return handleGetTableQueries(params);
                case "analyze_sql_performance":
                    return handleAnalyzeSqlPerformance(params);
                case "analyze_table_queries":
                    return handleAnalyzeTableQueries(params);
                default:
                    return createErrorResult("未知的工具: " + toolName);
            }
        } catch (Exception e) {
            logger.error("处理工具调用失败: " + toolName, e);
            return createErrorResult("处理失败: " + e.getMessage());
        }
    }

    /**
     * 获取表结构
     */
    private McpProtocolModels.ToolCallResult handleGetTableStructure(Map<String, Object> params) {
        try {
            String tableName = (String) params.get("tableName");
            String datasourceName = (String) params.get("datasourceName");

            if (tableName == null || tableName.trim().isEmpty()) {
                return createErrorResult("tableName参数不能为空");
            }

            // 构建一个简单的SQL来获取表结构
            String sql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(sql, datasourceName);

            if (structures.isEmpty()) {
                return createErrorResult("未找到表: " + tableName);
            }

            TableStructure structure = structures.get(0);
            String jsonResult = objectMapper.writeValueAsString(structure);

            return createSuccessResult(jsonResult);
        } catch (Exception e) {
            logger.error("获取表结构失败", e);
            return createErrorResult("获取表结构失败: " + e.getMessage());
        }
    }

    /**
     * 获取执行计划
     */
    private McpProtocolModels.ToolCallResult handleGetExecutionPlan(Map<String, Object> params) {
        try {
            String sql = (String) params.get("sql");
            String datasourceName = (String) params.get("datasourceName");
            Boolean replaceParameters = params.get("replaceParameters") != null ? 
                Boolean.parseBoolean(params.get("replaceParameters").toString()) : true;

            if (sql == null || sql.trim().isEmpty()) {
                return createErrorResult("sql参数不能为空");
            }

            // 如果需要替换参数，先替换占位符
            String executableSql = sql;
            if (replaceParameters && sql.contains("#{") && sqlParameterReplacerService != null) {
                try {
                    executableSql = sqlParameterReplacerService.replaceParametersSmart(sql, datasourceName);
                } catch (Exception e) {
                    logger.warn("替换SQL参数失败，使用原始SQL: {}", e.getMessage());
                }
            }

            ExecutionPlan plan = sqlExecutionPlanService.getExecutionPlan(executableSql, datasourceName);
            if (plan == null) {
                return createErrorResult("无法获取执行计划");
            }

            String jsonResult = objectMapper.writeValueAsString(plan);
            return createSuccessResult(jsonResult);
        } catch (Exception e) {
            logger.error("获取执行计划失败", e);
            return createErrorResult("获取执行计划失败: " + e.getMessage());
        }
    }

    /**
     * 获取表索引
     */
    private McpProtocolModels.ToolCallResult handleGetTableIndexes(Map<String, Object> params) {
        try {
            String tableName = (String) params.get("tableName");
            String datasourceName = (String) params.get("datasourceName");

            if (tableName == null || tableName.trim().isEmpty()) {
                return createErrorResult("tableName参数不能为空");
            }

            String sql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(sql, datasourceName);

            if (structures.isEmpty()) {
                return createErrorResult("未找到表: " + tableName);
            }

            TableStructure structure = structures.get(0);
            List<TableStructure.IndexInfo> indexes = structure.getIndexes();

            String jsonResult = objectMapper.writeValueAsString(indexes);
            return createSuccessResult(jsonResult);
        } catch (Exception e) {
            logger.error("获取表索引失败", e);
            return createErrorResult("获取表索引失败: " + e.getMessage());
        }
    }

    /**
     * 获取表相关的所有查询
     */
    private McpProtocolModels.ToolCallResult handleGetTableQueries(Map<String, Object> params) {
        try {
            String tableName = (String) params.get("tableName");

            if (tableName == null || tableName.trim().isEmpty()) {
                return createErrorResult("tableName参数不能为空");
            }

            if (myBatisConfigurationParserService == null) {
                return createErrorResult("MyBatis解析服务未启用");
            }

            List<Map<String, Object>> queries = myBatisConfigurationParserService.getQueriesByTable(tableName);
            String jsonResult = objectMapper.writeValueAsString(queries);

            return createSuccessResult(jsonResult);
        } catch (Exception e) {
            logger.error("获取表查询失败", e);
            return createErrorResult("获取表查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建成功结果
     */
    private McpProtocolModels.ToolCallResult createSuccessResult(String text) {
        McpProtocolModels.ToolCallResult result = new McpProtocolModels.ToolCallResult();
        McpProtocolModels.ContentItem contentItem = new McpProtocolModels.ContentItem();
        contentItem.setType("text");
        contentItem.setText(text);
        result.setContent(Collections.singletonList(contentItem));
        result.setIsError(false);
        return result;
    }

    /**
     * 创建错误结果
     */
    private McpProtocolModels.ToolCallResult createErrorResult(String message) {
        McpProtocolModels.ToolCallResult result = new McpProtocolModels.ToolCallResult();
        McpProtocolModels.ContentItem contentItem = new McpProtocolModels.ContentItem();
        contentItem.setType("text");
        contentItem.setText("错误: " + message);
        result.setContent(Collections.singletonList(contentItem));
        result.setIsError(true);
        return result;
    }

    /**
     * 分析SQL性能（调用大模型）
     */
    private McpProtocolModels.ToolCallResult handleAnalyzeSqlPerformance(Map<String, Object> params) {
        try {
            String sql = (String) params.get("sql");
            String datasourceName = (String) params.get("datasourceName");
            String llmName = (String) params.get("llmName");

            if (sql == null || sql.trim().isEmpty()) {
                return createErrorResult("sql参数不能为空");
            }

            if (aiClientService == null || llmManagerService == null) {
                return createErrorResult("AI服务未启用");
            }

            // 获取执行计划
            ExecutionPlan plan = sqlExecutionPlanService.getExecutionPlan(sql, datasourceName);
            if (plan == null) {
                return createErrorResult("无法获取执行计划");
            }

            // 获取表结构
            List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(sql, datasourceName);

            // 格式化执行计划和表结构
            String executionPlanStr = formatExecutionPlan(plan);
            String tableStructuresStr = formatTableStructures(structures);

            // 调用AI模型进行分析
            String analysisResult = aiClientService.analyzeSqlPerformance(
                    sql, executionPlanStr, tableStructuresStr, llmName);

            return createSuccessResult(analysisResult);
        } catch (Exception e) {
            logger.error("分析SQL性能失败", e);
            return createErrorResult("分析SQL性能失败: " + e.getMessage());
        }
    }

    /**
     * 分析表的所有查询（调用大模型）
     */
    private McpProtocolModels.ToolCallResult handleAnalyzeTableQueries(Map<String, Object> params) {
        try {
            String tableName = (String) params.get("tableName");
            String datasourceName = (String) params.get("datasourceName");
            String llmName = (String) params.get("llmName");

            if (tableName == null || tableName.trim().isEmpty()) {
                return createErrorResult("tableName参数不能为空");
            }


            if (aiClientService == null || llmManagerService == null) {
                return createErrorResult("AI服务未启用");
            }

            // 获取表的所有查询
            List<Map<String, Object>> queries = myBatisConfigurationParserService.getQueriesByTable(tableName);
            if (queries.isEmpty()) {
                return createErrorResult("未找到表 " + tableName + " 相关的SQL查询");
            }

            // 获取表结构
            String testSql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(testSql, datasourceName);
            TableStructure tableStructure = structures.isEmpty() ? null : structures.get(0);

            // 构建分析上下文
            StringBuilder context = new StringBuilder();
            context.append("表名: ").append(tableName).append("\n\n");
            
            if (tableStructure != null) {
                context.append("表结构:\n");
                if (tableStructure.getColumns() != null) {
                    context.append("列信息:\n");
                    for (TableStructure.ColumnInfo col : tableStructure.getColumns()) {
                        context.append(String.format("  - %s (%s)\n", col.getColumnName(), col.getDataType()));
                    }
                }
                if (tableStructure.getIndexes() != null && !tableStructure.getIndexes().isEmpty()) {
                    context.append("现有索引:\n");
                    for (TableStructure.IndexInfo idx : tableStructure.getIndexes()) {
                        context.append(String.format("  - %s (%s)\n", idx.getIndexName(), idx.getColumnName()));
                    }
                }
                context.append("\n");
            }

            context.append("SQL查询列表:\n");
            for (int i = 0; i < queries.size(); i++) {
                Map<String, Object> query = queries.get(i);
                context.append(String.format("\n查询 #%d (%s):\n", i + 1, query.get("statementId")));
                context.append("SQL: ").append(query.get("sql")).append("\n");
                if (query.get("dynamicConditions") != null) {
                    context.append("动态条件: ").append(query.get("dynamicConditions")).append("\n");
                }
            }

            // 构建提示词
            String prompt = String.format(
                "你是一位资深的MySQL性能优化专家。请基于以下信息，为表 %s 的所有SQL查询提供综合性的优化建议。\n\n" +
                "%s\n\n" +
                "请提供以下方面的优化建议：\n" +
                "1. 索引优化建议（包括需要创建的索引、复合索引等）\n" +
                "2. SQL语句优化建议（包括查询重写、JOIN优化等）\n" +
                "3. 表结构优化建议（如需要）\n" +
                "4. 其他性能优化建议\n\n" +
                "请用中文回答，建议要具体、可操作。",
                tableName, context.toString()
            );

            // 调用AI模型
            var chatClient = llmManagerService.getChatClient(llmName);
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return createSuccessResult(result);
        } catch (Exception e) {
            logger.error("分析表查询失败", e);
            return createErrorResult("分析表查询失败: " + e.getMessage());
        }
    }

    /**
     * 格式化执行计划
     */
    private String formatExecutionPlan(ExecutionPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            logger.warn("格式化执行计划失败", e);
            return plan.getRawJson() != null ? plan.getRawJson() : "无法格式化执行计划";
        }
    }

    /**
     * 格式化表结构
     */
    private String formatTableStructures(List<TableStructure> structures) {
        try {
            return objectMapper.writeValueAsString(structures);
        } catch (Exception e) {
            logger.warn("格式化表结构失败", e);
            return "无法格式化表结构";
        }
    }

    /**
     * 获取所有可用工具列表
     */
    public List<McpProtocolModels.McpTool> getAvailableTools() {
        List<McpProtocolModels.McpTool> tools = new ArrayList<>();

        // get_table_structure
        McpProtocolModels.McpTool tool1 = new McpProtocolModels.McpTool();
        tool1.setName("get_table_structure");
        tool1.setDescription("获取指定表的结构信息，包括列、数据类型、约束等");
        tool1.setInputSchema(createToolInputSchema(
            Map.of(
                "tableName", createProperty("string", "表名"),
                "datasourceName", createProperty("string", "数据源名称（可选）")
            ),
            Arrays.asList("tableName")
        ));
        tools.add(tool1);

        // get_execution_plan
        McpProtocolModels.McpTool tool2 = new McpProtocolModels.McpTool();
        tool2.setName("get_execution_plan");
        tool2.setDescription("获取SQL语句的执行计划");
        tool2.setInputSchema(createToolInputSchema(
            Map.of(
                "sql", createProperty("string", "SQL语句"),
                "datasourceName", createProperty("string", "数据源名称（可选）")
            ),
            Arrays.asList("sql")
        ));
        tools.add(tool2);

        // get_table_indexes
        McpProtocolModels.McpTool tool3 = new McpProtocolModels.McpTool();
        tool3.setName("get_table_indexes");
        tool3.setDescription("获取指定表的所有索引信息");
        tool3.setInputSchema(createToolInputSchema(
            Map.of(
                "tableName", createProperty("string", "表名"),
                "datasourceName", createProperty("string", "数据源名称（可选）")
            ),
            Arrays.asList("tableName")
        ));
        tools.add(tool3);

        // get_table_queries
        McpProtocolModels.McpTool tool4 = new McpProtocolModels.McpTool();
        tool4.setName("get_table_queries");
        tool4.setDescription("获取指定表相关的所有SQL查询（从MyBatis Mapper XML解析结果中）");
        tool4.setInputSchema(createToolInputSchema(
            Map.of(
                "tableName", createProperty("string", "表名")
            ),
            Arrays.asList("tableName")
        ));
        tools.add(tool4);

        // analyze_sql_performance
        McpProtocolModels.McpTool tool5 = new McpProtocolModels.McpTool();
        tool5.setName("analyze_sql_performance");
        tool5.setDescription("使用AI大模型分析SQL性能，返回详细的优化建议");
        tool5.setInputSchema(createToolInputSchema(
            Map.of(
                "sql", createProperty("string", "SQL语句"),
                "datasourceName", createProperty("string", "数据源名称（可选）"),
                "llmName", createProperty("string", "大模型名称（可选）")
            ),
            Arrays.asList("sql")
        ));
        tools.add(tool5);

        // analyze_table_queries
        McpProtocolModels.McpTool tool6 = new McpProtocolModels.McpTool();
        tool6.setName("analyze_table_queries");
        tool6.setDescription("使用AI大模型综合分析指定表的所有SQL查询，生成优化建议");
        tool6.setInputSchema(createToolInputSchema(
            Map.of(
                "tableName", createProperty("string", "表名"),
                "datasourceName", createProperty("string", "数据源名称（可选）"),
                "llmName", createProperty("string", "大模型名称（可选）")
            ),
            Arrays.asList("tableName")
        ));
        tools.add(tool6);

        return tools;
    }

    /**
     * 创建工具输入模式
     */
    private McpProtocolModels.ToolInputSchema createToolInputSchema(
            Map<String, McpProtocolModels.ToolProperty> properties,
            List<String> required) {
        McpProtocolModels.ToolInputSchema schema = new McpProtocolModels.ToolInputSchema();
        schema.setType("object");
        schema.setProperties(properties);
        schema.setRequired(required);
        return schema;
    }

    /**
     * 创建属性定义
     */
    private McpProtocolModels.ToolProperty createProperty(String type, String description) {
        McpProtocolModels.ToolProperty property = new McpProtocolModels.ToolProperty();
        property.setType(type);
        property.setDescription(description);
        return property;
    }
}

