package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "client")
public class ClientEntity extends AssignedIdEntity {
    private String name;
    private String type;
    private String deviceId;
    private Instant createdAt;
    private Instant lastSeenAt;
}
