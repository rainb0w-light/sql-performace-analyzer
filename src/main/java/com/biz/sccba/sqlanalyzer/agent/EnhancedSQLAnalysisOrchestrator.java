package com.biz.sccba.sqlanalyzer.agent;

import com.biz.sccba.sqlanalyzer.agent.context.SharedContextRepository;
import com.biz.sccba.sqlanalyzer.memory.BusinessSemanticsMemoryService;
import com.biz.sccba.sqlanalyzer.memory.SessionMemoryService;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisResult;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisSession;
import com.biz.sccba.sqlanalyzer.service.AgentScopeLlmService;
import com.biz.sccba.sqlanalyzer.tool.SqlAnalyzerTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Enhanced SQL analysis orchestrator with parallel expert execution.
 * Replaces the sequential ReActAgent with true parallel multi-expert coordination.
 */
@Service
public class EnhancedSQLAnalysisOrchestrator {

    private final SqlAnalyzerTools tools;
    private final AgentScopeLlmService llmService;
    private final SessionMemoryService sessionMemory;
    private final BusinessSemanticsMemoryService semanticsMemory;
    private final SharedContextRepository sharedContextRepository;
    private final ExpertPriorityCalculator priorityCalculator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple circuit breaker state tracking
    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    
    // Tool call counters (for limiting calls)
    private final Map<String, AtomicInteger> toolCallCounters = new ConcurrentHashMap<>();

    // Maximum tool calls per session (prevents infinite loops)
    private static final int MAX_TOOL_CALLS_PER_SESSION = 3;
    
    // Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    public EnhancedSQLAnalysisOrchestrator(SqlAnalyzerTools tools,
                                          AgentScopeLlmService llmService,
                                          SessionMemoryService sessionMemory,
                                          BusinessSemanticsMemoryService semanticsMemory,
                                          SharedContextRepository sharedContextRepository,
                                          ExpertPriorityCalculator priorityCalculator) {
        this.tools = tools;
        this.llmService = llmService;
        this.sessionMemory = sessionMemory;
        this.semanticsMemory = semanticsMemory;
        this.sharedContextRepository = sharedContextRepository;
        this.priorityCalculator = priorityCalculator;
    }

    /**
     * Analyzes user request with parallel expert execution.
     */
    public AnalysisResult analyze(String userRequest, String datasourceName, String llmName) {
        System.out.println("[EnhancedSQLAnalysisOrchestrator] 开始分析请求：" + userRequest);
        long startTime = System.currentTimeMillis();

        // Create session
        String sessionId = sessionMemory.createSession(userRequest, datasourceName, llmName);
        toolCallCounters.put(sessionId, new AtomicInteger(0));

        try {
            // Extract SQL and tables from request (basic parsing)
            String sql = extractSqlFromRequest(userRequest);
            List<String> tables = extractTablesFromRequest(userRequest);
            
            // Build parameters map
            Map<String, Object> parameters = buildExpertParameters(datasourceName, sql, tables);
            
            // Execute experts in parallel
            AnalysisResult result = executeExpertsParallel(sessionId, parameters, datasourceName, sql, tables)
                .block(Duration.ofSeconds(120));
                
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("[EnhancedSQLAnalysisOrchestrator] 并行专家执行完成，耗时：" + duration + "ms");
            
            // Record reasoning step
            AnalysisSession.ReasoningStep step = AnalysisSession.ReasoningStep.builder()
                .stepNumber(1)
                .thought("并行多专家执行完成，耗时：" + duration + "ms")
                .action("生成综合分析结果")
                .observation(result != null && result.isSuccess() ? "分析完成" : "分析失败")
                .build();
            sessionMemory.addReasoningStep(sessionId, step);
            
            if (result != null) {
                sessionMemory.setResult(sessionId, result);
            }
            
            return result != null ? result : AnalysisResult.builder()
                .success(false)
                .errorMessage("分析超时或失败")
                .build();

        } catch (Exception e) {
            System.err.println("[EnhancedSQLAnalysisOrchestrator] 分析失败：" + e.getMessage());
            e.printStackTrace();
            
            cleanupSessionResources(sessionId);
            sessionMemory.updateStatus(sessionId, AnalysisSession.SessionStatus.FAILED);

            return AnalysisResult.builder()
                .success(false)
                .errorMessage("分析失败：" + e.getMessage())
                .build();
        }
    }

    /**
     * Executes all expert tools in parallel using reactive streams.
     */
    private Mono<AnalysisResult> executeExpertsParallel(String sessionId, 
                                                       Map<String, Object> parameters,
                                                       String datasourceName,
                                                       String sql,
                                                       List<String> tables) {
        // Create shared context for this session
        SharedContextRepository.ExpertExecutionContext context = 
            sharedContextRepository.createContext(sessionId);
            
        // Store base parameters in shared context
        context.storeMetadata("parameters", parameters);
        context.storeMetadata("datasourceName", datasourceName);
        context.storeMetadata("sql", sql);
        context.storeMetadata("tables", tables);
        
        // Calculate dynamic priorities
        Map<String, Integer> priorities = priorityCalculator.calculatePriorities(datasourceName, sql, tables);
        context.storeMetadata("priorities", priorities);
        
        // Get expert tools in priority order
        List<String> expertTools = getExpertToolsInPriorityOrder(priorities);
        
        // Execute all experts in parallel
        Flux<Mono<String>> expertExecutions = Flux.fromIterable(expertTools)
            .map(toolName -> executeSingleExpert(toolName, parameters, sessionId, context));
            
        // Collect all results
        return expertExecutions.flatMapSequential(mono -> mono)
            .collectList()
            .map(results -> generateFinalReport(results, context));
    }
    
    /**
     * Gets expert tools ordered by their dynamic priorities.
     */
    private List<String> getExpertToolsInPriorityOrder(Map<String, Integer> priorities) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(priorities.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        return entries.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }
    
    /**
     * Executes a single expert tool.
     */
    private Mono<String> executeSingleExpert(String toolName, 
                                            Map<String, Object> parameters,
                                            String sessionId,
                                            SharedContextRepository.ExpertExecutionContext context) {
        // Check tool call limit
        if (!checkToolCallLimit(sessionId, toolName)) {
            String errorMsg = createErrorResponse("工具调用次数超过限制，已跳过重复调用");
            context.storeExpertResult(toolName, errorMsg);
            return Mono.just(errorMsg);
        }
        
        return Mono.fromCallable(() -> {
            System.out.println("[EnhancedSQLAnalysisOrchestrator] 执行专家工具：" + toolName + " 参数：" + parameters);
            
            // Set session ID for tool tracking
            SqlAnalyzerTools.setCurrentSessionId(sessionId);
            try {
                // Execute tool
                com.biz.sccba.sqlanalyzer.tool.ToolResult toolResult = tools.executeTool(toolName, parameters);
                String resultStr;
                try {
                    // Use reflection to get the data field if Lombok getter is not recognized
                    java.lang.reflect.Field dataField = toolResult.getClass().getDeclaredField("data");
                    dataField.setAccessible(true);
                    Object dataValue = dataField.get(toolResult);
                    resultStr = dataValue != null ? dataValue.toString() : "null";
                } catch (Exception e) {
                    // Fallback: assume the ToolResult has a getData() method
                    resultStr = toolResult.toString();
                }
                
                // Store result in shared context
                context.storeExpertResult(toolName, resultStr);
                
                System.out.println("[EnhancedSQLAnalysisOrchestrator] 专家工具执行成功：" + toolName);
                return resultStr;
            } finally {
                SqlAnalyzerTools.clearCurrentSessionId();
            }
        })
        .onErrorResume(throwable -> {
            System.err.println("[EnhancedSQLAnalysisOrchestrator] 专家工具执行失败：" + toolName + " - " + throwable.getMessage());
            String errorMsg = createErrorResponse("专家工具 " + toolName + " 执行失败：" + throwable.getMessage());
            context.storeExpertResult(toolName, errorMsg);
            return Mono.just(errorMsg);
        });
    }
    
    /**
     * Generates the final analysis report by synthesizing all expert results.
     */
    private AnalysisResult generateFinalReport(List<String> expertResults,
                                                    SharedContextRepository.ExpertExecutionContext context) {
        try {
            // Filter successful results
            List<String> successfulResults = expertResults.stream()
                .filter(result -> {
                    try {
                        JsonNode json = objectMapper.readTree(result);
                        return json.has("success") && json.get("success").asBoolean();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
                
            if (successfulResults.isEmpty()) {
                return AnalysisResult.builder()
                    .success(false)
                    .errorMessage("所有专家分析均失败")
                    .build();
            }
            
            // Simple concatenation of successful results
            String combinedReport = String.join("\n\n---\n\n", successfulResults);
            return AnalysisResult.builder()
                .success(true)
                .report(combinedReport)
                .build();
            
        } catch (Exception e) {
            System.err.println("[EnhancedSQLAnalysisOrchestrator] 生成报告失败：" + e.getMessage());
            return AnalysisResult.builder()
                .success(false)
                .errorMessage("生成报告失败：" + e.getMessage())
                .build();
        }
    }
    
    /**
     * Checks if tool call limit has been exceeded.
     */
    private boolean checkToolCallLimit(String sessionId, String toolName) {
        AtomicInteger counter = toolCallCounters.get(sessionId);
        if (counter == null) {
            return true;
        }
        
        int currentCount = counter.incrementAndGet();
        if (currentCount > MAX_TOOL_CALLS_PER_SESSION) {
            System.out.println("[EnhancedSQLAnalysisOrchestrator] 工具 " + toolName + " 调用次数超过限制 (" + currentCount + ")");
            return false;
        }
        return true;
    }
    
    /**
     * Creates structured error response.
     */
    private String createErrorResponse(String errorMessage) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "success", false,
                "error", errorMessage,
                "recoveryStrategy", "已记录错误并继续分析，基于其他可用信息提供建议"
            ));
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + errorMessage + "\"}";
        }
    }
    
    /**
     * Cleans up session resources.
     */
    private void cleanupSessionResources(String sessionId) {
        toolCallCounters.remove(sessionId);
        sharedContextRepository.removeContext(sessionId);
    }
    
    /**
     * Builds expert tool parameters.
     */
    private Map<String, Object> buildExpertParameters(String datasourceName, String sql, List<String> tables) {
        Map<String, Object> params = new HashMap<>();
        params.put("datasourceName", datasourceName != null ? datasourceName : "default");
        params.put("sql", sql);
        params.put("tables", tables != null ? tables : Collections.emptyList());
        return params;
    }
    
    /**
     * Simple SQL extraction from request (basic implementation).
     */
    private String extractSqlFromRequest(String userRequest) {
        // Look for SQL code blocks
        if (userRequest.contains("```sql")) {
            int start = userRequest.indexOf("```sql") + 7;
            int end = userRequest.indexOf("```", start);
            if (end > start) {
                return userRequest.substring(start, end).trim();
            }
        }
        // Look for generic code blocks
        if (userRequest.contains("```")) {
            int start = userRequest.indexOf("```");
            int end = userRequest.indexOf("```", start + 3);
            if (end > start) {
                String code = userRequest.substring(start + 3, end).trim();
                // Heuristic: if it looks like SQL, use it
                if (code.toLowerCase().contains("select") || 
                    code.toLowerCase().contains("insert") ||
                    code.toLowerCase().contains("update") ||
                    code.toLowerCase().contains("delete")) {
                    return code;
                }
            }
        }
        return null;
    }
    
    /**
     * Simple table extraction from request (basic implementation).
     */
    private List<String> extractTablesFromRequest(String userRequest) {
        // This is a placeholder - in practice, you'd use proper SQL parsing
        // For now, return empty list which will trigger table detection in expert tools
        return Collections.emptyList();
    }

    // Convenience methods (same as original orchestrator)
    
    public AnalysisResult analyzeSql(String sql, String datasourceName, String llmName) {
        String request = String.format("请分析以下 SQL 的性能:\n```sql\n%s\n```", sql);
        return analyze(request, datasourceName, llmName);
    }

    public AnalysisResult analyzeTable(String tableName, String datasourceName, String llmName) {
        String request = String.format("请对表 %s 进行综合性能分析", tableName);
        return analyze(request, datasourceName, llmName);
    }

    public AnalysisResult parseMapper(String filePath, String llmName) {
        String request = String.format("请解析 MyBatis Mapper 文件：%s", filePath);
        return analyze(request, "default", llmName);
    }
}