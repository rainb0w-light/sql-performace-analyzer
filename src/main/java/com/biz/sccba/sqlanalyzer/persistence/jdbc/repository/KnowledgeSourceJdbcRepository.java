package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KnowledgeSourceEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeSourceJdbcRepository extends CrudRepository<KnowledgeSourceEntity, String> {

    @Query("SELECT * FROM sql_analyzer.knowledge_source WHERE id = :sourceId AND client_id = :clientId")
    Optional<KnowledgeSourceEntity> findByIdAndClientId(@Param("sourceId") String sourceId,
                                                        @Param("clientId") String clientId);

    @Query("SELECT * FROM sql_analyzer.knowledge_source WHERE client_id = :clientId ORDER BY created_at")
    List<KnowledgeSourceEntity> findAllByClientId(@Param("clientId") String clientId);

    @Modifying
    @Query("UPDATE sql_analyzer.knowledge_source SET current_version_id = :versionId, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = :sourceId")
    void updateCurrentVersion(@Param("sourceId") String sourceId, @Param("versionId") String versionId);
}
