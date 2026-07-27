package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.ConversationMessage;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ConversationMessageEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.ConversationMessageJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.MessageRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Message append is idempotent by primary key (replays of the same message id keep the original
 * row). The former PostgreSQL {@code ON CONFLICT DO NOTHING} is expressed portably as
 * insert-or-ignore via the duplicate-key exception, so behavior is identical on H2.
 */
@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcMessageRepository implements MessageRepository {

    private final ConversationMessageJdbcRepository jdbc;

    public JdbcMessageRepository(ConversationMessageJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ConversationMessage append(String id, String sessionId, String role, String content,
                                      String messageType, String runId) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setMessageType(messageType);
        entity.setRunId(runId);
        entity.markNew();
        try {
            jdbc.save(entity);
        } catch (DuplicateKeyException ignored) {
            // idempotent replay: the original row wins
        }
        return toDomain(jdbc.findById(id).orElseThrow());
    }

    @Override
    public List<ConversationMessage> listForSession(String clientId, String sessionId) {
        return jdbc.findBySessionForClient(clientId, sessionId).stream().map(JdbcMessageRepository::toDomain).toList();
    }

    static ConversationMessage toDomain(ConversationMessageEntity e) {
        return new ConversationMessage(e.getId(), e.getSessionId(), e.getRole(), e.getContent(),
                e.getMessageType(), e.getRunId(), e.getCreatedAt());
    }
}
