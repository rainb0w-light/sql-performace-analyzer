package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "conversation_message")
public class ConversationMessageEntity extends AssignedIdEntity {
    private String sessionId;
    private String role;
    private String content;
    private String messageType;
    private String runId;
    private Instant createdAt;
}
