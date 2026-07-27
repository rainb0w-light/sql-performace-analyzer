package com.biz.sccba.sqlanalyzer.agent;

import java.util.List;

/** Selects bounded, session-owned context before invoking the AgentScope runtime. */
public interface ContextBuilder {
    String build(String clientId, String sessionId, String userContent, List<String> artifactIds);
}
