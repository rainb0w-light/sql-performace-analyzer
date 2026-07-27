package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "shard_def")
public class ShardDefEntity extends AssignedIdEntity {
    private String clientId;
    private String datasource;
    private String schemaName;
    private String logicalTable;
    private String physicalPattern;
    private String shardKey;
    private String secondaryShardKey;
    private String algorithm;
    private String routingExpr;
    private String topologyJson;
    private String source;
    private String confirmedBy;
    private Instant validFrom;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
}
