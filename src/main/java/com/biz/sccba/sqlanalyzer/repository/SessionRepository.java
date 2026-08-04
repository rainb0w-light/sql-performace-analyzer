package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.AnalysisSession;

import java.util.List;
import java.util.Optional;

/** Analysis sessions; every read is scoped to the authenticated client. */
public interface SessionRepository {
    AnalysisSession create(String id, String clientId, String title);

    Optional<AnalysisSession> findByIdForClient(String id, String clientId);

    List<AnalysisSession> listForClient(String clientId);

    void touch(String id, String status);

    default boolean belongsToClient(String sessionId, String clientId) {
        return findByIdForClient(sessionId, clientId).isPresent();
    }
}
