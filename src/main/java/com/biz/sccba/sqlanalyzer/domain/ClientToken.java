package com.biz.sccba.sqlanalyzer.domain;

import java.time.Instant;

public record ClientToken(
        String id,
        String clientId,
        String tokenPrefix,
        String status,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt) {
}
