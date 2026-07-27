package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.Recommendation;

import java.util.List;

/** Optimization recommendations projected from the standard report; accept/reject feedback. */
public interface RecommendationRepository {
    Recommendation create(Recommendation recommendation);

    /** Lists recommendations of a session after verifying session ownership. */
    List<Recommendation> listForSession(String clientId, String sessionId);

    /** Records a decision; tenancy is enforced inside the statement (affected rows must be 1). */
    void decide(String id, String clientId, String decision, String category, String reason);
}
