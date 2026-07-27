package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.repository.RecommendationRepository;
import com.biz.sccba.sqlanalyzer.service.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** recommendations/feedback resource API (docs/contracts/rest-api.md §1). */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class RecommendationController {

    private final RecommendationRepository recommendations;
    private final SessionService sessions;
    private final BearerClients bearer;

    public RecommendationController(RecommendationRepository recommendations, SessionService sessions, BearerClients bearer) {
        this.recommendations = recommendations;
        this.sessions = sessions;
        this.bearer = bearer;
    }

    @GetMapping("/sessions/{sessionId}/recommendations")
    public Object recommendations(@RequestHeader("Authorization") String authorization,
                                  @PathVariable String sessionId) {
        String clientId = bearer.clientId(authorization);
        sessions.getSession(clientId, sessionId);
        return recommendations.listForSession(clientId, sessionId);
    }

    @PostMapping("/recommendations/{recommendationId}/decision")
    public ResponseEntity<Void> decide(@RequestHeader("Authorization") String authorization,
                                       @PathVariable String recommendationId,
                                       @Valid @RequestBody RecommendationDecisionRequest request) {
        recommendations.decide(recommendationId, bearer.clientId(authorization), request.decision(), request.category(), request.reason());
        return ResponseEntity.noContent().build();
    }

    public record RecommendationDecisionRequest(@NotBlank String decision, String category, String reason) {}
}
