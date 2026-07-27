package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ShardDefEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShardDefJdbcRepository extends CrudRepository<ShardDefEntity, String> {

    @Query("SELECT * FROM sql_analyzer.shard_def WHERE client_id = :clientId AND logical_table = :logicalTable")
    Optional<ShardDefEntity> findByClientAndLogicalTable(@Param("clientId") String clientId,
                                                         @Param("logicalTable") String logicalTable);

    @Query("SELECT * FROM sql_analyzer.shard_def WHERE client_id = :clientId ORDER BY logical_table")
    List<ShardDefEntity> findAllByClient(@Param("clientId") String clientId);

    @Query("SELECT * FROM sql_analyzer.shard_def WHERE client_id = :clientId "
            + "AND datasource = :datasource AND schema_name = :schemaName "
            + "AND logical_table = :logicalTable")
    Optional<ShardDefEntity> findScoped(@Param("clientId") String clientId,
                                        @Param("datasource") String datasource,
                                        @Param("schemaName") String schemaName,
                                        @Param("logicalTable") String logicalTable);
}
