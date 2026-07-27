package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "knowledge_source")
public class KnowledgeSourceEntity extends AssignedIdEntity {
    private String clientId;
    private String name;
    private String sourceType;
    private String currentVersionId;
    private Instant createdAt;
    private Instant updatedAt;
}
