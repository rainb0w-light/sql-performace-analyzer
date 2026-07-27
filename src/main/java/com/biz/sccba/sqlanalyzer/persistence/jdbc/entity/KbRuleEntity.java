package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Table(schema = "sql_analyzer", value = "kb_rule")
public class KbRuleEntity extends AssignedIdEntity {
    private String sourceId;
    private String versionId;
    private String ruleKey;
    private String target;
    private String description;
    private String constraintExpr;
    private Integer priority;
    private Instant effectiveFrom;
    private String sheetLocator;
    private Boolean active;
    private Instant createdAt;
}
