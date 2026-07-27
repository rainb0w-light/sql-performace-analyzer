package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.AnalysisSessionEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnalysisSessionJdbcRepository extends CrudRepository<AnalysisSessionEntity, String> {

    @Query("SELECT * FROM sql_analyzer.analysis_session WHERE id = :id AND client_id = :clientId")
    Optional<AnalysisSessionEntity> findByIdAndClientId(@Param("id") String id, @Param("clientId") String clientId);

    @Query("SELECT * FROM sql_analyzer.analysis_session WHERE client_id = :clientId ORDER BY updated_at DESC")
    List<AnalysisSessionEntity> findAllByClientIdOrderByUpdatedAtDesc(@Param("clientId") String clientId);

    @Modifying
    @Query("UPDATE sql_analyzer.analysis_session SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void touchSession(@Param("id") String id, @Param("status") String status);
}
