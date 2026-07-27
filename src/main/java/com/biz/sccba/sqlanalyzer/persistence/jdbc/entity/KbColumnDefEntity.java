package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "kb_column_def")
public class KbColumnDefEntity extends AssignedIdEntity {
    private String sourceId;
    private String versionId;
    private String tableName;
    private String columnName;
    private String businessMeaning;
    private String dataType;
    private String enumDomain;
    private Boolean isSensitive;
    private Boolean isRequired;
    private String sensitivityPolicy;
    private String sheetLocator;
    private Boolean active;
    private Instant createdAt;
}
