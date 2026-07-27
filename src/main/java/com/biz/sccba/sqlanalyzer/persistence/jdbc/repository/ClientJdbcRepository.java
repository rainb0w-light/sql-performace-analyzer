package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ClientEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ClientJdbcRepository extends CrudRepository<ClientEntity, String> {

    @Modifying
    @Query("UPDATE sql_analyzer.client SET last_seen_at = CURRENT_TIMESTAMP WHERE id = :id")
    void touchClient(@Param("id") String id);
}
