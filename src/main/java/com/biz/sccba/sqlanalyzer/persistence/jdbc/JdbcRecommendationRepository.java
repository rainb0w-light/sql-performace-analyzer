package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import com.biz.sccba.sqlanalyzer.domain.Recommendation;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.RecommendationEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.entity.RecommendationFeedbackEntity;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.RecommendationFeedbackJdbcRepository;
import com.biz.sccba.sqlanalyzer.persistence.jdbc.repository.RecommendationJdbcRepository;
import com.biz.sccba.sqlanalyzer.repository.RecommendationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class JdbcRecommendationRepository implements RecommendationRepository {

    private final RecommendationJdbcRepository jdbc;
    private final RecommendationFeedbackJdbcRepository feedback;

    public JdbcRecommendationRepository(RecommendationJdbcRepository jdbc,
                                        RecommendationFeedbackJdbcRepository feedback) {
        this.jdbc = jdbc;
        this.feedback = feedback;
    }

    @Override
    public Recommendation create(Recommendation r) {
        RecommendationEntity entity = new RecommendationEntity();
        entity.setCreatedAt(java.time.Instant.now());
        entity.setId(r.id());
        entity.setRunId(r.runId());
        entity.setSessionId(r.sessionId());
        entity.setType(r.type());
        entity.setTitle(r.title());
        entity.setDescription(r.description());
        entity.setProblem(r.problem());
        entity.setImpact(r.impact());
        entity.setPriority(r.priority());
        entity.setEvidence(r.evidenceJson());
        entity.setSuggestedSql(r.suggestedSql());
        entity.setSuggestedDdl(r.suggestedDdl());
        entity.setConfidence(r.confidence());
        entity.setStatus(r.status());
        entity.setVersion(r.version());
        entity.markNew();
        jdbc.save(entity);
        return r;
    }

    @Override
    public List<Recommendation> listForSession(String clientId, String sessionId) {
        return jdbc.findBySessionForClient(clientId, sessionId).stream().map(e -> new Recommendation(
                e.getId(), e.getRunId(), e.getSessionId(), e.getType(), e.getTitle(), e.getDescription(),
                e.getProblem(), e.getImpact(), e.getPriority(), e.getEvidence(), e.getSuggestedSql(),
                e.getSuggestedDdl(), e.getConfidence() == null ? 0 : e.getConfidence(),
                e.getStatus(), e.getVersion() == null ? 1 : e.getVersion(), e.getCreatedAt())).toList();
    }

    @Override
    @Transactional(transactionManager = "managementTransactionManager")
    public void decide(String id, String clientId, String decision, String category, String reason) {
        if (!"ACCEPTED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException("建议决策只能是 ACCEPTED 或 REJECTED");
        }
        if ("REJECTED".equals(decision) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("拒绝建议时必须填写原因");
        }
        int updated = jdbc.applyDecisionForClient(id, clientId, decision);
        if (updated != 1) throw new IllegalArgumentException("建议不存在或不属于当前客户端");
        RecommendationFeedbackEntity fb = new RecommendationFeedbackEntity();
        fb.setCreatedAt(java.time.Instant.now());
        fb.setId(UUID.randomUUID().toString());
        fb.setRecommendationId(id);
        fb.setClientId(clientId);
        fb.setDecision(decision);
        fb.setCategory(category);
        fb.setReason(reason);
        fb.markNew();
        feedback.save(fb);
    }
}
