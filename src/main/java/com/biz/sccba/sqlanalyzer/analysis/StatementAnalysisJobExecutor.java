package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Executes one queued statement-analysis job and owns its persisted AG-UI lifecycle. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class StatementAnalysisJobExecutor {

    private final StatementAnalysisService analysis;
    private final ArtifactService artifacts;
    private final AgentJobRepository jobs;
    private final AgentRunRepository runs;
    private final RunEventRepository events;
    private final SessionRepository sessions;
    private final ObjectMapper objectMapper;

    public StatementAnalysisJobExecutor(StatementAnalysisService analysis, ArtifactService artifacts,
                                        AgentJobRepository jobs, AgentRunRepository runs,
                                        RunEventRepository events, SessionRepository sessions,
                                        ObjectMapper objectMapper) {
        this.analysis = analysis;
        this.artifacts = artifacts;
        this.jobs = jobs;
        this.runs = runs;
        this.events = events;
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    public void execute(AgentJobRepository.Job job) {
        String sessionId = null;
        try {
            JsonNode payload = objectMapper.readTree(job.payloadJson());
            String clientId = required(payload, "clientId");
            sessionId = required(payload, "sessionId");
            String artifactId = required(payload, "artifactId");
            String statementId = required(payload, "statementId");
            byte[] mapperXml = artifacts.read(clientId, artifactId);

            runs.updateStatus(job.runId(), "RUNNING", null);
            append(job.runId(), "RUN_STARTED", objectMapper.createObjectNode()
                    .put("runId", job.runId()).put("sessionId", sessionId));
            phase(job.runId(), "PARSING_MAPPER");
            phase(job.runId(), "RESOLVING_CONTEXT");

            String reportId = "report_" + UUID.randomUUID();
            var result = analysis.analyzeWithReportId(
                    clientId, job.runId(), sessionId, nullable(payload, "projectId"),
                    mapperXml, artifactId, statementId,
                    nullable(payload, "mybatisConfigXml"), nullable(payload, "databaseId"),
                    samples(payload.path("userSamples")), payload.path("maxScenarios").asInt(20),
                    required(payload, "datasourceProfileId"), nullable(payload, "schemaName"),
                    reportId, new StatementAnalysisService.ProgressListener() {
                        @Override
                        public void scenariosReady(com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine.PlanResult plan) {
                            ObjectNode event = objectMapper.createObjectNode();
                            event.put("name", "spa.scenarios_ready");
                            event.put("count", plan.scenarios().size());
                            event.set("fingerprints", objectMapper.valueToTree(plan.scenarios().stream()
                                    .map(s -> s.sqlFingerprint()).distinct().toList()));
                            append(job.runId(), "CUSTOM", event);
                        }

                        @Override
                        public void collectingExecutionPlans() {
                            phase(job.runId(), "COLLECTING_EXECUTION_PLANS");
                        }

                        @Override
                        public void assemblingReport() {
                            phase(job.runId(), "ASSEMBLING_REPORT");
                        }

                        @Override
                        public void enhancingWithAgent() {
                            phase(job.runId(), "AGENT_ENHANCEMENT");
                        }
                    });

            append(job.runId(), "CUSTOM", objectMapper.createObjectNode()
                    .put("name", "spa.report_ready").put("reportId", result.report().id()));
            append(job.runId(), "CUSTOM", objectMapper.createObjectNode()
                    .put("name", "spa.recommendations_ready")
                    .put("reportId", result.report().id())
                    .put("count", result.recommendationCount()));
            append(job.runId(), "RUN_FINISHED", objectMapper.createObjectNode()
                    .put("runId", job.runId()).put("status", "COMPLETED"));
            runs.updateStatus(job.runId(), "COMPLETED", null);
            jobs.complete(job.id());
            sessions.touch(sessionId, "ACTIVE");
        } catch (Exception exception) {
            boolean retry = jobs.fail(job.id(), safe(exception.getMessage()));
            if (retry) {
                runs.updateStatus(job.runId(), "RETRYING", safe(exception.getMessage()));
                append(job.runId(), "CUSTOM", objectMapper.createObjectNode()
                        .put("name", "spa.run_retrying").put("message", safe(exception.getMessage())));
            } else {
                append(job.runId(), "RUN_ERROR", objectMapper.createObjectNode()
                        .put("runId", job.runId()).put("code", "ANALYSIS_FAILED")
                        .put("message", safe(exception.getMessage())).put("retryable", false));
                append(job.runId(), "RUN_FINISHED", objectMapper.createObjectNode()
                        .put("runId", job.runId()).put("status", "FAILED"));
                runs.updateStatus(job.runId(), "FAILED", safe(exception.getMessage()));
                if (sessionId != null && !sessionId.isBlank()) sessions.touch(sessionId, "ACTIVE");
            }
        }
    }

    private void phase(String runId, String phase) {
        append(runId, "CUSTOM", objectMapper.createObjectNode()
                .put("name", "spa.phase_changed").put("phase", phase));
    }

    private void append(String runId, String type, JsonNode payload) {
        events.append(runId, type, payload.toString());
    }

    private static String required(JsonNode payload, String field) {
        String value = payload.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("分析任务缺少字段：" + field);
        return value;
    }

    private static String nullable(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> samples(JsonNode node) {
        if (!node.isArray()) return List.of();
        return objectMapper.convertValue(node, objectMapper.getTypeFactory()
                .constructCollectionType(List.class,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "statement analysis failed" : value;
    }
}
