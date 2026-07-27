package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.MessageRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.biz.sccba.sqlanalyzer.domain.AgentRun;
import com.biz.sccba.sqlanalyzer.domain.AnalysisSession;
import com.biz.sccba.sqlanalyzer.domain.ConversationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

/** Analysis session lifecycle and run submission. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class SessionService {
    private final SessionRepository sessions;
    private final MessageRepository messages;
    private final AgentRunRepository runs;
    private final AgentJobRepository jobs;
    private final RunEventRepository events;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<com.biz.sccba.sqlanalyzer.agui.AguiExecutor> aguiExecutor;
    private final int maxConcurrentRuns;

    public SessionService(SessionRepository sessions, MessageRepository messages, AgentRunRepository runs,
                          AgentJobRepository jobs, RunEventRepository events, ObjectMapper objectMapper,
                          ObjectProvider<com.biz.sccba.sqlanalyzer.agui.AguiExecutor> aguiExecutor,
                          @Value("${sql-analyzer.limits.max-concurrent-runs:10}") int maxConcurrentRuns) {
        this.sessions = sessions;
        this.messages = messages;
        this.runs = runs;
        this.jobs = jobs;
        this.events = events;
        this.objectMapper = objectMapper;
        this.aguiExecutor = aguiExecutor;
        this.maxConcurrentRuns = Math.max(1, maxConcurrentRuns);
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public AnalysisSession createSession(String clientId, String title) {
        return sessions.create("session_" + UUID.randomUUID(), clientId,
                title == null || title.isBlank() ? "SQL 分析会话" : title);
    }

    public List<AnalysisSession> listSessions(String clientId) {
        return sessions.listForClient(clientId);
    }

    public AnalysisSession getSession(String clientId, String sessionId) {
        return sessions.findByIdForClient(sessionId, clientId).orElseThrow(() -> new IllegalArgumentException("会话不存在"));
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public RunSubmission submit(String clientId, String sessionId, String content, String messageType, String modelName,
                                List<String> artifactIds, Map<String, String> datasourceProfile) {
        getSession(clientId, sessionId);
        if (runs.countActiveForClient(clientId) >= maxConcurrentRuns) {
            throw new IllegalStateException("当前客户端同时运行的 Agent Session 已达到上限：" + maxConcurrentRuns);
        }
        String runId = "run_" + UUID.randomUUID();
        messages.append("message_" + UUID.randomUUID(), sessionId, "USER", content, messageType, runId);
        runs.create(runId, sessionId, modelName);
        try {
            jobs.enqueue("job_" + UUID.randomUUID(), runId, objectMapper.writeValueAsString(java.util.Map.of(
                    "clientId", clientId, "sessionId", sessionId, "content", content,
                    "messageType", messageType == null ? "TEXT" : messageType,
                    "modelName", modelName == null ? "" : modelName,
                    "artifactIds", artifactIds == null ? List.of() : artifactIds,
                    "datasourceProfile", datasourceProfile == null ? Map.of() : datasourceProfile)));
        } catch (Exception e) {
            throw new IllegalStateException("无法创建 Agent Job", e);
        }
        events.append(runId, "RUN_QUEUED", "{\"runId\":\"" + runId + "\"}");
        sessions.touch(sessionId, "RUNNING");
        return new RunSubmission(runId, sessionId, "QUEUED");
    }

    public List<ConversationMessage> messages(String clientId, String sessionId) {
        getSession(clientId, sessionId);
        return messages.listForSession(clientId, sessionId);
    }

    public List<AgentRun> runs(String clientId, String sessionId) {
        getSession(clientId, sessionId);
        return runs.listForSession(clientId, sessionId);
    }

    public List<RunEventRepository.RunEvent> events(String clientId, String runId, long after) {
        if (!runs.belongsToClient(runId, clientId)) throw new IllegalArgumentException("Run 不存在或不属于当前客户端");
        return events.after(clientId, runId, after);
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public Cancellation cancel(String clientId, String runId) {
        if (!runs.belongsToClient(runId, clientId)) {
            throw new IllegalArgumentException("Run 不存在或不属于当前客户端");
        }
        // Queued first (legacy + AG-UI jobs share the queue).
        boolean cancelled = jobs.cancelQueuedForRun(runId);
        if (cancelled) {
            runs.updateStatus(runId, "CANCELLED", "cancelled by client");
            events.append(runId, "RUN_ERROR", "{\"runId\":\"" + runId
                    + "\",\"code\":\"CANCELLED\",\"message\":\"cancelled by client\",\"retryable\":false}");
            events.append(runId, "RUN_FINISHED", "{\"runId\":\"" + runId
                    + "\",\"status\":\"CANCELLED\"}");
            return new Cancellation(runId, "CANCELLED");
        }
        // Then a live AG-UI streaming execution (emits RUN_ERROR(code=CANCELLED)+RUN_FINISHED).
        var executor = aguiExecutor.getIfAvailable();
        if (executor != null && executor.cancel(runId)) {
            return new Cancellation(runId, "CANCELLED");
        }
        return new Cancellation(runId, "NOT_CANCELLABLE");
    }

    public record RunSubmission(String runId, String sessionId, String status) {}
    public record Cancellation(String runId, String status) {}
}
