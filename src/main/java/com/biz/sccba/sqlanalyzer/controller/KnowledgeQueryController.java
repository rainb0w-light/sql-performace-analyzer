package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeQueryService;
import com.biz.sccba.sqlanalyzer.knowledge.retrieval.KnowledgeRetriever;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured knowledge retrieval (always available) plus optional semantic search (enabled with
 * the vector index). Every fact carries evidence: source, version, locator, confidence.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class KnowledgeQueryController {

    private final KnowledgeQueryService query;
    private final ObjectProvider<KnowledgeRetriever> retrieverProvider;
    private final BearerClients bearer;

    public KnowledgeQueryController(KnowledgeQueryService query,
                                    ObjectProvider<KnowledgeRetriever> retrieverProvider,
                                    BearerClients bearer) {
        this.query = query;
        this.retrieverProvider = retrieverProvider;
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
    public Map<String, Object> search(@RequestHeader("Authorization") String authorization,
                                      @RequestParam String q,
                                      @RequestParam(required = false) String sourceId,
                                      @RequestParam(defaultValue = "8") int limit) {
        String clientId = bearer.clientId(authorization);
        var retriever = retrieverProvider.getIfAvailable();
        Map<String, Object> out = new LinkedHashMap<>();
        if (retriever == null || !retriever.available()) {
            out.put("available", false);
            out.put("results", java.util.List.of());
            out.put("note", "语义检索未启用（embedding 未配置）；请使用结构化检索端点。");
            return out;
        }
        out.put("available", true);
        out.put("results", retriever.search(clientId, q, sourceId, limit));
        return out;
    }
}
