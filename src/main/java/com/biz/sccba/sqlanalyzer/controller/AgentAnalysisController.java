package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.agent.SQLAnalysisOrchestrator;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisResult;
import com.biz.sccba.sqlanalyzer.model.agent.AnalysisSession;
import com.biz.sccba.sqlanalyzer.memory.SessionMemoryService;
import com.biz.sccba.sqlanalyzer.service.AgentScopeLlmService;
import com.biz.sccba.sqlanalyzer.service.DataSourceManagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent SQL 分析控制器
 * 提供基于 Agent 的 SQL 分析 REST API
 */
@RestController
@RequestMapping("/api/v2/agent")
public class AgentAnalysisController {

    private final SQLAnalysisOrchestrator orchestrator;
    private final SessionMemoryService sessionMemory;
    private final AgentScopeLlmService llmService;
    private final DataSourceManagerService dataSourceService;

    public AgentAnalysisController(SQLAnalysisOrchestrator orchestrator,
                                   SessionMemoryService sessionMemory,
                                   AgentScopeLlmService llmService,
                                   DataSourceManagerService dataSourceService) {
        this.orchestrator = orchestrator;
        this.sessionMemory = sessionMemory;
        this.llmService = llmService;
        this.dataSourceService = dataSourceService;
    }

    /**
     * 分析 SQL 性能
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyzeSql(@RequestBody SqlAnalysisRequest request) {
        System.out.println("[AgentAnalysisController] 收到 SQL 分析请求：datasource=" + request.getDatasourceName() + ", llm=" + request.getLlmName());

        AnalysisResult result = orchestrator.analyze(
            request.getSql(),
            request.getDatasourceName(),
            request.getLlmName()
        ).block();

        return ResponseEntity.ok(result);
    }

    /**
     * 综合分析表
     */
    @GetMapping("/analyze-table/{tableName}")
    public ResponseEntity<AnalysisResult> analyzeTable(
            @PathVariable String tableName,
            @RequestParam(required = false) String datasourceName,
            @RequestParam(required = false) String llmName) {

        System.out.println("[AgentAnalysisController] 收到表分析请求：table=" + tableName);

        AnalysisResult result = orchestrator.analyze("Analyze table: " + tableName, datasourceName, llmName).block();
        return ResponseEntity.ok(result);
    }

    /**
     * 解析 MyBatis Mapper
     */
    @PostMapping("/parse-mapper")
    public ResponseEntity<AnalysisResult> parseMapper(@RequestBody MapperParseRequest request) {
        System.out.println("[AgentAnalysisController] 收到 Mapper 解析请求：file=" + request.getFilePath());

        AnalysisResult result = orchestrator.analyze("Parse mapper: " + request.getFilePath(), null, request.getLlmName()).block();
        return ResponseEntity.ok(result);
    }

    /**
     * 自然语言分析
     */
    @PostMapping("/chat")
    public ResponseEntity<AnalysisResult> naturalLanguageAnalysis(@RequestBody NaturalLanguageRequest request) {
        System.out.println("[AgentAnalysisController] 收到自然语言分析请求：" + request.getQuery());

        AnalysisResult result = orchestrator.analyze(
            request.getQuery(),
            request.getDatasourceName(),
            request.getLlmName()
        ).block();

        return ResponseEntity.ok(result);
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<AnalysisSession> getSession(@PathVariable String sessionId) {
        AnalysisSession session = sessionMemory.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    /**
     * 获取所有活动会话
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<AnalysisSession>> getActiveSessions() {
        return ResponseEntity.ok(sessionMemory.getActiveSessions());
    }

    /**
     * 获取可用数据源
     */
    @GetMapping("/datasources")
    public ResponseEntity<List<DataSourceManagerService.DataSourceInfo>> getDatasources() {
        return ResponseEntity.ok(dataSourceService.getAllDataSources());
    }

    /**
     * 获取可用 LLM
     */
    @GetMapping("/llms")
    public ResponseEntity<List<AgentScopeLlmService.LlmInfo>> getLlms() {
        return ResponseEntity.ok(llmService.getLlmInfos());
    }

    /**
     * 获取 Agent 状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeSessions", sessionMemory.getSessionCount());
        status.put("availableDatasources", dataSourceService.getAllDataSources().size());
        status.put("availableLlms", llmService.getAvailableModelNames());
        status.put("status", "running");
        return ResponseEntity.ok(status);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "SQL Performance Analyzer - AgentScope Edition"
        ));
    }

    /**
     * SQL 分析请求
     */
    public static class SqlAnalysisRequest {
        private String sql;
        private String datasourceName;
        private String llmName;

        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public String getDatasourceName() { return datasourceName; }
        public void setDatasourceName(String datasourceName) { this.datasourceName = datasourceName; }
        public String getLlmName() { return llmName; }
        public void setLlmName(String llmName) { this.llmName = llmName; }
    }

    /**
     * Mapper 解析请求
     */
    public static class MapperParseRequest {
        private String filePath;
        private String xmlContent;
        private String llmName;

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getXmlContent() { return xmlContent; }
        public void setXmlContent(String xmlContent) { this.xmlContent = xmlContent; }
        public String getLlmName() { return llmName; }
        public void setLlmName(String llmName) { this.llmName = llmName; }
    }

    /**
     * 自然语言请求
     */
    public static class NaturalLanguageRequest {
        private String query;
        private String datasourceName;
        private String llmName;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public String getDatasourceName() { return datasourceName; }
        public void setDatasourceName(String datasourceName) { this.datasourceName = datasourceName; }
        public String getLlmName() { return llmName; }
        public void setLlmName(String llmName) { this.llmName = llmName; }
    }
}
