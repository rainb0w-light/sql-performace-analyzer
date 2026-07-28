package com.biz.sccba.sqlanalyzer.repository;

import java.util.Optional;

/**
 * Agent job queue with lease/retry semantics. Claiming is the one vendor-sensitive operation:
 * the SQL difference (PostgreSQL SKIP LOCKED vs H2 row-lock claim) lives behind the dialect's
 * JobClaimStrategy, never here or in services (docs/cloud-code-next-goal.md §3.5).
 */
public interface AgentJobRepository {
    void enqueue(String id, String runId, String payloadJson);

    Optional<Job> claim(String workerId);

    void complete(String id);

    /** Returns true when the job was requeued for another attempt. */
    boolean fail(String id, String error);

    /** Marks the job FAILED without any retry (used by streaming AG-UI runs). */
    void failNoRetry(String id, String error);

    /** Extends the claim lease for long-running asynchronous executions. */
    void extendLease(String id, int minutes);

    /** Requests cancellation for a queued/running job; executors must observe the Run state. */
    boolean cancelQueuedForRun(String runId);

    record Job(String id, String runId, String payloadJson) {}
}
