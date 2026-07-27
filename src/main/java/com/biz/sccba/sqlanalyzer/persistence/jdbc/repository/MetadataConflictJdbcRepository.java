package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.MetadataConflictEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MetadataConflictJdbcRepository extends CrudRepository<MetadataConflictEntity, String> {

    @Query("SELECT * FROM sql_analyzer.metadata_conflict WHERE client_id = :clientId AND status = 'PENDING' "
            + "ORDER BY created_at")
    List<MetadataConflictEntity> findPendingForClient(@Param("clientId") String clientId);

    @Modifying
    @Query("UPDATE sql_analyzer.metadata_conflict SET status = :status WHERE id = :id AND client_id = :clientId")
    int resolveForClient(@Param("clientId") String clientId, @Param("id") String id, @Param("status") String status);
}
