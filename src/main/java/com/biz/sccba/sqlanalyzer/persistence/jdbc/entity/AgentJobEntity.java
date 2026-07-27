package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "agent_job")
public class AgentJobEntity extends AssignedIdEntity {
    private String runId;
    private String status;
    private String payload;
    private String leasedBy;
    private Instant leaseUntil;
    private Integer retryCount;
    private String lastError;
    private Instant createdAt;
}
