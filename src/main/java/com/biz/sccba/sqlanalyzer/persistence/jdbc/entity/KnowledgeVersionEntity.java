package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "knowledge_version")
public class KnowledgeVersionEntity extends AssignedIdEntity {
    private String sourceId;
    private Integer versionNo;
    private String status;
    private String artifactId;
    private String previewJson;
    private String errorJson;
    private String publishedBy;
    private Instant publishedAt;
    private Instant createdAt;
}
