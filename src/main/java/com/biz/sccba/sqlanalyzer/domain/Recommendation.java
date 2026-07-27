package com.biz.sccba.sqlanalyzer.domain;

import java.time.Instant;

public record Recommendation(
        String id,
        String runId,
        String sessionId,
        String type,
        String title,
        String description,
        String problem,
        String impact,
        String priority,
        String evidenceJson,
        String suggestedSql,
        String suggestedDdl,
        double confidence,
        String status,
        int version,
        Instant createdAt) {
}
