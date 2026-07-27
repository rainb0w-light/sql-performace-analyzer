package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.DatasourceProfileEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DatasourceProfileJdbcRepository extends CrudRepository<DatasourceProfileEntity, String> {

    @Query("SELECT * FROM sql_analyzer.datasource_profile WHERE id = :id AND client_id = :clientId")
    Optional<DatasourceProfileEntity> findByIdAndClientId(@Param("id") String id, @Param("clientId") String clientId);

    @Query("SELECT * FROM sql_analyzer.datasource_profile WHERE client_id = :clientId ORDER BY created_at DESC")
    List<DatasourceProfileEntity> findAllByClientId(@Param("clientId") String clientId);
}
