package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.ConversationMessage;

import java.util.List;

/** Conversation message history (auditable projection, independent of the AG-UI event stream). */
public interface MessageRepository {
    ConversationMessage append(String id, String sessionId, String role, String content,
                               String messageType, String runId);

    /** Lists messages of a session after verifying the session belongs to the client (defense in depth). */
    List<ConversationMessage> listForSession(String clientId, String sessionId);
}
