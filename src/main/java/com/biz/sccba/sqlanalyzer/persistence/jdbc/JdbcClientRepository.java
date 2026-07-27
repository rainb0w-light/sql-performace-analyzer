package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.Client;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.ClientEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.ClientJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.ClientRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcClientRepository implements ClientRepository {

    private final ClientJdbcRepository jdbc;

    public JdbcClientRepository(ClientJdbcRepository jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Client create(String id, String name, String type, String deviceId) {
        ClientEntity entity = new ClientEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setName(name);
        entity.setType(type);
        entity.setDeviceId(deviceId);
        entity.markNew();
        return toDomain(jdbc.save(entity));
    }

    @Override
    public Optional<Client> findById(String id) {
        return jdbc.findById(id).map(JdbcClientRepository::toDomain);
    }

    @Override
    public void touch(String id) {
        jdbc.touchClient(id);
    }

    static Client toDomain(ClientEntity e) {
        return new Client(e.getId(), e.getName(), e.getType(), e.getDeviceId(), e.getCreatedAt(), e.getLastSeenAt());
    }
}
