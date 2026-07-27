package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "recommendation")
public class RecommendationEntity extends AssignedIdEntity {
    private String runId;
    private String sessionId;
    private String type;
    private String title;
    private String description;
    private String problem;
    private String impact;
    private String priority;
    private String evidence;
    private String suggestedSql;
    private String suggestedDdl;
    private Double confidence;
    private String status;
    private Integer version;
    private Instant createdAt;
}
