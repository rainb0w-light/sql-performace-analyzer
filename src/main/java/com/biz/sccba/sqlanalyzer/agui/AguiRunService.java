package com.biz.sccba.sqlanalyzer.agui;

import com.biz.sccba.sqlanalyzer.adapter.agentscope.UserBindingAgents;
import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import com.biz.sccba.sqlanalyzer.domain.AnalysisSession;
import com.biz.sccba.sqlanalyzer.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agui.model.RunAgentInput;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates AG-UI runs: maps AG-UI {@code threadId} to {@code analysis_session.id}, allocates the
 * {@code agent_run}, enqueues an {@code AGUI} protocol job, and stamps the authenticated client id
 * into server-controlled {@code forwardedProps} (the identity source for USER-scoped state/memory).
 *
 * <p>Supplying an existing {@code runId} is an idempotent replay: the same run is returned and the
 * client resumes the persisted event stream instead of starting a second execution.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class AguiRunService {

    public static final String PROTOCOL = "AGUI";

    private final SessionService sessions;
    private final SessionRepository sessionDao;
    private final AgentRunRepository runs;
    private final AgentJobRepository jobs;
    private final ObjectMapper objectMapper;
    private final int maxConcurrentRuns;

    public AguiRunService(SessionService sessions, SessionRepository sessionDao, AgentRunRepository runs, AgentJobRepository jobs,
                          ObjectMapper objectMapper,
                          @Value("${sql-analyzer.limits.max-concurrent-runs:10}") int maxConcurrentRuns) {
        this.sessions = sessions;
        this.sessionDao = sessionDao;
        this.runs = runs;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.maxConcurrentRuns = Math.max(1, maxConcurrentRuns);
    }

    public record RunHandle(String runId, String sessionId, boolean created) {}

    @Transactional(transactionManager = "managementTransactionManager")
    public RunHandle submit(String clientId, RunAgentInput input) {
        if (input == null) throw new IllegalArgumentException("RunAgentInput 不能为空");

        String sessionId;
        String threadId = input.getThreadId();
        if (threadId == null || threadId.isBlank()) {
            AnalysisSession created = sessions.createSession(clientId, "AG-UI 会话");
            sessionId = created.id();
        } else {
            sessionId = sessions.getSession(clientId, threadId).id();
        }

        String requestedRunId = input.getRunId() == null || input.getRunId().isBlank()
                ? "run_" + UUID.randomUUID() : input.getRunId();

        // Idempotent replay: an already-persisted run owned by this client is resumed, not re-run.
        var existing = runs.findById(requestedRunId);
        if (existing.isPresent()) {
            if (!runs.belongsToClient(requestedRunId, clientId)) {
                throw new IllegalArgumentException("Run 不存在或不属于当前客户端");
            }
            return new RunHandle(requestedRunId, sessionId, false);
        }

        if (runs.countActiveForClient(clientId) >= maxConcurrentRuns) {
            throw new IllegalStateException("当前客户端同时运行的 Agent Session 已达到上限：" + maxConcurrentRuns);
        }

        // Server-controlled identity stamp: forwardedProps["spa.clientId"] is authoritative and
        // overwrites anything the client sent.
        Map<String, Object> props = new LinkedHashMap<>();
        if (input.getForwardedProps() != null) props.putAll(input.getForwardedProps());
        props.put(UserBindingAgents.CLIENT_ID_PROP, clientId);
        RunAgentInput bound = RunAgentInput.builder()
                .threadId(sessionId)
                .runId(requestedRunId)
                .messages(input.getMessages())
                .tools(input.getTools())
                .context(input.getContext())
                .state(input.getState())
                .forwardedProps(props)
                .build();

        String modelName = props.get("modelName") == null ? "" : String.valueOf(props.get("modelName"));
        runs.create(requestedRunId, sessionId, modelName.isBlank() ? null : modelName);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("protocol", PROTOCOL);
            payload.put("clientId", clientId);
            payload.put("sessionId", sessionId);
            payload.put("runId", requestedRunId);
            payload.put("modelName", modelName);
            payload.put("input", objectMapper.writeValueAsString(bound));
            jobs.enqueue("job_" + UUID.randomUUID(), requestedRunId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("无法创建 AG-UI Job", e);
        }
        sessionDao.touch(sessionId, "RUNNING");
        return new RunHandle(requestedRunId, sessionId, true);
    }
}
