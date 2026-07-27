package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.agent.AgentRuntime;
import com.biz.sccba.sqlanalyzer.agent.ContextBuilder;
import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.MessageRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.List;
import java.util.Map;

/** Polls queued Agent jobs, drives the shared AgentScope runtime, and persists run events/recommendations. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.worker", name = "enabled", havingValue = "true")
public class AgentWorker {
    private final AgentJobRepository jobs;
    private final AgentRunRepository runs;
    private final RunEventRepository events;
    private final MessageRepository messages;
    private final AgentRuntime runtime;
    private final ContextBuilder contextBuilder;
    private final RecommendationProjector recommendationProjector;
    private final ObjectMapper objectMapper;
    private final String workerId = "worker_" + UUID.randomUUID();

    private final org.springframework.beans.factory.ObjectProvider<com.biz.sccba.sqlanalyzer.agui.AguiExecutor> aguiExecutor;
    private final org.springframework.beans.factory.ObjectProvider<com.biz.sccba.sqlanalyzer.analysis.StatementAnalysisJobExecutor>
            statementAnalysisExecutor;

    public AgentWorker(AgentJobRepository jobs, AgentRunRepository runs, RunEventRepository events, MessageRepository messages,
                       AgentRuntime runtime, ContextBuilder contextBuilder,
                       RecommendationProjector recommendationProjector, ObjectMapper objectMapper,
                       org.springframework.beans.factory.ObjectProvider<com.biz.sccba.sqlanalyzer.agui.AguiExecutor> aguiExecutor,
                       org.springframework.beans.factory.ObjectProvider<com.biz.sccba.sqlanalyzer.analysis.StatementAnalysisJobExecutor>
                               statementAnalysisExecutor) {
        this.jobs = jobs;
        this.runs = runs;
        this.events = events;
        this.messages = messages;
        this.runtime = runtime;
        this.contextBuilder = contextBuilder;
        this.recommendationProjector = recommendationProjector;
        this.objectMapper = objectMapper;
        this.aguiExecutor = aguiExecutor;
        this.statementAnalysisExecutor = statementAnalysisExecutor;
    }

    @Scheduled(fixedDelayString = "${sql-analyzer.worker.poll-delay-ms:500}")
    public void poll() {
        jobs.claim(workerId).ifPresent(this::dispatch);
    }

    private void dispatch(AgentJobRepository.Job job) {
        try {
            String protocol = objectMapper.readTree(job.payloadJson()).path("protocol").asText("");
            if ("AGUI".equals(protocol)) {
                var executor = aguiExecutor.getIfAvailable();
                if (executor == null) {
                    jobs.failNoRetry(job.id(), "AG-UI 执行器不可用");
                    runs.updateStatus(job.runId(), "FAILED", "AG-UI 执行器不可用");
                    return;
                }
                // AG-UI runs execute asynchronously and persist their own terminal state.
                executor.executeAsync(job);
                return;
            }
            if (com.biz.sccba.sqlanalyzer.analysis.AnalysisRunOrchestrator.PROTOCOL.equals(protocol)) {
                var executor = statementAnalysisExecutor.getIfAvailable();
                if (executor == null) {
                    jobs.failNoRetry(job.id(), "Statement 分析执行器不可用");
                    runs.updateStatus(job.runId(), "FAILED", "Statement 分析执行器不可用");
                    events.append(job.runId(), "RUN_ERROR",
                            "{\"code\":\"ANALYSIS_EXECUTOR_UNAVAILABLE\",\"retryable\":false}");
                    events.append(job.runId(), "RUN_FINISHED", "{\"status\":\"FAILED\"}");
                    return;
                }
                executor.execute(job);
                return;
            }
        } catch (Exception e) {
            // malformed payload falls through to the legacy path's error handling
        }
        execute(job);
    }

    private void execute(AgentJobRepository.Job job) {
        String sessionId = null;
        try {
            JsonNode payload = objectMapper.readTree(job.payloadJson());
            sessionId = payload.path("sessionId").asText();
            String content = payload.path("content").asText();
            String modelName = payload.path("modelName").asText(null);
            events.append(job.runId(), "RUN_STARTED", "{}");
            runs.updateStatus(job.runId(), "RUNNING", null);
            events.append(job.runId(), "ASSISTANT_STARTED", "{}");

            List<String> artifactIds = payload.has("artifactIds") && payload.path("artifactIds").isArray()
                    ? objectMapper.convertValue(payload.path("artifactIds"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                    : List.of();
            Map<String, String> datasourceProfile = payload.has("datasourceProfile") && payload.path("datasourceProfile").isObject()
                    ? objectMapper.convertValue(payload.path("datasourceProfile"), objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class))
                    : Map.of();
            String contextContent = contextBuilder.build(payload.path("clientId").asText(), sessionId, content, artifactIds);
            AgentRuntime.AgentOutput output = runtime.execute(new AgentRuntime.AgentExecutionRequest(
                    payload.path("clientId").asText(), sessionId, job.runId(), contextContent, modelName,
                    artifactIds, datasourceProfile));
            if (output.success()) {
                recommendationProjector.project(job.runId(), sessionId, output.report());
            }
            String report = objectMapper.writeValueAsString(java.util.Map.of(
                    "success", output.success(), "report", output.report() == null ? "" : output.report()));
            if (output.success()) {
                appendAssistantMessage(sessionId, job.runId(), output.report(), "TEXT");
                events.append(job.runId(), "RUN_COMPLETED", report);
                runs.updateStatus(job.runId(), "COMPLETED", null);
                jobs.complete(job.id());
            } else {
                boolean retry = jobs.fail(job.id(), output.report());
                if (!retry) appendAssistantMessage(sessionId, job.runId(), output.report(), "ERROR");
                events.append(job.runId(), retry ? "RUN_RETRYING" : "RUN_FAILED", report);
                runs.updateStatus(job.runId(), retry ? "RETRYING" : "FAILED", output.report());
            }
        } catch (Exception e) {
            boolean retry = jobs.fail(job.id(), e.getMessage());
            if (!retry && sessionId != null && !sessionId.isBlank()) {
                appendAssistantMessage(sessionId, job.runId(), e.getMessage(), "ERROR");
            }
            events.append(job.runId(), retry ? "RUN_RETRYING" : "RUN_FAILED", "{\"error\":\"worker failure\"}");
            runs.updateStatus(job.runId(), retry ? "RETRYING" : "FAILED", e.getMessage());
        }
    }

    private void appendAssistantMessage(String sessionId, String runId, String content, String messageType) {
        try {
            messages.append("message_assistant_" + runId, sessionId, "ASSISTANT",
                    content == null ? "" : content, messageType, runId);
        } catch (RuntimeException persistenceError) {
            // Conversation history is best-effort here; never turn a completed Agent run into a retry
            // solely because the auxiliary history projection failed.
            try {
                events.append(runId, "ASSISTANT_MESSAGE_PERSIST_FAILED", "{\"error\":\"history write failed\"}");
            } catch (RuntimeException ignored) {
                // Preserve the primary run lifecycle even if event persistence is also unavailable.
            }
        }
    }
}
