package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "artifact")
public class ArtifactEntity extends AssignedIdEntity {
    private String clientId;
    private String sessionId;
    private String sourceType;
    private String fileName;
    private String mediaType;
    private String sha256;
    private Long byteSize;
    private String status;
    private String metadata;
    private Instant createdAt;
}
