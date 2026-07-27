package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "metadata_conflict")
public class MetadataConflictEntity extends AssignedIdEntity {
    private String clientId;
    private String entityType;
    private String entityKey;
    private String existingJson;
    private String incomingJson;
    private String source;
    private String status;
    private Instant createdAt;
}
