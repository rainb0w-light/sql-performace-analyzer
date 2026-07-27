package com.biz.sccba.sqlanalyzer.persistence.jdbc.repository;

import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.RecommendationFeedbackEntity;
import org.springframework.data.repository.CrudRepository;

public interface RecommendationFeedbackJdbcRepository extends CrudRepository<RecommendationFeedbackEntity, String> {
}
