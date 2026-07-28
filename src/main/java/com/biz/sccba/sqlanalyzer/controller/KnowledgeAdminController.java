package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.knowledge.ActiveKnowledgeSearchService;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeAdminService;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeImportService;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeOperationService;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeOperationRepository.Filter;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import com.biz.sccba.sqlanalyzer.service.TokenService.AuthenticatedClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class KnowledgeAdminController {

    private final KnowledgeAdminService admin;
    private final KnowledgeImportService imports;
    private final ActiveKnowledgeSearchService search;
    private final KnowledgeOperationService operations;
    private final KnowledgeSourceRepository sources;
    private final BearerClients bearer;

    public KnowledgeAdminController(KnowledgeAdminService admin, KnowledgeImportService imports,
                                    ActiveKnowledgeSearchService search,
                                    KnowledgeOperationService operations,
                                    KnowledgeSourceRepository sources, BearerClients bearer) {
        this.admin = admin;
        this.imports = imports;
        this.search = search;
        this.operations = operations;
        this.sources = sources;
        this.bearer = bearer;
    }

    @PostMapping(value = "/knowledge-sources/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) String sourceName) throws IOException {
        AuthenticatedClient actor = bearer.requireAny(authorization, "KNOWLEDGE_ADMIN");
        long started = System.nanoTime();
        Map<String, Object> summary = uploadSummary(file, idempotencyKey);
        try {
            var result = admin.upload(actor.clientId(), sourceId, sourceName,
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
            long duration = elapsed(started);
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "UPLOAD",
                    result.sourceId(), result.versionId(), null, null, summary,
                    "SUCCESS", null, duration, 1, null);
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "PARSE",
                    result.sourceId(), result.versionId(), null, null, summary,
                    "SUCCESS", null, duration, result.chunkCount(), null);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sourceId", result.sourceId());
            body.put("versionId", result.versionId());
            body.put("versionNo", result.versionNo());
            body.put("status", result.status());
            body.put("contentHash", result.contentHash());
            body.put("chunkCount", result.chunkCount());
            body.put("idempotent", result.idempotent());
            body.put("statusUrl", "/api/v1/admin/knowledge-versions/" + result.versionId());
            return ResponseEntity.accepted().body(body);
        } catch (RuntimeException exception) {
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "UPLOAD",
                    sourceId, null, null, null, summary, "FAILED", errorCode(exception),
                    elapsed(started), 0, null);
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "PARSE",
                    sourceId, null, null, null, summary, "FAILED", errorCode(exception),
                    elapsed(started), 0, null);
            throw exception;
        }
    }

    @GetMapping("/knowledge-sources")
    public Map<String, Object> sources(@RequestHeader("Authorization") String authorization,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        AuthenticatedClient actor = bearer.requireAny(
                authorization, "KNOWLEDGE_ADMIN", "KNOWLEDGE_VIEWER");
        if (page < 0 || size < 1 || size > 200) throw new IllegalArgumentException("分页参数无效");
        var all = admin.listSources(actor.clientId());
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return Map.of("items", all.subList(from, to), "page", page, "size", size, "total", all.size());
    }

    @GetMapping("/knowledge-sources/{sourceId}")
    public Map<String, Object> source(@RequestHeader("Authorization") String authorization,
                                      @PathVariable String sourceId) {
        AuthenticatedClient actor = bearer.requireAny(
                authorization, "KNOWLEDGE_ADMIN", "KNOWLEDGE_VIEWER");
        var source = sources.findSourceForClient(actor.clientId(), sourceId)
                .orElseThrow(() -> new IllegalArgumentException("知识源不存在"));
        return Map.of("source", source, "versions", imports.listVersions(actor.clientId(), sourceId));
    }

    @GetMapping("/knowledge-versions/{versionId}")
    public Object version(@RequestHeader("Authorization") String authorization,
                          @PathVariable String versionId) {
        AuthenticatedClient actor = bearer.requireAny(
                authorization, "KNOWLEDGE_ADMIN", "KNOWLEDGE_VIEWER");
        return admin.version(actor.clientId(), versionId);
    }

    @PostMapping("/knowledge-versions/{versionId}/publish")
    public Object publish(@RequestHeader("Authorization") String authorization,
                          @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                          @PathVariable String versionId) {
        AuthenticatedClient actor = bearer.requireAny(authorization, "KNOWLEDGE_ADMIN");
        long started = System.nanoTime();
        Map<String, Object> summary = idempotencySummary(idempotencyKey);
        boolean publishingStarted = false;
        try {
            publishingStarted = admin.beginPublishing(actor.clientId(), versionId);
            var published = imports.publish(actor.clientId(), versionId, actor.actorId());
            long duration = elapsed(started);
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "INDEX",
                    published.sourceId(), published.id(), null, null, summary,
                    "SUCCESS", null, duration, null, null);
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "PUBLISH",
                    published.sourceId(), published.id(), null, null, summary,
                    "SUCCESS", null, duration, 1, null);
            return Map.of("sourceId", published.sourceId(), "versionId", published.id(),
                    "versionNo", published.versionNo(), "status", "ACTIVE",
                    "publishedAt", published.publishedAt());
        } catch (RuntimeException exception) {
            if (publishingStarted) {
                admin.publishFailed(actor.clientId(), versionId, errorCode(exception));
            }
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "INDEX",
                    null, versionId, null, null, summary,
                    "FAILED", errorCode(exception), elapsed(started), 0, null);
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "PUBLISH",
                    null, versionId, null, null, summary,
                    "FAILED", errorCode(exception), elapsed(started), 0, null);
            throw exception;
        }
    }

    @PostMapping("/knowledge-samples")
    public ActiveKnowledgeSearchService.SearchResponse sample(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody SampleRequest request) {
        AuthenticatedClient actor = bearer.requireAny(
                authorization, "KNOWLEDGE_ADMIN", "KNOWLEDGE_VIEWER");
        int topK = request.topK() == null ? 5 : request.topK();
        long started = System.nanoTime();
        try {
            var response = search.sample(actor.clientId(), request.query(), request.sourceId(), topK);
            List<String> hitSources = response.results().stream()
                    .map(ActiveKnowledgeSearchService.SearchHit::sourceId).distinct().toList();
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "SAMPLE",
                    request.sourceId(), null, null, null,
                    KnowledgeOperationService.querySummary(
                            request.query(), topK, request.sourceId(), hitSources),
                    "SUCCESS", null, response.durationMs(), response.results().size(), null);
            return response;
        } catch (RuntimeException exception) {
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "SAMPLE",
                    request.sourceId(), null, null, null,
                    KnowledgeOperationService.querySummary(request.query(), topK, request.sourceId(), List.of()),
                    "FAILED", errorCode(exception), elapsed(started), 0, null);
            throw exception;
        }
    }

    @GetMapping("/knowledge-operations")
    public Object operationList(@RequestHeader("Authorization") String authorization,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                @RequestParam(required = false) String actorId,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String sourceId,
                                @RequestParam(required = false) String traceId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
        AuthenticatedClient actor = bearer.requireAny(
                authorization, "KNOWLEDGE_ADMIN", "KNOWLEDGE_VIEWER");
        return operations.list(actor.clientId(),
                new Filter(from, to, actorId, type, status, sourceId, traceId), page, size);
    }

    @GetMapping("/knowledge-operations/stats")
    public Object stats(@RequestHeader("Authorization") String authorization) {
        AuthenticatedClient actor = bearer.requireAny(
                authorization, "KNOWLEDGE_ADMIN", "KNOWLEDGE_VIEWER");
        return operations.stats(actor.clientId());
    }

    @GetMapping(value = "/knowledge-operations/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) String traceId) {
        AuthenticatedClient actor = bearer.requireAny(authorization, "KNOWLEDGE_ADMIN");
        long started = System.nanoTime();
        Filter filter = new Filter(from, to, actorId, type, status, sourceId, traceId);
        try {
            byte[] csv = operations.exportCsv(actor.clientId(), filter);
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "EXPORT_LOGS",
                    sourceId, null, null, null, Map.of("filters", "applied"),
                    "SUCCESS", null, elapsed(started), null, null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=knowledge-operations.csv")
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(csv);
        } catch (RuntimeException exception) {
            operations.record(requestId, actor.clientId(), actor.actorId(), actor.role(), "EXPORT_LOGS",
                    sourceId, null, null, null, Map.of("filters", "applied"),
                    "FAILED", errorCode(exception), elapsed(started), 0, null);
            throw exception;
        }
    }

    private static Map<String, Object> uploadSummary(MultipartFile file, String idempotencyKey) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fileName", file.getOriginalFilename());
        summary.put("fileSize", file.getSize());
        summary.put("mediaType", file.getContentType());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            summary.put("idempotencyKeyHash", Integer.toHexString(idempotencyKey.hashCode()));
        }
        return summary;
    }

    private static Map<String, Object> idempotencySummary(String key) {
        return key == null || key.isBlank() ? Map.of()
                : Map.of("idempotencyKeyHash", Integer.toHexString(key.hashCode()));
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException) return "INVALID_REQUEST";
        if (exception.getMessage() != null && exception.getMessage().contains("未启用")) {
            return "RETRIEVAL_UNAVAILABLE";
        }
        return "KNOWLEDGE_OPERATION_FAILED";
    }

    private static long elapsed(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    public record SampleRequest(@NotBlank String query, String sourceId, Integer topK) {}
}
