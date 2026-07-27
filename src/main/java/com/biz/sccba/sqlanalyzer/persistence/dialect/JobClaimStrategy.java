package com.biz.sccba.sqlanalyzer.persistence.dialect;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Job-queue claim/lease SQL strategy — the one place where the management-database vendor
 * difference is allowed to live (docs/cloud-code-next-goal.md §3.5: {@code SKIP LOCKED} and
 * vendor interval syntax belong to the dialect adapter, never to services or repositories).
 *
 * <p>Claimable = {@code status='QUEUED'} or a {@code RUNNING} row whose lease has expired.
 * Both strategies preserve the historical PostgreSQL semantics: oldest claimable row first,
 * the claim increments {@code retry_count} and marks the row {@code RUNNING} with a fresh lease.
 */
public interface JobClaimStrategy {

    /**
     * Claims the oldest claimable row of {@code qualifiedTable}, marks it RUNNING and returns the
     * requested {@code returningColumns} of the claimed row. Empty when the queue has no
     * claimable row. Implementations must be safe under concurrent workers.
     */
    Optional<Map<String, Object>> claim(NamedParameterJdbcTemplate jdbc, String qualifiedTable,
                                        String workerId, int leaseMinutes, List<String> returningColumns);

    /** SQL expression computing "now + minutes" for lease extensions. */
    String leaseUntilExpression(String minutesParameter);
}
