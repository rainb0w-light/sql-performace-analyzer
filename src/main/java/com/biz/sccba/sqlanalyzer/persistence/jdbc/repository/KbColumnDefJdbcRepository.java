package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbColumnDefEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbColumnDefJdbcRepository extends CrudRepository<KbColumnDefEntity, String> {

    @Query("SELECT c.* FROM sql_analyzer.kb_column_def c "
            + "JOIN sql_analyzer.knowledge_source s ON s.id = c.source_id "
            + "WHERE c.active = TRUE AND c.table_name = :tableName AND s.client_id = :clientId "
            + "ORDER BY c.created_at")
    List<KbColumnDefEntity> findActiveForClient(@Param("clientId") String clientId,
                                                @Param("tableName") String tableName);

    @Query("SELECT c.* FROM sql_analyzer.kb_column_def c "
            + "JOIN sql_analyzer.knowledge_source s ON s.id = c.source_id "
            + "WHERE c.active = TRUE AND c.table_name = :tableName AND c.column_name = :columnName "
            + "AND s.client_id = :clientId ORDER BY c.created_at")
    List<KbColumnDefEntity> findActiveColumnForClient(@Param("clientId") String clientId,
                                                      @Param("tableName") String tableName,
                                                      @Param("columnName") String columnName);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_column_def SET active = FALSE WHERE source_id = :sourceId")
    void deactivateBySource(@Param("sourceId") String sourceId);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_column_def SET active = TRUE WHERE version_id = :versionId")
    void activateByVersion(@Param("versionId") String versionId);
}
