package com.biz.sccba.sqlanalyzer.repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Idempotency-Key store (docs/contracts/rest-api.md §4, docs/cloud-code-next-goal.md §3.4).
 * The key space is per client: (clientId, idempotencyKey) identifies a stored response; replay
 * of an identical request returns it, a different request digest under the same key conflicts.
 */
public interface IdempotencyRepository {

    Optional<Record> find(String clientId, String idempotencyKey);

    void save(Record record);

    /** Removes expired entries; returns the number of purged rows. */
    int purgeExpired(Instant now);

    record Record(String clientId, String idempotencyKey, String requestDigest, String method,
                  String path, int responseStatus, String responseBody, Instant createdAt,
                  Instant expiresAt) {}
}
