package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.RunEventJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.RunEventRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * Event persistence with database-generated monotonic ids. The insert uses JDBC generated keys
 * (portable: SERIAL/BIGSERIAL on PostgreSQL, IDENTITY on H2) instead of the former
 * PostgreSQL-only {@code RETURNING}.
 */
@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcRunEventRepository implements RunEventRepository {

    private final RunEventJdbcRepository jdbc;
    private final JdbcTemplate jdbcTemplate;

    public JdbcRunEventRepository(RunEventJdbcRepository jdbc,
                                  @Qualifier("managementJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long append(String runId, String type, String payloadJson) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sql_analyzer.run_event(run_id, type, payload) VALUES (?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, runId);
            ps.setString(2, type);
            ps.setString(3, payloadJson);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    @Override
    public List<RunEvent> after(String clientId, String runId, long lastEventId) {
        return jdbc.afterForClient(clientId, runId, lastEventId).stream()
                .map(e -> new RunEvent(e.getId(), e.getRunId(), e.getType(), e.getPayload()))
                .toList();
    }
}
