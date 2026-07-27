package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "analysis_report")
public class AnalysisReportEntity extends AssignedIdEntity {
    private String clientId;
    private String runId;
    private String sessionId;
    private String namespace;
    private String statementId;
    private String schemaVersion;
    private String severity;
    private String reportJson;
    private String markdown;
    private Instant createdAt;
}
