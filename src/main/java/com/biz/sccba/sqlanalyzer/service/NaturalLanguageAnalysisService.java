package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ExecutionPlan;
import com.biz.sccba.sqlanalyzer.model.TableStructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 自然语言分析服务
 * 接收自然语言需求，识别用户意图，调用相关工具执行分析
 */
@Service
public class NaturalLanguageAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(NaturalLanguageAnalysisService.class);

    @Autowired(required = false)
    private LlmManagerService llmManagerService;

    @Autowired(required = false)
    private MyBatisMapperParserService myBatisMapperParserService;

    @Autowired
    private SqlExecutionPlanService sqlExecutionPlanService;

    @Autowired
    private TableQueryAnalysisService tableQueryAnalysisService;

    @Autowired(required = false)
    private AiClientService aiClientService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理自然语言请求
     */
    public NaturalLanguageResponse processNaturalLanguageRequest(String userRequest, String datasourceName, String llmName) {
        logger.info("处理自然语言请求: {}", userRequest);

        NaturalLanguageResponse response = new NaturalLanguageResponse();
        response.setUserRequest(userRequest);

        try {
            if (llmManagerService == null) {
                response.setError("AI服务未启用");
                return response;
            }

            // 1. 调用大模型进行意图识别和工具调用规划
            ToolCallPlan plan = identifyIntentAndPlan(userRequest, llmName);
            response.setIntent(plan.getIntent());
            response.setToolCalls(plan.getToolCalls());

            // 2. 执行工具调用
            List<ToolCallResult> results = executeToolCalls(plan, datasourceName, llmName);
            response.setToolResults(results);

            // 3. 生成最终分析结果
            String finalResult = generateFinalResult(userRequest, plan, results, llmName);
            response.setAnalysisResult(finalResult);
            response.setSuccess(true);

            return response;

        } catch (Exception e) {
            logger.error("处理自然语言请求失败", e);
            response.setSuccess(false);
            response.setError("处理失败: " + e.getMessage());
            return response;
        }
    }

    /**
     * 识别用户意图并规划工具调用
     */
    private ToolCallPlan identifyIntentAndPlan(String userRequest, String llmName) {
        try {
            String prompt = String.format(
                "你是一个SQL性能分析助手。用户的需求是：%s\n\n" +
                "请分析用户意图，并规划需要调用的工具。\n\n" +
                "可用工具：\n" +
                "1. scan_mapper_files - 扫描代码目录，查找MyBatis Mapper XML文件\n" +
                "2. parse_mapper_file - 解析MyBatis Mapper XML文件\n" +
                "3. get_table_structure - 获取表结构信息\n" +
                "4. get_table_queries - 获取表相关的所有SQL查询\n" +
                "5. analyze_table - 综合分析表的所有查询\n" +
                "6. analyze_sql - 分析单个SQL的性能\n\n" +
                "请以JSON格式返回，格式如下：\n" +
                "{\n" +
                "  \"intent\": \"用户意图描述\",\n" +
                "  \"toolCalls\": [\n" +
                "    {\"tool\": \"工具名\", \"params\": {\"参数名\": \"参数值\"}},\n" +
                "    ...\n" +
                "  ]\n" +
                "}\n\n" +
                "只返回JSON，不要其他文字。",
                userRequest
            );

            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 解析JSON结果
            ToolCallPlan plan = objectMapper.readValue(extractJson(result), ToolCallPlan.class);
            logger.info("识别意图: {}, 规划工具调用: {}", plan.getIntent(), plan.getToolCalls().size());
            return plan;

        } catch (Exception e) {
            logger.warn("意图识别失败，使用默认策略", e);
            // 如果AI识别失败，使用简单的关键词匹配
            return fallbackIntentRecognition(userRequest);
        }
    }

    /**
     * 提取JSON内容
     */
    private String extractJson(String text) {
        // 尝试提取JSON部分
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}") + 1;
        if (start >= 0 && end > start) {
            return text.substring(start, end);
        }
        return text;
    }

    /**
     * 备用意图识别（基于关键词）
     */
    private ToolCallPlan fallbackIntentRecognition(String userRequest) {
        ToolCallPlan plan = new ToolCallPlan();
        String lowerRequest = userRequest.toLowerCase();

        if (lowerRequest.contains("分析") && lowerRequest.contains("表")) {
            // 提取表名
            String tableName = extractTableName(userRequest);
            if (tableName != null) {
                plan.setIntent("分析表的所有SQL查询");
                plan.getToolCalls().add(new ToolCall("get_table_queries", Map.of("tableName", tableName)));
                plan.getToolCalls().add(new ToolCall("analyze_table", Map.of("tableName", tableName)));
            }
        } else if (lowerRequest.contains("扫描") || lowerRequest.contains("mapper") || lowerRequest.contains("xml")) {
            plan.setIntent("扫描MyBatis Mapper文件");
            plan.getToolCalls().add(new ToolCall("scan_mapper_files", Map.of()));
        } else if (lowerRequest.contains("sql") || lowerRequest.contains("查询")) {
            plan.setIntent("分析SQL性能");
            // 尝试提取SQL
            String sql = extractSql(userRequest);
            if (sql != null) {
                plan.getToolCalls().add(new ToolCall("analyze_sql", Map.of("sql", sql)));
            }
        } else {
            plan.setIntent("通用分析");
            plan.getToolCalls().add(new ToolCall("scan_mapper_files", Map.of()));
        }

        return plan;
    }

    /**
     * 从文本中提取表名
     */
    private String extractTableName(String text) {
        // 简单的表名提取逻辑
        Pattern pattern = Pattern.compile("(?:表|table)[:：]?\\s*([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从文本中提取SQL
     */
    private String extractSql(String text) {
        // 尝试提取SQL语句
        Pattern pattern = Pattern.compile("(?:SELECT|INSERT|UPDATE|DELETE)\\s+.*?(?=\\s*$|\\s*;)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(0).trim();
        }
        return null;
    }

    /**
     * 执行工具调用
     */
    private List<ToolCallResult> executeToolCalls(ToolCallPlan plan, String datasourceName, String llmName) {
        List<ToolCallResult> results = new ArrayList<>();

        for (ToolCall toolCall : plan.getToolCalls()) {
            try {
                ToolCallResult result = executeTool(toolCall, datasourceName, llmName);
                results.add(result);
            } catch (Exception e) {
                logger.error("执行工具调用失败: {}", toolCall.getTool(), e);
                ToolCallResult errorResult = new ToolCallResult();
                errorResult.setTool(toolCall.getTool());
                errorResult.setSuccess(false);
                errorResult.setError("执行失败: " + e.getMessage());
                results.add(errorResult);
            }
        }

        return results;
    }

    /**
     * 执行单个工具调用
     */
    private ToolCallResult executeTool(ToolCall toolCall, String datasourceName, String llmName) {
        ToolCallResult result = new ToolCallResult();
        result.setTool(toolCall.getTool());
        result.setParams(toolCall.getParams());

        try {
            switch (toolCall.getTool()) {
                case "scan_mapper_files":
                    result.setResult(scanMapperFiles());
                    break;
                case "parse_mapper_file":
                    result.setResult(parseMapperFile(toolCall.getParams()));
                    break;
                case "get_table_structure":
                    result.setResult(getTableStructure(toolCall.getParams(), datasourceName));
                    break;
                case "get_table_queries":
                    result.setResult(getTableQueries(toolCall.getParams()));
                    break;
                case "analyze_table":
                    result.setResult(analyzeTable(toolCall.getParams(), datasourceName, llmName));
                    break;
                case "analyze_sql":
                    result.setResult(analyzeSql(toolCall.getParams(), datasourceName, llmName));
                    break;
                default:
                    throw new IllegalArgumentException("未知的工具: " + toolCall.getTool());
            }
            result.setSuccess(true);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        return result;
    }

    /**
     * 扫描Mapper文件
     */
    private Map<String, Object> scanMapperFiles() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> files = new ArrayList<>();

        try {
            // 扫描常见的Mapper文件位置
            List<String> searchPaths = Arrays.asList(
                "src/main/resources",
                "src/main/java",
                "mapper",
                "mappers"
            );

            for (String searchPath : searchPaths) {
                Path path = Paths.get(searchPath);
                if (Files.exists(path)) {
                    try (Stream<Path> paths = Files.walk(path)) {
                        paths.filter(p -> p.toString().endsWith("Mapper.xml") || p.toString().endsWith("mapper.xml"))
                                .forEach(p -> {
                                    Map<String, String> fileInfo = new HashMap<>();
                                    fileInfo.put("path", p.toString());
                                    fileInfo.put("name", p.getFileName().toString());
                                    try {
                                        fileInfo.put("size", String.valueOf(Files.size(p)));
                                    } catch (Exception e) {
                                        fileInfo.put("size", "未知");
                                    }
                                    files.add(fileInfo);
                                });
                    }
                }
            }

            result.put("files", files);
            result.put("count", files.size());
            logger.info("扫描到 {} 个Mapper文件", files.size());

        } catch (Exception e) {
            logger.error("扫描Mapper文件失败", e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 解析Mapper文件
     */
    private Map<String, Object> parseMapperFile(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();

        try {
            String filePath = (String) params.get("filePath");
            if (filePath == null) {
                throw new IllegalArgumentException("filePath参数不能为空");
            }

            if (myBatisMapperParserService == null) {
                throw new IllegalStateException("MyBatis解析服务未启用");
            }

            // 读取文件内容
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("文件不存在: " + filePath);
            }

            String xmlContent = Files.readString(path);
            
            // 提取namespace（从文件路径或XML内容）
            String namespace = extractNamespaceFromPath(filePath);
            if (namespace == null) {
                namespace = extractNamespaceFromXml(xmlContent);
            }
            if (namespace == null) {
                namespace = "com.example.mapper." + path.getFileName().toString().replace(".xml", "");
            }

            // 解析XML
            MyBatisMapperParserService.ParseResult parseResult = 
                    myBatisMapperParserService.parseMapperXml(xmlContent, namespace);

            result.put("namespace", parseResult.getMapperNamespace());
            result.put("queryCount", parseResult.getQueryCount());
            result.put("success", true);

        } catch (Exception e) {
            logger.error("解析Mapper文件失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 从文件路径提取namespace
     */
    private String extractNamespaceFromPath(String filePath) {
        // 尝试从路径推断namespace
        // 例如: src/main/java/com/example/mapper/UserMapper.xml -> com.example.mapper.UserMapper
        if (filePath.contains("java")) {
            int javaIndex = filePath.indexOf("java");
            String relativePath = filePath.substring(javaIndex + 5); // +5 for "java/"
            String namespace = relativePath.replace(".xml", "").replace(File.separator, ".");
            return namespace;
        }
        return null;
    }

    /**
     * 从XML内容提取namespace
     */
    private String extractNamespaceFromXml(String xmlContent) {
        Pattern pattern = Pattern.compile("namespace\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(xmlContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 获取表结构
     */
    private Map<String, Object> getTableStructure(Map<String, Object> params, String datasourceName) {
        Map<String, Object> result = new HashMap<>();
        try {
            String tableName = (String) params.get("tableName");
            if (tableName == null) {
                throw new IllegalArgumentException("tableName参数不能为空");
            }

            String sql = "SELECT * FROM " + tableName + " LIMIT 1";
            List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(sql, datasourceName);
            
            if (!structures.isEmpty()) {
                result.put("tableStructure", structures.get(0));
                result.put("success", true);
            } else {
                result.put("success", false);
                result.put("error", "未找到表: " + tableName);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 获取表查询
     */
    private Map<String, Object> getTableQueries(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String tableName = (String) params.get("tableName");
            if (tableName == null) {
                throw new IllegalArgumentException("tableName参数不能为空");
            }

            if (myBatisMapperParserService == null) {
                throw new IllegalStateException("MyBatis解析服务未启用");
            }

            List<Map<String, Object>> queries = myBatisMapperParserService.getQueriesByTable(tableName);
            result.put("queries", queries);
            result.put("count", queries.size());
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 分析表
     */
    private Map<String, Object> analyzeTable(Map<String, Object> params, String datasourceName, String llmName) {
        Map<String, Object> result = new HashMap<>();
        try {
            String tableName = (String) params.get("tableName");
            if (tableName == null) {
                throw new IllegalArgumentException("tableName参数不能为空");
            }

            TableQueryAnalysisService.TableAnalysisResult analysisResult = 
                    tableQueryAnalysisService.analyzeTable(tableName, datasourceName);
            
            result.put("analysisResult", analysisResult);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 分析SQL
     */
    private Map<String, Object> analyzeSql(Map<String, Object> params, String datasourceName, String llmName) {
        Map<String, Object> result = new HashMap<>();
        try {
            String sql = (String) params.get("sql");
            if (sql == null) {
                throw new IllegalArgumentException("sql参数不能为空");
            }

            if (aiClientService == null) {
                throw new IllegalStateException("AI服务未启用");
            }

            // 获取执行计划
            ExecutionPlan plan = sqlExecutionPlanService.getExecutionPlan(sql, datasourceName);
            List<TableStructure> structures = sqlExecutionPlanService.getTableStructures(sql, datasourceName);

            // 格式化
            String executionPlanStr = objectMapper.writeValueAsString(plan);
            String tableStructuresStr = objectMapper.writeValueAsString(structures);

            // 调用AI分析
            String analysisResult = aiClientService.analyzeSqlPerformance(
                    sql, executionPlanStr, tableStructuresStr, llmName);

            result.put("sql", sql);
            result.put("executionPlan", plan);
            result.put("tableStructures", structures);
            result.put("analysisResult", analysisResult);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 生成最终分析结果
     */
    private String generateFinalResult(String userRequest, ToolCallPlan plan, 
                                      List<ToolCallResult> results, String llmName) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("用户需求: ").append(userRequest).append("\n\n");
            context.append("识别意图: ").append(plan.getIntent()).append("\n\n");
            context.append("执行的工具调用:\n");
            
            for (ToolCallResult result : results) {
                context.append("- ").append(result.getTool()).append(": ");
                if (result.isSuccess()) {
                    context.append("成功\n");
                    // 简化结果展示
                    if (result.getResult() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resultMap = (Map<String, Object>) result.getResult();
                        if (resultMap.containsKey("count")) {
                            context.append("  结果数量: ").append(resultMap.get("count")).append("\n");
                        }
                        if (resultMap.containsKey("analysisResult")) {
                            context.append("  分析完成\n");
                        }
                    }
                } else {
                    context.append("失败 - ").append(result.getError()).append("\n");
                }
            }

            String prompt = String.format(
                "基于以下工具调用结果，为用户需求生成最终的分析报告。\n\n" +
                "%s\n\n" +
                "请用中文生成一份详细、专业的分析报告，包括：\n" +
                "1. 需求理解\n" +
                "2. 执行的分析步骤\n" +
                "3. 发现的问题\n" +
                "4. 优化建议\n" +
                "5. 总结\n\n" +
                "报告要清晰、专业、可操作。",
                context.toString()
            );

            ChatClient chatClient = llmManagerService.getChatClient(llmName);
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

        } catch (Exception e) {
            logger.error("生成最终结果失败", e);
            return "分析完成，但生成报告时出错: " + e.getMessage();
        }
    }

    /**
     * 工具调用规划
     */
    public static class ToolCallPlan {
        private String intent;
        private List<ToolCall> toolCalls = new ArrayList<>();

        public String getIntent() {
            return intent;
        }

        public void setIntent(String intent) {
            this.intent = intent;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }
    }

    /**
     * 工具调用
     */
    public static class ToolCall {
        private String tool;
        private Map<String, Object> params;

        public ToolCall() {
        }

        public ToolCall(String tool, Map<String, Object> params) {
            this.tool = tool;
            this.params = params;
        }

        public String getTool() {
            return tool;
        }

        public void setTool(String tool) {
            this.tool = tool;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public void setParams(Map<String, Object> params) {
            this.params = params;
        }
    }

    /**
     * 工具调用结果
     */
    public static class ToolCallResult {
        private String tool;
        private Map<String, Object> params;
        private boolean success;
        private Object result;
        private String error;

        public String getTool() {
            return tool;
        }

        public void setTool(String tool) {
            this.tool = tool;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public void setParams(Map<String, Object> params) {
            this.params = params;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * 自然语言响应
     */
    public static class NaturalLanguageResponse {
        private boolean success;
        private String userRequest;
        private String intent;
        private List<ToolCall> toolCalls;
        private List<ToolCallResult> toolResults;
        private String analysisResult;
        private String error;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getUserRequest() {
            return userRequest;
        }

        public void setUserRequest(String userRequest) {
            this.userRequest = userRequest;
        }

        public String getIntent() {
            return intent;
        }

        public void setIntent(String intent) {
            this.intent = intent;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }

        public List<ToolCallResult> getToolResults() {
            return toolResults;
        }

        public void setToolResults(List<ToolCallResult> toolResults) {
            this.toolResults = toolResults;
        }

        public String getAnalysisResult() {
            return analysisResult;
        }

        public void setAnalysisResult(String analysisResult) {
            this.analysisResult = analysisResult;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}

