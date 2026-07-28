package com.biz.sccba.sqlanalyzer.controller;

import com.biz.sccba.sqlanalyzer.domain.Artifact;
import com.biz.sccba.sqlanalyzer.service.ArtifactPipelineService;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.biz.sccba.sqlanalyzer.pluginapi.StaticAnnotationMapperService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

/** artifacts/documents resource API (docs/contracts/rest-api.md §1). */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ArtifactController {

    private final ArtifactService artifacts;
    private final ArtifactPipelineService pipeline;
    private final StaticAnnotationMapperService annotationMappers;
    private final BearerClients bearer;

    public ArtifactController(ArtifactService artifacts, ArtifactPipelineService pipeline,
                              StaticAnnotationMapperService annotationMappers, BearerClients bearer) {
        this.artifacts = artifacts;
        this.pipeline = pipeline;
        this.annotationMappers = annotationMappers;
        this.bearer = bearer;
    }

    @PostMapping(value = "/artifacts/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Artifact upload(@RequestHeader("Authorization") String authorization,
                           @RequestParam MultipartFile file,
                           @RequestParam(required = false) String sessionId,
                           @RequestParam(defaultValue = "FILE_UPLOAD") String sourceType) throws IOException {
        return artifacts.ingest(bearer.clientId(authorization), sessionId, sourceType, file.getOriginalFilename(),
                file.getContentType(), file.getBytes(), "{}");
    }

    @PostMapping("/artifacts/text")
    public Artifact text(@RequestHeader("Authorization") String authorization,
                         @RequestBody TextArtifactRequest request) {
        return artifacts.ingestText(bearer.clientId(authorization), request.sessionId(), request.sourceType(), request.content());
    }

    @GetMapping("/artifacts/{artifactId}/content")
    public ResponseEntity<byte[]> content(@RequestHeader("Authorization") String authorization,
                                          @PathVariable String artifactId) {
        return ResponseEntity.ok(artifacts.read(bearer.clientId(authorization), artifactId));
    }

    @PostMapping("/artifacts/text/index")
    public ArtifactPipelineService.IndexedArtifact textAndIndex(
            @RequestHeader("Authorization") String authorization,
            @RequestBody TextArtifactRequest request) {
        return pipeline.ingestText(bearer.clientId(authorization), request.sessionId(), request.sourceType(), request.content());
    }

    @PostMapping("/artifacts/mybatis/index")
    public ArtifactPipelineService.IndexedArtifact mybatisAndIndex(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody MyBatisArtifactRequest request) {
        return pipeline.ingestMyBatisMapper(bearer.clientId(authorization), request.sessionId(), request.xmlContent(), request.namespace());
    }

    @PostMapping("/artifacts/mybatis/annotation-index")
    public ArtifactPipelineService.IndexedArtifact mybatisAnnotationAndIndex(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody MyBatisAnnotationArtifactRequest request) {
        return annotationMappers.index(bearer.clientId(authorization), request.sessionId(),
                request.javaContent(), request.namespace(), request.methodName());
    }

    @PostMapping("/artifacts/evidence/index")
    public ArtifactPipelineService.IndexedArtifact evidenceAndIndex(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody EvidenceArtifactRequest request) {
        return pipeline.ingestEvidence(bearer.clientId(authorization), request.sessionId(), request.evidenceType(), request.content());
    }

    public record TextArtifactRequest(@NotBlank String content, String sessionId, String sourceType) {
        public TextArtifactRequest {
            if (sourceType == null || sourceType.isBlank()) sourceType = "TEXT_INPUT";
        }
    }

    public record MyBatisArtifactRequest(@NotBlank String xmlContent, String sessionId, String namespace) {}

    public record MyBatisAnnotationArtifactRequest(@NotBlank String javaContent, String sessionId,
                                                   @NotBlank String namespace,
                                                   @NotBlank String methodName) {}

    public record EvidenceArtifactRequest(@NotBlank String evidenceType, @NotBlank String content, String sessionId) {}
}
