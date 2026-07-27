package com.biz.sccba.sqlanalyzer.domain;

import java.time.Instant;

public record AgentRun(
        String id,
        String sessionId,
        String status,
        String modelName,
        String contextSnapshotId,
        String error,
        Instant createdAt,
        Instant finishedAt) {
}
