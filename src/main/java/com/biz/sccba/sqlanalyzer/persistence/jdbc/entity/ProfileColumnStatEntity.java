package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** Per-column statistics; Top-K values already obey the sensitivity policy at collection time. */
@Getter
@Setter
@Table(schema = "sql_analyzer", value = "profile_column_stat")
public class ProfileColumnStatEntity extends AssignedIdEntity {
    private String snapshotId;
    private String schemaName;
    private String tableName;
    private String columnName;
    private Double nullRatio;
    private Long approxDistinct;
    private String minValue;
    private String maxValue;
    private String topKJson;
    private String bucketsJson;
    private String quantilesJson;
    private String sensitivityPolicy;
    private Instant collectedAt;
}
