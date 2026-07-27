package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "kb_table_def")
public class KbTableDefEntity extends AssignedIdEntity {
    private String sourceId;
    private String versionId;
    private String datasource;
    private String schemaName;
    private String tableName;
    private String businessName;
    private String purpose;
    private String owner;
    private String dataDomain;
    private String sheetLocator;
    private Boolean active;
    private Instant createdAt;
}
