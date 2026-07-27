package com.biz.sccba.sqlanalyzer.agent;

import java.util.List;
import java.util.Map;

/**
 * The only runtime boundary used by the Agent worker.
 * Implementations may be AgentScope-backed, but application code must not know
 * which AgentScope agent or orchestration strategy is used.
 */
public interface AgentRuntime {
    AgentOutput execute(AgentExecutionRequest request);

    record AgentExecutionRequest(
            String clientId,
            String sessionId,
            String runId,
            String content,
            String modelName,
            List<String> artifactIds,
            Map<String, String> datasourceProfile) {
    }

    record AgentOutput(boolean success, String report, String runtimeSessionId) {
    }
}
