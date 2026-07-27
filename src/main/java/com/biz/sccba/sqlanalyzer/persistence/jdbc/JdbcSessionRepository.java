package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.AnalysisSession;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.AnalysisSessionEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.AnalysisSessionJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.SessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcSessionRepository implements SessionRepository {

    private final AnalysisSessionJdbcRepository jdbc;

    public JdbcSessionRepository(AnalysisSessionJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AnalysisSession create(String id, String clientId, String title) {
        AnalysisSessionEntity entity = new AnalysisSessionEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setUpdatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setClientId(clientId);
        entity.setTitle(title);
        entity.setStatus("ACTIVE");
        entity.markNew();
        return toDomain(jdbc.save(entity));
    }

    @Override
    public Optional<AnalysisSession> findByIdForClient(String id, String clientId) {
        return jdbc.findByIdAndClientId(id, clientId).map(JdbcSessionRepository::toDomain);
    }

    @Override
    public List<AnalysisSession> listForClient(String clientId) {
        return jdbc.findAllByClientIdOrderByUpdatedAtDesc(clientId).stream().map(JdbcSessionRepository::toDomain).toList();
    }

    @Override
    public void touch(String id, String status) {
        jdbc.touchSession(id, status);
    }

    static AnalysisSession toDomain(AnalysisSessionEntity e) {
        return new AnalysisSession(e.getId(), e.getClientId(), e.getTitle(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
