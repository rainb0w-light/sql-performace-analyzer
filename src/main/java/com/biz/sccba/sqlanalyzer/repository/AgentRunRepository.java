package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.AgentRun;

import java.util.List;
import java.util.Optional;

/** Agent run lifecycle projection (queryable/auditable, never replaced by AgentState). */
public interface AgentRunRepository {
    AgentRun create(String id, String sessionId, String modelName);

    Optional<AgentRun> findById(String id);

    /** Lists runs of a session after verifying the session belongs to the client. */
    List<AgentRun> listForSession(String clientId, String sessionId);

    /** The tenancy primitive: true only when the run's session belongs to the client. */
    boolean belongsToClient(String id, String clientId);

    int countActiveForClient(String clientId);

    void updateStatus(String id, String status, String error);
}
