package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbTableDefEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbTableDefJdbcRepository extends CrudRepository<KbTableDefEntity, String> {

    @Query("SELECT t.* FROM sql_analyzer.kb_table_def t "
            + "JOIN sql_analyzer.knowledge_source s ON s.id = t.source_id "
            + "WHERE t.active = TRUE AND t.table_name = :tableName AND s.client_id = :clientId "
            + "ORDER BY t.created_at")
    List<KbTableDefEntity> findActiveForClient(@Param("clientId") String clientId,
                                               @Param("tableName") String tableName);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_table_def SET active = FALSE WHERE source_id = :sourceId")
    void deactivateBySource(@Param("sourceId") String sourceId);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_table_def SET active = TRUE WHERE version_id = :versionId")
    void activateByVersion(@Param("versionId") String versionId);
}
