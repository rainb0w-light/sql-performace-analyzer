package com.biz.sccba.sqlanalyzer.analysis;

import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.biz.sccba.sqlanalyzer.pluginapi.PluginRunPlanningService;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Executes one queued statement-analysis job and owns its persisted AG-UI lifecycle. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class StatementAnalysisJobExecutor {

    private final StatementAnalysisService analysis;
    private final ArtifactService artifacts;
    private final ObjectProvider<PluginRunPlanningService> planning;
    private final AgentJobRepository jobs;
    private final AgentRunRepository runs;
    private final RunEventRepository events;
    private final SessionRepository sessions;
    private final ObjectMapper objectMapper;

    public StatementAnalysisJobExecutor(StatementAnalysisService analysis, ArtifactService artifacts,
                                        ObjectProvider<PluginRunPlanningService> planning,
                                        AgentJobRepository jobs, AgentRunRepository runs,
                                        RunEventRepository events, SessionRepository sessions,
                                        ObjectMapper objectMapper) {
        this.analysis = analysis;
        this.artifacts = artifacts;
        this.planning = planning;
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
            PluginRunPlanningService planner = planning.getIfAvailable();
            if (planner == null) {
                executeLegacy(job, payload, clientId, sessionId);
                return;
            }
            String executionPhase = payload.path("phase").asText("PLAN");
            runs.updateStatus(job.runId(), "PLAN".equals(executionPhase)
                    ? "PLANNING" : "RUNNING", null);
            append(job.runId(), "RUN_STARTED", objectMapper.createObjectNode()
                    .put("runId", job.runId()).put("sessionId", sessionId)
                    .put("phase", executionPhase));

            PluginRunPlanningService.StoredRunPlan stored;
            List<String> included;
            if ("EXECUTE".equals(executionPhase)) {
                stored = planner.read(clientId, required(payload, "planArtifactId"));
                included = stringList(payload.path("includedScenarioIds"));
            } else {
                phase(job.runId(), "PARSING_MAPPER");
                phase(job.runId(), "RESOLVING_CONTEXT");
                var planned = planner.planAndStore(clientId, job.runId(), sessionId, payload);
                stored = planned.plan();
                included = stored.plan().scenarios().stream()
                        .map(item -> item.scenario().scenarioId()).toList();
                boolean review = "REVIEW".equals(payload.path("executionMode").asText("AUTO"));
                ObjectNode event = objectMapper.createObjectNode();
                event.put("name", "spa.scenarios_ready");
                event.put("count", stored.plan().scenarios().size());
                event.put("planArtifactId", planned.planArtifactId());
                event.put("cost", stored.cost().name());
                event.put("requiresConfirmation",
                        review || !stored.blockingGuards().isEmpty());
                event.set("guards", objectMapper.valueToTree(stored.blockingGuards().stream()
                        .map(guard -> Map.of("type", pluginGuard(guard), "blocking", true,
                                "message", "强制守卫需要处理后重新分析",
                                "locator", stored.statementId()))
                        .toList()));
                event.set("requiredScenarioIds",
                        objectMapper.valueToTree(stored.requiredScenarioIds()));
                event.set("scenarios", objectMapper.valueToTree(stored.plan().scenarios().stream()
                        .map(item -> Map.of(
                                "scenarioId", item.scenario().scenarioId(),
                                "name", item.scenario().name(),
                                "required", stored.requiredScenarioIds()
                                        .contains(item.scenario().scenarioId()),
                                "mainPath", item.scenario().name().equals("业务主路径")))
                        .toList()));
                append(job.runId(), "CUSTOM", event);
                if (!stored.blockingGuards().isEmpty()) {
                    for (String guard : stored.blockingGuards()) {
                        append(job.runId(), "CUSTOM", objectMapper.createObjectNode()
                                .put("name", "spa.guard_triggered")
                                .put("guard", guard).put("blocking", true));
                    }
                }
                if (review || !stored.blockingGuards().isEmpty()) {
                    runs.updateStatus(job.runId(), "AWAITING_CONFIRMATION", null);
                    append(job.runId(), "CUSTOM", objectMapper.createObjectNode()
                            .put("name", "spa.awaiting_confirmation")
                            .put("planArtifactId", planned.planArtifactId())
                            .put("blocked", !stored.blockingGuards().isEmpty()));
                    jobs.complete(job.id());
                    sessions.touch(sessionId, "ACTIVE");
                    return;
                }
            }

            byte[] mapperXml = artifacts.read(clientId, stored.sourceArtifactId());
            if (cancelled(job.runId())) {
                return;
            }
            Set<String> selected = new java.util.LinkedHashSet<>(included);
            ScenarioEngine.PlanResult selectedPlan = new ScenarioEngine.PlanResult(
                    stored.plan().namespace(), stored.plan().statementId(),
                    stored.plan().scenarios().stream()
                            .filter(item -> selected.contains(item.scenario().scenarioId())).toList(),
                    stored.plan().loadError());
            String reportId = "report_" + UUID.randomUUID();
            var result = analysis.analyzePrepared(clientId, job.runId(), sessionId, reportId,
                    stored.projectId(), stored.moduleId(), stored.datasourceProfileId(),
                    stored.contextFingerprint(), mapperXml, stored.sourceArtifactId(),
                    stored.context().references(), selectedPlan, stored.context(),
                    new StatementAnalysisService.ProgressListener() {
                        @Override
                        public void assemblingReport() {
                            phase(job.runId(), "ASSEMBLING_REPORT");
                        }
                    });
            if (cancelled(job.runId())) {
                return;
            }

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
            if (cancelled(job.runId())) {
                return;
            }
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

    private void executeLegacy(AgentJobRepository.Job job, JsonNode payload,
                               String clientId, String sessionId) {
        String artifactId = required(payload, "artifactId");
        String statementId = required(payload, "statementId");
        byte[] mapperXml = artifacts.read(clientId, artifactId);
        runs.updateStatus(job.runId(), "RUNNING", null);
        append(job.runId(), "RUN_STARTED", objectMapper.createObjectNode()
                .put("runId", job.runId()).put("sessionId", sessionId));
        String reportId = "report_" + UUID.randomUUID();
        var result = analysis.analyzeWithReportId(clientId, job.runId(), sessionId,
                nullable(payload, "projectId"), mapperXml, artifactId, statementId,
                nullable(payload, "mybatisConfigXml"), nullable(payload, "databaseId"),
                samples(payload.path("userSamples")), payload.path("maxScenarios").asInt(20),
                required(payload, "datasourceProfileId"), nullable(payload, "schemaName"),
                reportId, StatementAnalysisService.ProgressListener.NOOP);
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

    private static List<String> stringList(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> result = new java.util.ArrayList<>();
        node.forEach(item -> result.add(item.asText()));
        return List.copyOf(result);
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

    private boolean cancelled(String runId) {
        return runs.findById(runId).map(run -> "CANCELLED".equals(run.status())).orElse(false);
    }

    private static String pluginGuard(String guard) {
        return switch (guard) {
            case "DOLLAR_WHITELIST_REQUIRED" -> "DOLLAR_WHITELIST_MISSING";
            case "CRITICAL_PARAMETER_TYPE", "LANGUAGE_DRIVER_UNSUPPORTED" ->
                    "UNSUPPORTED_LANGUAGE_OR_TYPE";
            default -> guard;
        };
    }
}
