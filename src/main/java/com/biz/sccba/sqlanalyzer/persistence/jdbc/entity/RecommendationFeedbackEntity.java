package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Accept/reject feedback. Database CHECK constraints enforce the decision domain and the
 * "REJECTED requires a reason" rule identically on PostgreSQL and H2.
 */
@Getter
@Setter
@Table(schema = "sql_analyzer", value = "recommendation_feedback")
public class RecommendationFeedbackEntity extends AssignedIdEntity {
    private String recommendationId;
    private String clientId;
    private String decision;
    private String category;
    private String reason;
    private Instant createdAt;
}
