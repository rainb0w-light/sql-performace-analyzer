package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KnowledgeVersionEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeVersionJdbcRepository extends CrudRepository<KnowledgeVersionEntity, String> {

    @Query("SELECT v.* FROM sql_analyzer.knowledge_version v "
            + "JOIN sql_analyzer.knowledge_source s ON s.id = v.source_id "
            + "WHERE v.id = :versionId AND s.client_id = :clientId")
    Optional<KnowledgeVersionEntity> findByIdForClient(@Param("clientId") String clientId,
                                                       @Param("versionId") String versionId);

    @Query("SELECT * FROM sql_analyzer.knowledge_version WHERE source_id = :sourceId ORDER BY version_no DESC")
    List<KnowledgeVersionEntity> findBySourceOrderByVersionNoDesc(@Param("sourceId") String sourceId);

    @Query("SELECT COALESCE(MAX(version_no), 0) FROM sql_analyzer.knowledge_version WHERE source_id = :sourceId")
    int maxVersionNo(@Param("sourceId") String sourceId);

    @Modifying
    @Query("UPDATE sql_analyzer.knowledge_version SET status = 'PUBLISHED', published_by = :publishedBy, "
            + "published_at = CURRENT_TIMESTAMP WHERE id = :versionId")
    void markPublished(@Param("versionId") String versionId, @Param("publishedBy") String publishedBy);

    @Modifying
    @Query("UPDATE sql_analyzer.knowledge_version SET status = 'ROLLED_BACK' WHERE id = :versionId AND status = 'PUBLISHED'")
    void markRolledBack(@Param("versionId") String versionId);
}
