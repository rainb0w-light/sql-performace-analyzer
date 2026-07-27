package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.analysis.AnalysisRunOrchestrator;
import com.biz.sccba.sqlanalyzer.analysis.IdempotentAnalysisRunService;
import com.biz.sccba.sqlanalyzer.mybatis.DynamicNodeCatalog;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * mapper-statements resource API (docs/contracts/rest-api.md §1). Scenario planning and analysis
 * are driven entirely server-side (docs/cloud-code-next-goal.md §8): the client sends only the
 * mapper artifact, statementId and optional samples — knowledge, profiles, indexes and shards
 * are loaded by the server for the authenticated tenant. Any client-provided knowledge/profile/
 * index/shard arrays are ignored (untrusted input).
 */
@RestController
@RequestMapping("/api/v1/mapper-statements")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class MapperStatementController {

    private final IdempotentAnalysisRunService analysisRuns;
    private final DynamicNodeCatalog catalog;
    private final ArtifactService artifacts;
    private final BearerClients bearer;

    public MapperStatementController(IdempotentAnalysisRunService analysisRuns, DynamicNodeCatalog catalog,
                                     ArtifactService artifacts, BearerClients bearer) {
        this.analysisRuns = analysisRuns;
        this.catalog = catalog;
        this.artifacts = artifacts;
        this.bearer = bearer;
    }

    /** Structural statement inventory of an uploaded mapper artifact. */
    @GetMapping
    public Object statements(@RequestHeader("Authorization") String authorization,
                             @RequestParam String artifactId) {
        String clientId = bearer.clientId(authorization);
        byte[] content = artifacts.read(clientId, artifactId);
        var structure = catalog.scan(new String(content, StandardCharsets.UTF_8));
        return structure.statements().stream().map(s -> Map.of(
                "namespace", nullSafe(structure.namespace()),
                "statementId", nullSafe(s.statementId()),
                "statementType", nullSafe(s.statementType()),
                "dynamicNodes", s.nodes().size(),
                "dollarInterpolations", s.dollarExpressions())).toList();
    }

    /**
     * Full analysis: server-side context resolution → scenario matrix (official BoundSql) →
     * validated standard report → persistence → recommendation projection → AG-UI events.
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisRunOrchestrator.RunHandle> analyze(
                          @RequestHeader(value = "Authorization", required = false) String authorization,
                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                          @Valid @RequestBody AnalyzeRequest request) {
        String clientId = bearer.clientId(authorization);
        var handle = analysisRuns.start(clientId, idempotencyKey, new AnalysisRunOrchestrator.Command(
                request.artifactId(), request.statementId(), request.datasourceProfileId(),
                request.projectId(), request.moduleId(), request.sessionId(),
                request.mybatisConfigXml(), request.databaseId(), request.schemaName(), request.maxScenarios(),
                request.userSamples()));
        return ResponseEntity.accepted().body(handle);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    public record AnalyzeRequest(@NotBlank String statementId,
                                 @NotBlank String artifactId,
                                 @NotBlank String datasourceProfileId,
                                 String sessionId,
                                 String projectId,
                                 String moduleId,
                                 String mybatisConfigXml,
                                 String databaseId,
                                 String schemaName,
                                 Integer maxScenarios,
                                 List<Map<String, Object>> userSamples) {}
}
