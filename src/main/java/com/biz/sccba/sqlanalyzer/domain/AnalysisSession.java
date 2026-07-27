package com.biz.sccba.sqlanalyzer.domain;

import java.time.Instant;

public record AnalysisSession(
        String id,
        String clientId,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
