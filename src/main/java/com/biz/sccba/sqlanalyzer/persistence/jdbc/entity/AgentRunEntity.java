package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "agent_run")
public class AgentRunEntity extends AssignedIdEntity {
    private String sessionId;
    private String status;
    private String modelName;
    private String contextSnapshotId;
    private String error;
    private Instant createdAt;
    private Instant finishedAt;
}
