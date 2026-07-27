package com.biz.sccba.sqlanalyzer.domain;

import java.time.Instant;

public record Client(
        String id,
        String name,
        String type,
        String deviceId,
        Instant createdAt,
        Instant lastSeenAt) {
}
