package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.repository.IdempotencyRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Idempotency store over the composite key (client_id, idempotency_key). The table has no
 * surrogate id, so it is accessed with NamedParameterJdbcTemplate (the sanctioned tool for shapes
 * Spring Data JDBC does not express cleanly, docs/cloud-code-next-goal.md §3.2).
 */
@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private static final RowMapper<Record> ROW_MAPPER = (rs, n) -> new Record(
            rs.getString("client_id"), rs.getString("idempotency_key"), rs.getString("request_digest"),
            rs.getString("method"), rs.getString("path"), rs.getInt("response_status"),
            rs.getString("response_body"), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("expires_at")));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcIdempotencyRepository(@Qualifier("managementNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Record> find(String clientId, String idempotencyKey) {
        List<Record> rows = jdbc.query("SELECT * FROM sql_analyzer.idempotency_record "
                        + "WHERE client_id = :clientId AND idempotency_key = :key AND expires_at > CURRENT_TIMESTAMP",
                new MapSqlParameterSource().addValue("clientId", clientId).addValue("key", idempotencyKey),
                ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void save(Record record) {
        try {
            jdbc.update("INSERT INTO sql_analyzer.idempotency_record(client_id, idempotency_key, request_digest, "
                            + "method, path, response_status, response_body, expires_at) "
                            + "VALUES (:clientId, :key, :digest, :method, :path, :status, :body, :expiresAt)",
                    new MapSqlParameterSource()
                            .addValue("clientId", record.clientId())
                            .addValue("key", record.idempotencyKey())
                            .addValue("digest", record.requestDigest())
                            .addValue("method", record.method() == null ? "POST" : record.method())
                            .addValue("path", record.path() == null ? "" : record.path())
                            .addValue("status", record.responseStatus())
                            .addValue("body", record.responseBody() == null ? "{}" : record.responseBody())
                            .addValue("expiresAt", Timestamp.from(record.expiresAt())));
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("幂等键已被使用", e);
        }
    }

    @Override
    public int purgeExpired(Instant now) {
        return jdbc.update("DELETE FROM sql_analyzer.idempotency_record WHERE expires_at <= :now",
                new MapSqlParameterSource("now", Timestamp.from(now)));
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
