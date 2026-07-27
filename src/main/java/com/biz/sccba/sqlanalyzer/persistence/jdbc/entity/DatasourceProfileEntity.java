package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** Read-only target datasource reference. Never stores passwords (credentialEnv names the env var). */
@Getter
@Setter
@Table(schema = "sql_analyzer", value = "datasource_profile")
public class DatasourceProfileEntity extends AssignedIdEntity {
    private String clientId;
    private String name;
    private String dialect;
    private String jdbcUrl;
    private String username;
    private String credentialEnv;
    private Boolean readOnly;
    private Instant createdAt;
}
