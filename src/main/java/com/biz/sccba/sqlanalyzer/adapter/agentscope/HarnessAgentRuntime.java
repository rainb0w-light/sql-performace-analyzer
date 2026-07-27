package com.biz.sccba.sqlanalyzer.adapter.agentscope;

import com.biz.sccba.sqlanalyzer.agentscope.store.AgentScopeStoreProvider;
import com.biz.sccba.sqlanalyzer.service.AgentScopeLlmService;
import com.biz.sccba.sqlanalyzer.agent.AgentRuntime;
import com.biz.sccba.sqlanalyzer.agent.AgentExecutionContext;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.time.Duration;

/**
 * AgentScope Java 2.x runtime.
 *
 * <p>A single shared {@link HarnessAgent} per model configuration serves all clients and sessions
 * (development-guide §2.2). Each call passes {@code RuntimeContext(userId=clientId, sessionId)};
 * the framework serializes same-session calls and runs different sessions in parallel, restoring
 * and persisting per-session {@code AgentState} through the {@link DistributedStore} chosen by
 * the {@link AgentScopeStoreProvider} for the active management database (official PostgreSQL
 * store on PostgreSQL; persistent H2 AgentStateStore + BaseStore on H2 — never in-memory).
 * Long-term memory is USER-isolated in the same store.
 *
 * <p>Product tables (analysis_session/conversation_message/agent_run/run_event) remain the
 * queryable, auditable projection; AgentState never replaces them.
 */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public final class HarnessAgentRuntime implements AgentRuntime {

    private final SharedHarnessAgents sharedAgents;

    public HarnessAgentRuntime(AgentScopeLlmService models,
                               Toolkit toolkit,
                               AgentScopeStoreProvider storeProvider,
                               @Qualifier("managementDataSource") DataSource dataSource) {
        this.sharedAgents = new SharedHarnessAgents(
                modelKey -> models.getModel("default".equals(modelKey) ? null : modelKey)
                        .orElseThrow(() -> new IllegalArgumentException("没有可用的 AgentScope 模型")),
                toolkit,
                storeProvider.create(dataSource));
    }

    /** Shared agent for a model key; used by the AG-UI streaming executor. */
    public HarnessAgent agentFor(String modelName) {
        return sharedAgents.agentFor(modelName);
    }

    @Override
    public AgentOutput execute(AgentExecutionRequest request) {
        String modelKey = request.modelName() == null || request.modelName().isBlank()
                ? "default" : request.modelName();
        HarnessAgent agent = sharedAgents.agentFor(modelKey);

        RuntimeContext context = RuntimeContext.builder()
                .userId(request.clientId())
                .sessionId(request.sessionId())
                .put("runId", request.runId())
                .put(AgentExecutionContext.class, new AgentExecutionContext(request.clientId(), request.sessionId(), request.runId()))
                .put("artifactIds", request.artifactIds())
                .put("datasourceProfile", request.datasourceProfile())
                .build();
        Msg result = agent.call(request.content(), context).block(Duration.ofMinutes(5));
        if (result == null) return new AgentOutput(false, "Agent 无响应", request.sessionId());
        return new AgentOutput(true, result.getTextContent(), request.sessionId());
    }

    @PreDestroy
    void close() {
        sharedAgents.close();
    }
}
