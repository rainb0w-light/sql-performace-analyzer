package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.IndexDefEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IndexDefJdbcRepository extends CrudRepository<IndexDefEntity, String> {

    @Query("SELECT * FROM sql_analyzer.index_def WHERE client_id = :clientId AND table_name = :tableName "
            + "AND index_name = :indexName")
    Optional<IndexDefEntity> findByClientTableIndex(@Param("clientId") String clientId,
                                                    @Param("tableName") String tableName,
                                                    @Param("indexName") String indexName);

    @Query("SELECT * FROM sql_analyzer.index_def WHERE client_id = :clientId AND table_name = :tableName "
            + "ORDER BY index_name")
    List<IndexDefEntity> findByClientAndTable(@Param("clientId") String clientId,
                                              @Param("tableName") String tableName);

    @Query("SELECT * FROM sql_analyzer.index_def WHERE client_id = :clientId "
            + "AND datasource = :datasource AND schema_name = :schemaName AND table_name = :tableName "
            + "ORDER BY index_name")
    List<IndexDefEntity> findScoped(@Param("clientId") String clientId,
                                    @Param("datasource") String datasource,
                                    @Param("schemaName") String schemaName,
                                    @Param("tableName") String tableName);

    @Query("SELECT * FROM sql_analyzer.index_def WHERE client_id = :clientId "
            + "AND datasource = :datasource AND schema_name = :schemaName "
            + "AND table_name = :tableName AND index_name = :indexName")
    Optional<IndexDefEntity> findScopedIndex(@Param("clientId") String clientId,
                                             @Param("datasource") String datasource,
                                             @Param("schemaName") String schemaName,
                                             @Param("tableName") String tableName,
                                             @Param("indexName") String indexName);
}
