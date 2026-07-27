package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "kb_enum_value")
public class KbEnumValueEntity extends AssignedIdEntity {
    private String sourceId;
    private String versionId;
    private String enumCode;
    private String displayName;
    private String meaning;
    private Boolean isValid;
    private String sheetLocator;
    private Boolean active;
    private Instant createdAt;
}
