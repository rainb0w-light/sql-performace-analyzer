package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeQueryService;
import com.biz.sccba.sqlanalyzer.knowledge.ActiveKnowledgeSearchService;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeOperationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Structured knowledge retrieval (always available) plus optional semantic search (enabled with
 * the vector index). Every fact carries evidence: source, version, locator, confidence.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class KnowledgeQueryController {

    private final KnowledgeQueryService query;
    private final ActiveKnowledgeSearchService activeSearch;
    private final KnowledgeOperationService operations;
    private final BearerClients bearer;

    public KnowledgeQueryController(KnowledgeQueryService query,
                                    ActiveKnowledgeSearchService activeSearch,
                                    KnowledgeOperationService operations,
                                    BearerClients bearer) {
        this.query = query;
        this.activeSearch = activeSearch;
        this.operations = operations;
        this.bearer = bearer;
    }

    @GetMapping("/tables")
    public Object tables(@RequestHeader("Authorization") String authorization, @RequestParam String name) {
        return query.resolveTables(bearer.clientId(authorization), name);
    }

    @GetMapping("/columns")
    public Object columns(@RequestHeader("Authorization") String authorization,
                          @RequestParam String table,
                          @RequestParam(required = false) String column) {
        return query.columns(bearer.clientId(authorization), table, column);
    }

    @GetMapping("/rules")
    public Object rules(@RequestHeader("Authorization") String authorization, @RequestParam String target) {
        return query.rules(bearer.clientId(authorization), target);
    }

    @GetMapping("/enums")
    public Object enums(@RequestHeader("Authorization") String authorization, @RequestParam String code) {
        return query.enums(bearer.clientId(authorization), code);
    }

    @GetMapping("/aliases")
    public Object aliases(@RequestHeader("Authorization") String authorization, @RequestParam String name) {
        return query.aliases(bearer.clientId(authorization), name);
    }

    @GetMapping("/search")
    public ActiveKnowledgeSearchService.SearchResponse search(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Run-Id", required = false) String runId,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestParam String q,
            @RequestParam(required = false) String sourceId,
            @RequestParam(defaultValue = "8") int limit) {
        var actor = bearer.identity(authorization);
        long started = System.nanoTime();
        try {
            var response = activeSearch.search(actor.clientId(), q, sourceId, limit);
            var hitSources = response.results().stream()
                    .map(ActiveKnowledgeSearchService.SearchHit::sourceId).distinct().toList();
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "RETRIEVE",
                    sourceId, null, runId, sessionId,
                    KnowledgeOperationService.querySummary(q, limit, sourceId, hitSources),
                    "SUCCESS", null, response.durationMs(), response.results().size(), null);
            return response;
        } catch (RuntimeException exception) {
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "RETRIEVE",
                    sourceId, null, runId, sessionId,
                    KnowledgeOperationService.querySummary(q, limit, sourceId, java.util.List.of()),
                    "FAILED", "RETRIEVAL_FAILED",
                    Math.max(0, (System.nanoTime() - started) / 1_000_000), 0, null);
            throw exception;
        }
    }
}
