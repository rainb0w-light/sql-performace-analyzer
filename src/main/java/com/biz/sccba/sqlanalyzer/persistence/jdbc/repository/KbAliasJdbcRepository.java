package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbAliasEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbAliasJdbcRepository extends CrudRepository<KbAliasEntity, String> {

    @Query("SELECT a.* FROM sql_analyzer.kb_alias a "
            + "JOIN sql_analyzer.knowledge_source s ON s.id = a.source_id "
            + "WHERE a.active = TRUE AND a.alias_name = :aliasName AND s.client_id = :clientId "
            + "ORDER BY a.created_at")
    List<KbAliasEntity> findActiveForClient(@Param("clientId") String clientId,
                                            @Param("aliasName") String aliasName);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_alias SET active = FALSE WHERE source_id = :sourceId")
    void deactivateBySource(@Param("sourceId") String sourceId);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_alias SET active = TRUE WHERE version_id = :versionId")
    void activateByVersion(@Param("versionId") String versionId);
}
