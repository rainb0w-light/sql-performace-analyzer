package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "client_token")
public class ClientTokenEntity extends AssignedIdEntity {
    private String clientId;
    private String tokenHash;
    private String tokenPrefix;
    private String status;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
}
