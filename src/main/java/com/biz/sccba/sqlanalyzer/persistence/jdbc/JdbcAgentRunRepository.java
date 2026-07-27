package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.AgentRun;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.AgentRunEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.AgentRunJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentRunRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcAgentRunRepository implements AgentRunRepository {

    private final AgentRunJdbcRepository jdbc;

    public JdbcAgentRunRepository(AgentRunJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AgentRun create(String id, String sessionId, String modelName) {
        AgentRunEntity entity = new AgentRunEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setSessionId(sessionId);
        entity.setStatus("QUEUED");
        entity.setModelName(modelName);
        entity.markNew();
        return toDomain(jdbc.save(entity));
    }

    @Override
    public Optional<AgentRun> findById(String id) {
        return jdbc.findById(id).map(JdbcAgentRunRepository::toDomain);
    }

    @Override
    public List<AgentRun> listForSession(String clientId, String sessionId) {
        return jdbc.findBySessionForClient(clientId, sessionId).stream().map(JdbcAgentRunRepository::toDomain).toList();
    }

    @Override
    public boolean belongsToClient(String id, String clientId) {
        return jdbc.countBelongingToClient(id, clientId) == 1;
    }

    @Override
    public int countActiveForClient(String clientId) {
        return (int) jdbc.countActiveForClient(clientId);
    }

    @Override
    public void updateStatus(String id, String status, String error) {
        jdbc.updateRunStatus(id, status, error);
    }

    static AgentRun toDomain(AgentRunEntity e) {
        return new AgentRun(e.getId(), e.getSessionId(), e.getStatus(), e.getModelName(),
                e.getContextSnapshotId(), e.getError(), e.getCreatedAt(), e.getFinishedAt());
    }
}
