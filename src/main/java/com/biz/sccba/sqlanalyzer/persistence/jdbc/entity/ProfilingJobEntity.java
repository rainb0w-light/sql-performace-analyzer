package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "profiling_job")
public class ProfilingJobEntity extends AssignedIdEntity {
    private String clientId;
    private String datasourceProfileId;
    private String configJson;
    private String status;
    private String leasedBy;
    private Instant leaseUntil;
    private Integer retryCount;
    private String lastError;
    private Instant createdAt;
}
