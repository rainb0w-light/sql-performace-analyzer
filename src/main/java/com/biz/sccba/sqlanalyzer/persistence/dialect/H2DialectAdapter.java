package com.biz.sccba.sqlanalyzer.persistence.dialect;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * H2 claim strategy: H2 has no {@code SKIP LOCKED} and no {@code UPDATE ... RETURNING}, so the
 * claim is a small transaction — lock the oldest claimable row with {@code SELECT ... FOR UPDATE},
 * mark it RUNNING with a {@code DATEADD} lease, then re-read it. Same observable semantics as the
 * PostgreSQL strategy (oldest claimable row, retry_count incremented, RUNNING with fresh lease);
 * contending workers block briefly on the row lock instead of skipping it, which is acceptable
 * for H2's single-node local/dev role (docs/cloud-code-next-goal.md §3.1).
 */
public final class H2DialectAdapter implements JobClaimStrategy {

    private final TransactionTemplate transactions;

    public H2DialectAdapter(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public Optional<Map<String, Object>> claim(NamedParameterJdbcTemplate jdbc, String qualifiedTable,
                                               String workerId, int leaseMinutes, List<String> returningColumns) {
        Map<String, Object> claimed = transactions.execute(status -> {
            String lockSql = "SELECT id FROM " + qualifiedTable + " WHERE status='QUEUED' "
                    + "OR (status='RUNNING' AND lease_until<CURRENT_TIMESTAMP) "
                    + "ORDER BY created_at LIMIT 1 FOR UPDATE";
            List<Map<String, Object>> locked = jdbc.queryForList(lockSql, new MapSqlParameterSource());
            if (locked.isEmpty()) {
                return null;
            }
            Object id = locked.get(0).get("ID") != null ? locked.get(0).get("ID") : locked.get(0).get("id");
            String updateSql = "UPDATE " + qualifiedTable + " SET status='RUNNING', leased_by=:workerId, "
                    + "lease_until=DATEADD(MINUTE, :leaseMinutes, CURRENT_TIMESTAMP), "
                    + "retry_count=retry_count+1 WHERE id=:id";
            jdbc.update(updateSql, new MapSqlParameterSource()
                    .addValue("workerId", workerId)
                    .addValue("leaseMinutes", Math.max(1, leaseMinutes))
                    .addValue("id", id));
            String selectSql = "SELECT " + String.join(",", returningColumns) + " FROM " + qualifiedTable
                    + " WHERE id=:id";
            List<Map<String, Object>> rows = jdbc.queryForList(selectSql, new MapSqlParameterSource("id", id));
            return rows.isEmpty() ? null : rows.get(0);
        });
        return Optional.ofNullable(claimed);
    }

    @Override
    public String leaseUntilExpression(String minutesParameter) {
        return "DATEADD(MINUTE, :" + minutesParameter + ", CURRENT_TIMESTAMP)";
    }
}
