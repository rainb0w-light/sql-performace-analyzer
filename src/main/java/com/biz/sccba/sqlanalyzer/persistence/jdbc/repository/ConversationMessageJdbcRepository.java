package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ConversationMessageEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationMessageJdbcRepository extends CrudRepository<ConversationMessageEntity, String> {

    @Query("SELECT m.* FROM sql_analyzer.conversation_message m "
            + "JOIN sql_analyzer.analysis_session s ON s.id = m.session_id "
            + "WHERE m.session_id = :sessionId AND s.client_id = :clientId "
            + "ORDER BY m.created_at, m.id")
    List<ConversationMessageEntity> findBySessionForClient(@Param("clientId") String clientId,
                                                           @Param("sessionId") String sessionId);
}
