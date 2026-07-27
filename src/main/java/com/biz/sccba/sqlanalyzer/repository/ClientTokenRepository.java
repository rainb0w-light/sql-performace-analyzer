package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.ClientToken;

import java.util.Optional;

/** Bearer token hashes (server only ever stores SHA-256, never the raw token). */
public interface ClientTokenRepository {
    ClientToken create(String id, String clientId, String tokenHash, String tokenPrefix);

    /** Lookup by secret hash; the returned token carries the owning clientId. */
    Optional<ClientToken> findActiveByHash(String tokenHash);

    void touch(String id);

    void revoke(String id);
}
