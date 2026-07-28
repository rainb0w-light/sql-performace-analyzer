package com.biz.sccba.sqlanalyzer.domain.knowledge;

import java.time.Instant;

public record KnowledgeOperation(
        String id,
        String traceId,
        String clientId,
        String actorId,
        String actorType,
        String operationType,
        String sourceId,
        String versionId,
        String runId,
        String sessionId,
        String requestSummaryJson,
        String responseStatus,
        String errorCode,
        long durationMs,
        Integer resultCount,
        Long tokenConsumed,
        Instant createdAt) {
}
