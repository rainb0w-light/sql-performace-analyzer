package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "kb_alias")
public class KbAliasEntity extends AssignedIdEntity {
    private String sourceId;
    private String versionId;
    private String aliasType;
    private String aliasName;
    private String targetName;
    private String sheetLocator;
    private Boolean active;
    private Instant createdAt;
}
