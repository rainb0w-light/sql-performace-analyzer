package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.RecommendationEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecommendationJdbcRepository extends CrudRepository<RecommendationEntity, String> {

    @Query("SELECT rec.* FROM sql_analyzer.recommendation rec "
            + "JOIN sql_analyzer.analysis_session s ON s.id = rec.session_id "
            + "WHERE rec.session_id = :sessionId AND s.client_id = :clientId "
            + "ORDER BY rec.created_at, rec.id")
    List<RecommendationEntity> findBySessionForClient(@Param("clientId") String clientId,
                                                      @Param("sessionId") String sessionId);

    /** Tenancy enforced inside the statement: affects exactly one row or none. */
    @Modifying
    @Query("UPDATE sql_analyzer.recommendation r SET status = :decision WHERE r.id = :id AND EXISTS ("
            + "SELECT 1 FROM sql_analyzer.analysis_session s WHERE s.id = r.session_id AND s.client_id = :clientId)")
    int applyDecisionForClient(@Param("id") String id, @Param("clientId") String clientId,
                               @Param("decision") String decision);
}
