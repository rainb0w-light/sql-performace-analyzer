package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ClientTokenEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientTokenJdbcRepository extends CrudRepository<ClientTokenEntity, String> {

    @Query("SELECT * FROM sql_analyzer.client_token WHERE token_hash = :tokenHash AND status = 'ACTIVE' "
            + "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)")
    Optional<ClientTokenEntity> findActiveByHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE sql_analyzer.client_token SET last_used_at = CURRENT_TIMESTAMP WHERE id = :id")
    void touchToken(@Param("id") String id);

    @Modifying
    @Query("UPDATE sql_analyzer.client_token SET status = 'REVOKED' WHERE id = :id")
    void revokeToken(@Param("id") String id);
}
