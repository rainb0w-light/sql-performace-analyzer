package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbRuleEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbRuleJdbcRepository extends CrudRepository<KbRuleEntity, String> {

    @Query("SELECT r.* FROM sql_analyzer.kb_rule r "
            + "JOIN sql_analyzer.knowledge_source s ON s.id = r.source_id "
            + "WHERE r.active = TRUE AND r.target = :target AND s.client_id = :clientId "
            + "ORDER BY r.priority")
    List<KbRuleEntity> findActiveForClient(@Param("clientId") String clientId, @Param("target") String target);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_rule SET active = FALSE WHERE source_id = :sourceId")
    void deactivateBySource(@Param("sourceId") String sourceId);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_rule SET active = TRUE WHERE version_id = :versionId")
    void activateByVersion(@Param("versionId") String versionId);
}
