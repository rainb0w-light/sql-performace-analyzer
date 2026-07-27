package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.AgentRunEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgentRunJdbcRepository extends CrudRepository<AgentRunEntity, String> {

    @Query("SELECT r.* FROM sql_analyzer.agent_run r "
            + "JOIN sql_analyzer.analysis_session s ON s.id = r.session_id "
            + "WHERE r.session_id = :sessionId AND s.client_id = :clientId ORDER BY r.created_at DESC")
    List<AgentRunEntity> findBySessionForClient(@Param("clientId") String clientId,
                                                @Param("sessionId") String sessionId);

    @Query("SELECT COUNT(*) FROM sql_analyzer.agent_run r "
            + "JOIN sql_analyzer.analysis_session s ON s.id = r.session_id "
            + "WHERE r.id = :id AND s.client_id = :clientId")
    long countBelongingToClient(@Param("id") String id, @Param("clientId") String clientId);

    @Query("SELECT COUNT(*) FROM sql_analyzer.agent_run r "
            + "JOIN sql_analyzer.analysis_session s ON s.id = r.session_id "
            + "WHERE s.client_id = :clientId AND r.status IN ('QUEUED','RUNNING','RETRYING')")
    long countActiveForClient(@Param("clientId") String clientId);

    @Modifying
    @Query("UPDATE sql_analyzer.agent_run SET status = :status, error = :error, "
            + "finished_at = CASE WHEN :status IN ('COMPLETED','FAILED','CANCELLED') "
            + "THEN CURRENT_TIMESTAMP ELSE finished_at END WHERE id = :id")
    void updateRunStatus(@Param("id") String id, @Param("status") String status, @Param("error") String error);
}
