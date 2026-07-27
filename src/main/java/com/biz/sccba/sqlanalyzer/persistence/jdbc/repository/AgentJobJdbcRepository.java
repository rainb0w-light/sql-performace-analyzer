package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.AgentJobEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * Agent job basic persistence. The claim/lease SQL (the single vendor-sensitive part) is applied
 * through the dialect's JobClaimStrategy in the repository adapter, not here.
 */
public interface AgentJobJdbcRepository extends CrudRepository<AgentJobEntity, String> {

    @Modifying
    @Query("UPDATE sql_analyzer.agent_job SET status = 'COMPLETED', lease_until = NULL WHERE id = :id")
    void markCompleted(@Param("id") String id);

    @Modifying
    @Query("UPDATE sql_analyzer.agent_job SET status = 'FAILED', last_error = :error, lease_until = NULL WHERE id = :id")
    void markFailedNoRetry(@Param("id") String id, @Param("error") String error);

    /** Fails a RUNNING job; requeues it when retries remain. Rows in any other state are untouched. */
    @Modifying
    @Query("UPDATE sql_analyzer.agent_job SET status = CASE WHEN retry_count < 3 THEN 'QUEUED' ELSE 'FAILED' END, "
            + "last_error = :error, lease_until = NULL WHERE id = :id AND status = 'RUNNING'")
    int markFailedWithRetry(@Param("id") String id, @Param("error") String error);

    @Query("SELECT status = 'QUEUED' FROM sql_analyzer.agent_job WHERE id = :id")
    Boolean isQueued(@Param("id") String id);

    @Modifying
    @Query("UPDATE sql_analyzer.agent_job SET status = 'CANCELLED', lease_until = NULL, last_error = 'cancelled by client' "
            + "WHERE run_id = :runId AND status = 'QUEUED'")
    int cancelQueuedForRun(@Param("runId") String runId);
}
