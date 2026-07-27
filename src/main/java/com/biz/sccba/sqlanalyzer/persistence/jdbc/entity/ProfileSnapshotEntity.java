package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** Immutable profiling snapshot; ownership derives from the creating job's client. */
@Getter
@Setter
@Table(schema = "sql_analyzer", value = "profile_snapshot")
public class ProfileSnapshotEntity extends AssignedIdEntity {
    private String jobId;
    private String datasourceProfileId;
    private String status;
    private String configJson;
    private Instant startedAt;
    private Instant finishedAt;
}
