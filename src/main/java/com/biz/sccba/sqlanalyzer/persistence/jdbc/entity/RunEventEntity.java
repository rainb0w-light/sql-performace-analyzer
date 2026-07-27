package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Persisted AG-UI event. The id is a database-generated monotonic identity (BIGSERIAL on
 * PostgreSQL, IDENTITY on H2) and doubles as the SSE resume cursor.
 */
@Getter
@Setter
@Table(schema = "sql_analyzer", value = "run_event")
public class RunEventEntity {
    @Id
    private Long id;
    private String runId;
    private String type;
    private String payload;
    private Instant createdAt;
}
