package com.biz.sccba.sqlanalyzer.domain;

import java.time.Instant;

public record ConversationMessage(
        String id,
        String sessionId,
        String role,
        String content,
        String messageType,
        String runId,
        Instant createdAt) {
}
