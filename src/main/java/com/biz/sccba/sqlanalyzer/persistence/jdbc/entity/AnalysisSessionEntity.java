package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "analysis_session")
public class AnalysisSessionEntity extends AssignedIdEntity {
    private String clientId;
    private String title;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
