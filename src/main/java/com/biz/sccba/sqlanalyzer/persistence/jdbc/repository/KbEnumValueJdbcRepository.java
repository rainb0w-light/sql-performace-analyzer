package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.KbEnumValueEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbEnumValueJdbcRepository extends CrudRepository<KbEnumValueEntity, String> {

    @Query("SELECT e.* FROM sql_analyzer.kb_enum_value e "
            + "JOIN sql_analyzer.knowledge_source s ON s.id = e.source_id "
            + "WHERE e.active = TRUE AND e.enum_code = :enumCode AND s.client_id = :clientId "
            + "ORDER BY e.created_at")
    List<KbEnumValueEntity> findActiveForClient(@Param("clientId") String clientId,
                                                @Param("enumCode") String enumCode);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_enum_value SET active = FALSE WHERE source_id = :sourceId")
    void deactivateBySource(@Param("sourceId") String sourceId);

    @Modifying
    @Query("UPDATE sql_analyzer.kb_enum_value SET active = TRUE WHERE version_id = :versionId")
    void activateByVersion(@Param("versionId") String versionId);
}
