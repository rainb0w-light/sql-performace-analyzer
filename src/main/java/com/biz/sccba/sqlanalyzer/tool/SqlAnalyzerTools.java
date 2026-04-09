package com.biz.sccba.sqlanalyzer.tool;

import com.biz.sccba.sqlanalyzer.memory.SessionMemoryService;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisResult;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisSession;
import com.biz.sccba.sqlanalyzer.model.agent.BusinessSemantics;
import com.biz.sccba.sqlanalyzer.model.ColumnStatistics;
import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.ParsedSqlQuery;
import com.biz.sccba.sqlanalyzer.model.TableStructure;
import com.biz.sccba.sqlanalyzer.model.websocket.ServerMessageType;
import com.biz.sccba.sqlanalyzer.model.websocket.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 分析工具集
 * 提供统一的工具调用入口
 */
@Component
public class SqlAnalyzerTools {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private MyBatisParserTool myBatisParserTool;

    @Autowired(required = false)
    private TableStructureTool tableStructureTool;

    @Autowired(required = false)
    private ColumnStatsTool columnStatsTool;

    @Autowired(required = false)
    private ExecutionPlanTool executionPlanTool;

    @Autowired(required = false)
    private SqlFillerTool sqlFillerTool;

    @Autowired(required = false)
    private SqlAnalysisTool sqlAnalysisTool;

    @Autowired(required = false)
    private KnowledgeQueryTool knowledgeQueryTool;

    @Autowired(required = false)
    private CreateIndexTool createIndexTool;

    @Autowired(required = false)
    private DropIndexTool dropIndexTool;

    @Autowired(required = false)
    private AlterTableTool alterTableTool;

    @Autowired(required = false)
    private InnoDBExpertTool innoDBExpertTool;

    @Autowired(required = false)
    private DistributedDBExpertTool distributedDBExpertTool;

    @Autowired(required = false)
    private SqlOptimizerExpertTool sqlOptimizerExpertTool;

    @Autowired(required = false)
    private SqlQueryComplexityAnalyzer sqlQueryComplexityAnalyzer;

    @Autowired(required = false)
    private IndexUsageAnalyzer indexUsageAnalyzer;

    @Autowired(required = false)
    private SessionMemoryService sessionMemoryService;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    // 当前会话 ID 的 ThreadLocal 存储（用于工具调用时记录）
    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    /**
     * 设置当前会话 ID（在调用工具前设置）
     */
    public static void setCurrentSessionId(String sessionId) {
        CURRENT_SESSION_ID.set(sessionId);
    }

    /**
     * 清除当前会话 ID
     */
    public static void clearCurrentSessionId() {
        CURRENT_SESSION_ID.remove();
    }

    /**
     * 获取当前会话 ID
     */
    public static String getCurrentSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    /**
     * 获取所有可用工具名称
     */
    public List<String> getAvailableTools() {
        return List.of(
            "parse_mybatis_xml",
            "parse_mybatis_file",
            "get_table_structure",
            "get_table_indexes",
            "collect_column_stats",
            "get_execution_plan",
            "fill_test_conditions",
            "analyze_sql",
            "get_business_semantics",
            "enrich_business_semantics",
            "query_knowledge",
            "create_index",
            "drop_index",
            "alter_table",
            "innodb_expert_analyze",
            "distributed_db_expert_analyze",
            "sql_optimizer_analyze",
            "analyze_sql_complexity",
            "analyze_index_usage"
        );
    }

    /**
     * 执行工具调用
     */
    public ToolResult executeTool(String toolName, Map<String, Object> parameters) {
        String sessionId = getCurrentSessionId();
        System.out.println("[SqlAnalyzerTools] 执行工具：" + toolName + " 会话 ID: " + sessionId + " 参数：" + parameters);
        long startTime = System.currentTimeMillis();

        // 发送工具调用开始消息
        if (sessionId != null && messagingTemplate != null) {
            try {
                WebSocketMessage toolCallMsg = new WebSocketMessage(
                    ServerMessageType.SESSION_UPDATED.name(),
                    sessionId
                );
                toolCallMsg.addPayload("type", "TOOL_CALL");
                toolCallMsg.addPayload("toolName", toolName);
                toolCallMsg.addPayload("parameters", parameters);
                toolCallMsg.addPayload("status", "STARTED");
                messagingTemplate.convertAndSend("/topic/session/" + sessionId, toolCallMsg);
                System.out.println("[SqlAnalyzerTools] 已发送工具调用消息：" + toolName);
            } catch (Exception e) {
                System.out.println("[SqlAnalyzerTools] 发送工具调用消息失败：" + e.getMessage());
            }
        }

        try {
            Object result = switch (toolName) {
                case "parse_mybatis_xml" -> {
                    String xmlContent = (String) parameters.get("xmlContent");
                    String namespace = (String) parameters.get("namespace");
                    yield myBatisParserTool.parseFromContent(xmlContent, namespace);
                }
                case "parse_mybatis_file" -> {
                    String filePath = (String) parameters.get("filePath");
                    yield myBatisParserTool.parseFromFile(filePath);
                }
                case "get_table_structure" -> {
                    String tableName = (String) parameters.get("tableName");
                    String datasource = (String) parameters.get("datasourceName");
                    yield tableStructureTool.getTableStructure(tableName, datasource);
                }
                case "get_table_indexes" -> {
                    String tableName = (String) parameters.get("tableName");
                    String datasource = (String) parameters.get("datasourceName");
                    yield tableStructureTool.getTableStructure(tableName, datasource);
                }
                case "collect_column_stats" -> {
                    String tableName = (String) parameters.get("tableName");
                    String datasource = (String) parameters.get("datasourceName");
                    yield columnStatsTool.collectStatistics(tableName, datasource);
                }
                case "get_execution_plan" -> {
                    String sql = (String) parameters.get("sql");
                    String datasource = (String) parameters.get("datasourceName");
                    yield executionPlanTool.getExecutionPlan(sql, datasource);
                }
                case "fill_test_conditions" -> {
                    String sql = (String) parameters.get("sql");
                    String datasource = (String) parameters.get("datasourceName");
                    yield sqlFillerTool.fillTestConditions(sql, datasource);
                }
                case "analyze_sql" -> {
                    String sql = (String) parameters.get("sql");
                    String datasource = (String) parameters.get("datasourceName");
                    String llm = (String) parameters.get("llmName");
                    yield sqlAnalysisTool.analyzeSql(sql, datasource, llm);
                }
                case "get_business_semantics" -> {
                    String tableName = (String) parameters.get("tableName");
                    throw new UnsupportedOperationException("业务语义功能待实现");
                }
                case "enrich_business_semantics" -> {
                    throw new UnsupportedOperationException("业务语义功能待实现");
                }
                case "query_knowledge" -> {
                    String query = (String) parameters.get("query");
                    String category = (String) parameters.get("category");
                    yield knowledgeQueryTool.query(query, category);
                }
                case "create_index" -> {
                    String datasource = (String) parameters.get("datasource");
                    String schema = (String) parameters.get("schema");
                    String tableName = (String) parameters.get("tableName");
                    String indexName = (String) parameters.get("indexName");
                    String columns = (String) parameters.get("columns");
                    Boolean unique = (Boolean) parameters.get("unique");
                    String reqSessionId = (String) parameters.get("sessionId");
                    yield createIndexTool.createIndex(
                        datasource, schema, tableName, indexName, columns,
                        unique != null && unique, reqSessionId
                    );
                }
                case "drop_index" -> {
                    String datasource = (String) parameters.get("datasource");
                    String schema = (String) parameters.get("schema");
                    String tableName = (String) parameters.get("tableName");
                    String indexName = (String) parameters.get("indexName");
                    String reqSessionId = (String) parameters.get("sessionId");
                    yield dropIndexTool.dropIndex(datasource, schema, tableName, indexName, reqSessionId);
                }
                case "alter_table" -> {
                    String datasource = (String) parameters.get("datasource");
                    String schema = (String) parameters.get("schema");
                    String tableName = (String) parameters.get("tableName");
                    String alterClause = (String) parameters.get("alterClause");
                    String reqSessionId = (String) parameters.get("sessionId");
                    yield alterTableTool.alterTable(datasource, schema, tableName, alterClause, reqSessionId);
                }
                case "innodb_expert_analyze" -> {
                    String datasource = (String) parameters.get("datasourceName");
                    String sql = (String) parameters.get("sql");
                    List<String> tables = (List<String>) parameters.get("tables");
                    yield innoDBExpertTool.execute(datasource, sql, tables);
                }
                case "distributed_db_expert_analyze" -> {
                    String datasource = (String) parameters.get("datasourceName");
                    String sql = (String) parameters.get("sql");
                    List<String> tables = (List<String>) parameters.get("tables");
                    yield distributedDBExpertTool.execute(datasource, sql, tables);
                }
                case "sql_optimizer_analyze" -> {
                    String datasource = (String) parameters.get("datasourceName");
                    String sql = (String) parameters.get("sql");
                    List<String> tables = (List<String>) parameters.get("tables");
                    yield sqlOptimizerExpertTool.execute(datasource, sql, tables);
                }
                case "analyze_sql_complexity" -> {
                    String sql = (String) parameters.get("sql");
                    String datasource = (String) parameters.get("datasourceName");
                    yield sqlQueryComplexityAnalyzer.analyzeSqlComplexity(sql, datasource);
                }
                case "analyze_index_usage" -> {
                    String sql = (String) parameters.get("sql");
                    String datasource = (String) parameters.get("datasourceName");
                    String tableName = (String) parameters.get("tableName");
                    yield indexUsageAnalyzer.analyzeIndexUsage(sql, datasource, tableName);
                }
                default -> throw new IllegalArgumentException("未知的工具：" + toolName);
            };

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("工具执行成功：" + toolName + " 耗时：" + duration + "ms");

            // 发送工具调用完成消息并记录到会话
            if (sessionId != null && messagingTemplate != null && sessionMemoryService != null) {
                try {
                    // 发送完成消息
                    WebSocketMessage toolResultMsg = new WebSocketMessage(
                        ServerMessageType.SESSION_UPDATED.name(),
                        sessionId
                    );
                    toolResultMsg.addPayload("type", "TOOL_CALL_RESULT");
                    toolResultMsg.addPayload("toolName", toolName);
                    toolResultMsg.addPayload("success", true);
                    toolResultMsg.addPayload("duration", duration);
                    messagingTemplate.convertAndSend("/topic/session/" + sessionId, toolResultMsg);

                    // 记录到会话历史
                    AnalysisSession.ToolCallRecord record = new AnalysisSession.ToolCallRecord();
                    record.setToolName(toolName);
                    record.setParameters(parameters);
                    record.setResult(result != null ? result.toString() : "null");
                    record.setDuration(duration);
                    sessionMemoryService.addToolCall(sessionId, record);

                    System.out.println("[SqlAnalyzerTools] 工具调用已记录到会话：" + toolName);
                } catch (Exception e) {
                    System.out.println("[SqlAnalyzerTools] 记录工具调用失败：" + e.getMessage());
                }
            }

            return ToolResult.success(result, duration);

        } catch (Exception e) {
            System.out.println("工具执行失败：" + toolName + " 错误：" + e.getMessage());

            // 发送工具调用失败消息
            if (sessionId != null && messagingTemplate != null) {
                try {
                    WebSocketMessage toolErrorMsg = new WebSocketMessage(
                        ServerMessageType.SESSION_UPDATED.name(),
                        sessionId
                    );
                    toolErrorMsg.addPayload("type", "TOOL_CALL_ERROR");
                    toolErrorMsg.addPayload("toolName", toolName);
                    toolErrorMsg.addPayload("error", e.getMessage());
                    messagingTemplate.convertAndSend("/topic/session/" + sessionId, toolErrorMsg);
                } catch (Exception ex) {
                    System.out.println("[SqlAnalyzerTools] 发送工具错误消息失败：" + ex.getMessage());
                }
            }

            return ToolResult.failure("工具执行失败：" + toolName, e);
        }
    }

    /**
     * 获取工具描述
     */
    public Map<String, String> getToolDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("parse_mybatis_xml", "解析 MyBatis Mapper XML 内容");
        descriptions.put("parse_mybatis_file", "解析 MyBatis Mapper XML 文件");
        descriptions.put("get_table_structure", "获取数据库表结构信息");
        descriptions.put("get_table_indexes", "获取表的索引信息");
        descriptions.put("collect_column_stats", "收集表的列统计信息");
        descriptions.put("get_execution_plan", "获取 SQL 执行计划");
        descriptions.put("fill_test_conditions", "填充 MyBatis test 标签条件");
        descriptions.put("analyze_sql", "分析 SQL 性能");
        descriptions.put("get_business_semantics", "获取表的业务语义");
        descriptions.put("enrich_business_semantics", "补充表的业务语义");
        descriptions.put("query_knowledge", "查询技术知识库");
        descriptions.put("create_index", "创建数据库索引（需要用户确认）");
        descriptions.put("drop_index", "删除数据库索引（需要用户确认）");
        descriptions.put("alter_table", "修改表结构（需要用户确认）");
        descriptions.put("innodb_expert_analyze", "InnoDB 存储引擎专家分析表结构、索引和锁");
        descriptions.put("distributed_db_expert_analyze", "分布式数据库专家分析分片分布和跨分片查询");
        descriptions.put("sql_optimizer_analyze", "SQL 优化专家分析查询语句，提供查询重写和索引覆盖建议");
        descriptions.put("analyze_sql_complexity", "分析 SQL 查询的复杂度，提供复杂度评分和优化建议");
        descriptions.put("analyze_index_usage", "分析 SQL 查询中的索引使用情况，识别索引缺失和使用不当的问题");
        return descriptions;
    }

    /**
     * 获取所有工具的元数据（用于 AgentScope 集成）
     */
    public Map<String, Object> getAllToolsMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tools", List.of(
            getToolMetadata("get_table_structure", tableStructureTool),
            getToolMetadata("collect_column_stats", columnStatsTool),
            getToolMetadata("get_execution_plan", executionPlanTool)
        ));
        return metadata;
    }

    /**
     * 获取工具元数据
     */
    private Map<String, Object> getToolMetadata(String name, Object tool) {
        if (tool != null) {
            if (tool instanceof BaseTool baseTool) {
                return baseTool.getMetadata().toMap();
            }
            // 对于非 BaseTool 类型的工具，返回基本信息
            Map<String, Object> basicMetadata = new HashMap<>();
            basicMetadata.put("name", name);
            basicMetadata.put("description", tool.getClass().getSimpleName() + " - 工具");
            basicMetadata.put("parameters", Map.of());
            return basicMetadata;
        }
        Map<String, Object> basicMetadata = new HashMap<>();
        basicMetadata.put("name", name);
        basicMetadata.put("description", "工具未初始化");
        basicMetadata.put("parameters", Map.of());
        return basicMetadata;
    }
}
