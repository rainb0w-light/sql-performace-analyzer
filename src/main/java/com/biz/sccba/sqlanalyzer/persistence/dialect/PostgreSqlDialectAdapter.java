package com.biz.sccba.sqlanalyzer.persistence.dialect;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PostgreSQL claim strategy: the historical single-statement pattern — a {@code FOR UPDATE
 * SKIP LOCKED} CTE picks the oldest claimable row, the UPDATE marks it RUNNING and
 * {@code RETURNING} projects it (preserved verbatim from the former {@code PostgresAgentJobDao}
 * and {@code PostgresProfilingDao}, so concurrency behavior on PostgreSQL is unchanged).
 */
public final class PostgreSqlDialectAdapter implements JobClaimStrategy {

    @Override
    public Optional<Map<String, Object>> claim(NamedParameterJdbcTemplate jdbc, String qualifiedTable,
                                               String workerId, int leaseMinutes, List<String> returningColumns) {
        String returning = returningColumns.stream().map(c -> "j." + c)
                .collect(java.util.stream.Collectors.joining(","));
        String sql = "WITH candidate AS (SELECT id FROM " + qualifiedTable + " WHERE status='QUEUED' "
                + "OR (status='RUNNING' AND lease_until<CURRENT_TIMESTAMP) ORDER BY created_at "
                + "FOR UPDATE SKIP LOCKED LIMIT 1) "
                + "UPDATE " + qualifiedTable + " j SET status='RUNNING', leased_by=:workerId, "
                + "lease_until=CURRENT_TIMESTAMP + make_interval(mins => :leaseMinutes), "
                + "retry_count=j.retry_count+1 "
                + "FROM candidate c WHERE j.id=c.id "
                + "RETURNING " + returning;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workerId", workerId)
                .addValue("leaseMinutes", Math.max(1, leaseMinutes));
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public String leaseUntilExpression(String minutesParameter) {
        return "CURRENT_TIMESTAMP + make_interval(mins => :" + minutesParameter + ")";
    }
}
