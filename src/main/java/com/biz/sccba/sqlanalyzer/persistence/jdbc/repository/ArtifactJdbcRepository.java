package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ArtifactEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ArtifactJdbcRepository extends CrudRepository<ArtifactEntity, String> {

    @Query("SELECT * FROM sql_analyzer.artifact WHERE id = :artifactId AND client_id = :clientId")
    Optional<ArtifactEntity> findByIdAndClientId(@Param("artifactId") String artifactId,
                                                 @Param("clientId") String clientId);
}
