package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.api.ResourceNotFoundException;
import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.ProfilingRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single command boundary for statement analysis. It validates tenant-owned inputs and
 * atomically persists Session/Run/Job before returning; the HTTP request never performs analysis.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class AnalysisRunOrchestrator {

    public static final String PROTOCOL = "STATEMENT_ANALYSIS";

    private final SessionRepository sessions;
    private final AgentRunRepository runs;
    private final AgentJobRepository jobs;
    private final RunEventRepository events;
    private final ProfilingRepository profiling;
    private final ArtifactService artifacts;
    private final ObjectMapper objectMapper;

    public AnalysisRunOrchestrator(SessionRepository sessions, AgentRunRepository runs,
                                   AgentJobRepository jobs, RunEventRepository events,
                                   ProfilingRepository profiling, ArtifactService artifacts,
                                   ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.runs = runs;
        this.jobs = jobs;
        this.events = events;
        this.profiling = profiling;
        this.artifacts = artifacts;
        this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public RunHandle start(String clientId, Command command) {
        // Resolve through tenant-scoped repositories before creating any run resources.
        artifacts.read(clientId, command.artifactId());
        profiling.findProfile(command.datasourceProfileId(), clientId)
                .orElseThrow(() -> new ResourceNotFoundException("数据源配置不存在"));

        String sessionId;
        if (command.sessionId() == null || command.sessionId().isBlank()) {
            sessionId = sessions.create("session_" + UUID.randomUUID(), clientId,
                    command.statementId() + " SQL 分析").id();
        } else {
            sessionId = sessions.findByIdForClient(command.sessionId(), clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("会话不存在"))
                    .id();
        }

        String runId = "run_" + UUID.randomUUID();
        runs.create(runId, sessionId, "deterministic-analysis");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("protocol", PROTOCOL);
        payload.put("clientId", clientId);
        payload.put("sessionId", sessionId);
        payload.put("artifactId", command.artifactId());
        payload.put("statementId", command.statementId());
        payload.put("datasourceProfileId", command.datasourceProfileId());
        putNullable(payload, "projectId", command.projectId());
        putNullable(payload, "moduleId", command.moduleId());
        putNullable(payload, "mybatisConfigXml", command.mybatisConfigXml());
        putNullable(payload, "databaseId", command.databaseId());
        putNullable(payload, "schemaName", command.schemaName());
        payload.put("maxScenarios", command.maxScenarios() == null ? 20 : command.maxScenarios());
        payload.set("userSamples", objectMapper.valueToTree(
                command.userSamples() == null ? List.of() : command.userSamples()));

        jobs.enqueue("job_" + UUID.randomUUID(), runId, payload.toString());
        events.append(runId, "RUN_QUEUED", objectMapper.createObjectNode()
                .put("runId", runId).put("sessionId", sessionId).toString());
        sessions.touch(sessionId, "RUNNING");
        return new RunHandle(sessionId, runId, "QUEUED",
                "/api/v1/agui/runs/" + runId + "/stream");
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field);
        else node.put(field, value);
    }

    public record Command(String artifactId, String statementId, String datasourceProfileId,
                          String projectId, String moduleId, String sessionId,
                          String mybatisConfigXml, String databaseId, String schemaName, Integer maxScenarios,
                          List<Map<String, Object>> userSamples) {
        public Command(String artifactId, String statementId, String datasourceProfileId,
                       String projectId, String moduleId, String sessionId,
                       String mybatisConfigXml, String databaseId, Integer maxScenarios,
                       List<Map<String, Object>> userSamples) {
            this(artifactId, statementId, datasourceProfileId, projectId, moduleId, sessionId,
                    mybatisConfigXml, databaseId, "public", maxScenarios, userSamples);
        }
    }

    public record RunHandle(String sessionId, String runId, String status, String streamUrl) {}
}
