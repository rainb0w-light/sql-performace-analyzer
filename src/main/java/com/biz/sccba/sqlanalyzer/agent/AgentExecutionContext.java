package com.biz.sccba.sqlanalyzer.agent;

/**
 * Non-model execution identity made available to AgentScope tools.
 * It is injected by the worker and never needs to be supplied by the LLM.
 */
public record AgentExecutionContext(String clientId, String sessionId, String runId) {
}
