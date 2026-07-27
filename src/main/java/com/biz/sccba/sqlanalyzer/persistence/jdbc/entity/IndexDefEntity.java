package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "index_def")
public class IndexDefEntity extends AssignedIdEntity {
    private String clientId;
    private String datasource;
    private String schemaName;
    private String tableName;
    private String indexName;
    private String indexType;
    private String columnsJson;
    private Long cardinality;
    private Long usageCount;
    private String source;
    private String confirmedBy;
    private Instant validFrom;
    private Integer version;
    private String checksum;
    private Instant createdAt;
    private Instant updatedAt;
}
