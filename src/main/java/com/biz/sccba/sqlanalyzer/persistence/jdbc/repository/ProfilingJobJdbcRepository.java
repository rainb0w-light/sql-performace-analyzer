package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ProfilingJobEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProfilingJobJdbcRepository extends CrudRepository<ProfilingJobEntity, String> {

    @Query("SELECT * FROM sql_analyzer.profiling_job WHERE id = :id AND client_id = :clientId")
    Optional<ProfilingJobEntity> findByIdAndClientId(@Param("id") String id, @Param("clientId") String clientId);

    @Query("SELECT * FROM sql_analyzer.profiling_job WHERE client_id = :clientId ORDER BY created_at DESC")
    List<ProfilingJobEntity> findAllByClientId(@Param("clientId") String clientId);

    @Modifying
    @Query("UPDATE sql_analyzer.profiling_job SET status = 'COMPLETED', lease_until = NULL WHERE id = :id")
    void markCompleted(@Param("id") String id);

    @Modifying
    @Query("UPDATE sql_analyzer.profiling_job SET status = 'FAILED', last_error = :error, lease_until = NULL WHERE id = :id")
    void markFailed(@Param("id") String id, @Param("error") String error);

    @Modifying
    @Query("UPDATE sql_analyzer.profiling_job SET status = 'CANCELLED', lease_until = NULL "
            + "WHERE id = :id AND client_id = :clientId AND status IN ('QUEUED','RUNNING')")
    int markCancelledForClient(@Param("id") String id, @Param("clientId") String clientId);
}
