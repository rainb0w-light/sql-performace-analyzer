package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.persistence.dialect.JobClaimStrategy;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.AgentJobEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.AgentJobJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.AgentJobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcAgentJobRepository implements AgentJobRepository {

    private static final String TABLE = "sql_analyzer.agent_job";
    private static final int DEFAULT_LEASE_MINUTES = 5;

    private final AgentJobJdbcRepository jdbc;
    private final JobClaimStrategy claimStrategy;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcAgentJobRepository(AgentJobJdbcRepository jdbc, JobClaimStrategy claimStrategy,
                                  @Qualifier("managementNamedParameterJdbcTemplate") NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.claimStrategy = claimStrategy;
        this.namedJdbc = namedJdbc;
    }

    @Override
    public void enqueue(String id, String runId, String payloadJson) {
        AgentJobEntity entity = new AgentJobEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(id);
        entity.setRunId(runId);
        entity.setStatus("QUEUED");
        entity.setPayload(payloadJson);
        entity.setRetryCount(0);
        entity.markNew();
        jdbc.save(entity);
    }

    @Override
    public Optional<Job> claim(String workerId) {
        Optional<Map<String, Object>> claimed = claimStrategy.claim(namedJdbc, TABLE, workerId,
                DEFAULT_LEASE_MINUTES, List.of("id", "run_id", "payload"));
        return claimed.map(row -> {
            Map<String, Object> ci = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            ci.putAll(row);
            return new Job(String.valueOf(ci.get("id")), String.valueOf(ci.get("run_id")),
                    String.valueOf(ci.get("payload")));
        });
    }

    @Override
    public void complete(String id) {
        jdbc.markCompleted(id);
    }

    @Override
    public boolean fail(String id, String error) {
        int updated = jdbc.markFailedWithRetry(id, error);
        if (updated != 1) return false; // terminal or absent jobs are never requeued
        return Boolean.TRUE.equals(jdbc.isQueued(id));
    }

    @Override
    public void failNoRetry(String id, String error) {
        jdbc.markFailedNoRetry(id, error);
    }

    @Override
    public void extendLease(String id, int minutes) {
        namedJdbc.update("UPDATE " + TABLE + " SET lease_until = "
                        + claimStrategy.leaseUntilExpression("leaseMinutes") + " WHERE id = :id AND status = 'RUNNING'",
                new MapSqlParameterSource()
                        .addValue("leaseMinutes", Math.max(1, minutes))
                        .addValue("id", id));
    }

    @Override
    public boolean cancelQueuedForRun(String runId) {
        return jdbc.cancelQueuedForRun(runId) == 1;
    }
}
