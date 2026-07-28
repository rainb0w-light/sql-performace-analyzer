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

    @Query("SELECT * FROM sql_analyzer.artifact WHERE client_id = :clientId "
            + "AND source_type = :sourceType AND sha256 = :sha256 ORDER BY created_at FETCH FIRST 1 ROW ONLY")
    Optional<ArtifactEntity> findBySha256AndClientId(@Param("clientId") String clientId,
                                                     @Param("sourceType") String sourceType,
                                                     @Param("sha256") String sha256);
}
