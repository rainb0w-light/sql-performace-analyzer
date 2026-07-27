package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.RunEventEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Run event persistence. Insert-with-generated-key and cursor replay are portable; the id
 * sequence is identity-generated on both management databases.
 */
public interface RunEventJdbcRepository extends CrudRepository<RunEventEntity, Long> {

    @Query("SELECT e.* FROM sql_analyzer.run_event e "
            + "JOIN sql_analyzer.agent_run r ON r.id = e.run_id "
            + "JOIN sql_analyzer.analysis_session s ON s.id = r.session_id "
            + "WHERE e.run_id = :runId AND e.id > :lastEventId AND s.client_id = :clientId "
            + "ORDER BY e.id")
    List<RunEventEntity> afterForClient(@Param("clientId") String clientId,
                                        @Param("runId") String runId,
                                        @Param("lastEventId") long lastEventId);

    @Query("SELECT COUNT(*) FROM sql_analyzer.run_event e "
            + "JOIN sql_analyzer.agent_run r ON r.id = e.run_id "
            + "JOIN sql_analyzer.analysis_session s ON s.id = r.session_id "
            + "WHERE e.run_id = :runId AND s.client_id = :clientId")
    long countForClientRun(@Param("clientId") String clientId, @Param("runId") String runId);
}
