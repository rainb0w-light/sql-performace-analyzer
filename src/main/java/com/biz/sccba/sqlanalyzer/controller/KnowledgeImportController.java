package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Source;
import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Version;
import com.biz.sccba.sqlanalyzer.knowledge.KnowledgeImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
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
import java.util.List;
import java.util.Map;

/** knowledge-sources/imports resource API (docs/contracts/rest-api.md §1). */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class KnowledgeImportController {

    private final KnowledgeImportService imports;
    private final KnowledgeSourceRepository knowledge;
    private final BearerClients bearer;

    public KnowledgeImportController(KnowledgeImportService imports, KnowledgeSourceRepository knowledge, BearerClients bearer) {
        this.imports = imports;
        this.knowledge = knowledge;
        this.bearer = bearer;
    }

    @GetMapping("/knowledge-sources")
    public List<Source> sources(@RequestHeader("Authorization") String authorization) {
        var actor = bearer.requireAny(authorization, "KNOWLEDGE_ADMIN", "KNOWLEDGE_VIEWER");
        return knowledge.listSources(actor.clientId());
    }

    @PostMapping(value = "/knowledge-sources/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importExcel(@RequestHeader("Authorization") String authorization,
                                           @RequestParam MultipartFile file,
                                           @RequestParam(required = false) String sourceName) throws IOException {
        String clientId = bearer.requireAny(authorization, "KNOWLEDGE_ADMIN").clientId();
        var preview = imports.importExcel(clientId, sourceName, file.getOriginalFilename(), file.getBytes());
        return Map.of(
                "sourceId", preview.sourceId(),
                "versionId", preview.versionId(),
                "versionNo", preview.versionNo(),
                "counts", Map.of(
                        "tables", preview.parsed().tables().size(),
                        "columns", preview.parsed().columns().size(),
                        "rules", preview.parsed().rules().size(),
                        "enums", preview.parsed().enums().size(),
                        "aliases", preview.parsed().aliases().size(),
                        "shards", preview.parsed().shards().size()),
                "errors", preview.parsed().errors());
    }

    @GetMapping("/knowledge-sources/{sourceId}/versions")
    public List<Version> versions(@RequestHeader("Authorization") String authorization,
                                  @PathVariable String sourceId) {
        var actor = bearer.requireAny(authorization, "KNOWLEDGE_ADMIN", "KNOWLEDGE_VIEWER");
        return imports.listVersions(actor.clientId(), sourceId);
    }

    @GetMapping("/knowledge-versions/{versionId}/preview")
    public Map<String, Object> preview(@RequestHeader("Authorization") String authorization,
                                       @PathVariable String versionId) {
        var actor = bearer.requireAny(authorization, "KNOWLEDGE_ADMIN", "KNOWLEDGE_VIEWER");
        Version version = imports.preview(actor.clientId(), versionId);
        return Map.of("versionId", version.id(), "status", version.status(),
                "preview", version.previewJson(), "errors", version.errorJson());
    }

    @PostMapping("/knowledge-versions/{versionId}/publish")
    public Version publish(@RequestHeader("Authorization") String authorization,
                           @PathVariable String versionId,
                           @Valid @RequestBody PublishRequest request) {
        var identity = bearer.requireAny(authorization, "KNOWLEDGE_ADMIN");
        return imports.publish(identity.clientId(), versionId, identity.actorId());
    }

    @PostMapping("/knowledge-sources/{sourceId}/rollback")
    public Version rollback(@RequestHeader("Authorization") String authorization,
                            @PathVariable String sourceId,
                            @Valid @RequestBody RollbackRequest request) {
        var actor = bearer.requireAny(authorization, "KNOWLEDGE_ADMIN");
        return imports.rollback(actor.clientId(), sourceId, request.targetVersionId());
    }

    public record PublishRequest(String publishedBy) {}

    public record RollbackRequest(@NotBlank String targetVersionId) {}
}
