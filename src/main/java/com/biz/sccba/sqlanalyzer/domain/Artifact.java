package com.biz.sccba.sqlanalyzer.domain;

import java.time.Instant;

public record Artifact(
        String id,
        String clientId,
        String sessionId,
        String sourceType,
        String fileName,
        String mediaType,
        String sha256,
        long byteSize,
        String status,
        String metadataJson,
        Instant createdAt) {
}
