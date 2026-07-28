package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.AnalysisReportEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnalysisReportJdbcRepository extends CrudRepository<AnalysisReportEntity, String> {

    @Query("SELECT * FROM sql_analyzer.analysis_report WHERE id = :reportId AND client_id = :clientId")
    Optional<AnalysisReportEntity> findByIdForClient(@Param("clientId") String clientId,
                                                     @Param("reportId") String reportId);

    @Query("SELECT * FROM sql_analyzer.analysis_report WHERE run_id = :runId AND client_id = :clientId "
            + "ORDER BY created_at DESC")
    List<AnalysisReportEntity> findByRunForClient(@Param("clientId") String clientId,
                                                  @Param("runId") String runId);

    @Query("SELECT * FROM sql_analyzer.analysis_report WHERE client_id = :clientId "
            + "ORDER BY created_at DESC LIMIT :limit")
    List<AnalysisReportEntity> findAllForClient(@Param("clientId") String clientId,
                                                @Param("limit") int limit);

    @Query("SELECT * FROM sql_analyzer.analysis_report WHERE client_id = :clientId "
            + "ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
    List<AnalysisReportEntity> findPageForClient(@Param("clientId") String clientId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);
}
