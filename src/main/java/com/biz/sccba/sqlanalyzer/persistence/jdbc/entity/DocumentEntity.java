package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "document")
public class DocumentEntity extends AssignedIdEntity {
    private String artifactId;
    private String documentType;
    private String parserName;
    private String parserVersion;
    private String normalizedText;
    private String structuredData;
    private String status;
    private Instant createdAt;
}
