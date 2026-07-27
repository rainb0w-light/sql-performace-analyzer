package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.ClientToken;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ClientTokenEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.ClientTokenJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.ClientTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcClientTokenRepository implements ClientTokenRepository {

    private final ClientTokenJdbcRepository jdbc;

    public JdbcClientTokenRepository(ClientTokenJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ClientToken create(String id, String clientId, String tokenHash, String tokenPrefix) {
        ClientTokenEntity entity = new ClientTokenEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setClientId(clientId);
        entity.setTokenHash(tokenHash);
        entity.setTokenPrefix(tokenPrefix);
        entity.setStatus("ACTIVE");
        entity.markNew();
        return toDomain(jdbc.save(entity));
    }

    @Override
    public Optional<ClientToken> findActiveByHash(String tokenHash) {
        return jdbc.findActiveByHash(tokenHash).map(JdbcClientTokenRepository::toDomain);
    }

    @Override
    public void touch(String id) {
        jdbc.touchToken(id);
    }

    @Override
    public void revoke(String id) {
        jdbc.revokeToken(id);
    }

    static ClientToken toDomain(ClientTokenEntity e) {
        return new ClientToken(e.getId(), e.getClientId(), e.getTokenPrefix(), e.getStatus(),
                e.getCreatedAt(), e.getLastUsedAt(), e.getExpiresAt());
    }
}
