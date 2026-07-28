package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.evidence.SlowLogSource;
import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import com.biz.sccba.sqlanalyzer.repository.DocumentRepository;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioEngine;
import com.biz.sccba.sqlanalyzer.scenario.ScenarioModels.PlannerInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Ingests artifacts and projects them into chunked documents for the agent context pipeline. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ArtifactPipelineService {
    private static final int CHUNK_CHARS = 8000;
    private static final int PIPELINE_SCENARIO_CAP = 6;
    private final ArtifactService artifactService;
    private final ArtifactRepository artifacts;
    private final DocumentRepository documents;
    private final MyBatisXmlParserService myBatisParser;
    private final ScenarioEngine scenarioEngine;
    private final ObjectMapper objectMapper;

    public ArtifactPipelineService(ArtifactService artifactService, ArtifactRepository artifacts, DocumentRepository documents,
                                   MyBatisXmlParserService myBatisParser,
                                   ScenarioEngine scenarioEngine, ObjectMapper objectMapper) {
        this.artifactService = artifactService;
        this.artifacts = artifacts;
        this.documents = documents;
        this.myBatisParser = myBatisParser;
        this.scenarioEngine = scenarioEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public IndexedArtifact ingestText(String clientId, String sessionId, String sourceType, String content) {
        var artifact = artifactService.ingestText(clientId, sessionId, sourceType, content);
        return index(clientId, artifact.id(), sourceType, content);
    }

    public IndexedArtifact index(String clientId, String artifactId, String documentType, String content) {
        artifacts.findByIdForClient(artifactId, clientId).orElseThrow(() -> new IllegalArgumentException("Artifact 不存在"));
        String documentId = "document_" + UUID.randomUUID();
        documents.create(documentId, artifactId, documentType, "plain-text", "1", content, "{}");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String normalized = new String(bytes, StandardCharsets.UTF_8);
        int sequence = 0;
        for (int start = 0; start < normalized.length(); start += CHUNK_CHARS) {
            String chunk = normalized.substring(start, Math.min(normalized.length(), start + CHUNK_CHARS));
            documents.addChunk("chunk_" + UUID.randomUUID(), documentId, sequence++, "TEXT", chunk,
                    Math.max(1, chunk.length() / 4), "{}");
        }
        if (normalized.isEmpty()) documents.addChunk("chunk_" + UUID.randomUUID(), documentId, 0, "TEXT", "", 0, "{}");
        return new IndexedArtifact(artifactId, documentId, sequence);
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public IndexedArtifact ingestMyBatisMapper(String clientId, String sessionId, String xmlContent, String namespace) {
        return ingestMyBatisMapper(clientId, sessionId, xmlContent, namespace,
                "MYBATIS_MAPPER_XML", "{}");
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public IndexedArtifact ingestMyBatisMapper(String clientId, String sessionId, String xmlContent,
                                               String namespace, String sourceType,
                                               String metadataJson) {
        var artifact = artifactService.ingest(clientId, sessionId, sourceType, null,
                "application/xml", xmlContent.getBytes(StandardCharsets.UTF_8), metadataJson);
        var parsed = myBatisParser.parseMapper(xmlContent, namespace);
        byte[] mapperBytes = xmlContent.getBytes(StandardCharsets.UTF_8);
        try {
            String structured = objectMapper.writeValueAsString(parsed);
            String documentId = "document_" + UUID.randomUUID();
            documents.create(documentId, artifact.id(), "MYBATIS_MAPPER", "mybatis-xml", "1", xmlContent, structured);
            int sequence = 0;
            for (var statement : parsed.statements()) {
                // BoundSql scenarios come exclusively from the official MyBatis runtime.
                List<String> scenarioLines = new ArrayList<>();
                String loadError = null;
                try {
                    var plan = scenarioEngine.plan(mapperBytes,
                            "artifact:" + artifact.id() + ":" + statement.statementId(),
                            statement.statementId(), PlannerInput.defaults(PIPELINE_SCENARIO_CAP), null, null);
                    loadError = plan.loadError();
                    for (var bound : plan.scenarios()) {
                        if (bound.isUnsupported()) {
                            scenarioLines.add("[UNSUPPORTED] " + bound.unsupported());
                        } else {
                            scenarioLines.add("[fp=" + bound.sqlFingerprint()
                                    + (bound.hasDollarInterpolation() ? ",RISK=${}" : "") + "] "
                                    + bound.boundSql().replaceAll("\\s+", " ").trim());
                        }
                    }
                } catch (RuntimeException planFailed) {
                    loadError = "UNSUPPORTED: " + planFailed.getMessage();
                }
                String content = statement.statementType() + " " + statement.namespace() + "." + statement.statementId()
                        + "\n" + statement.originalSql()
                        + "\nBOUND_SCENARIOS:\n"
                        + (loadError != null ? loadError + "\n" : "")
                        + String.join("\n", scenarioLines);
                documents.addChunk("chunk_" + UUID.randomUUID(), documentId, sequence++, "MYBATIS_STATEMENT",
                        content, Math.max(1, content.length() / 4), objectMapper.writeValueAsString(statement.testConditions()));
            }
            if (sequence == 0) documents.addChunk("chunk_" + UUID.randomUUID(), documentId, 0, "MYBATIS_MAPPER", xmlContent,
                    Math.max(1, xmlContent.length() / 4), "{}");
            return new IndexedArtifact(artifact.id(), documentId, sequence);
        } catch (Exception e) {
            throw new IllegalArgumentException("MyBatis 解析结果无法写入上下文管线", e);
        }
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public IndexedArtifact ingestEvidence(String clientId, String sessionId, String evidenceType, String content) {
        if (evidenceType == null || evidenceType.isBlank()) throw new IllegalArgumentException("证据类型不能为空");
        return ingestText(clientId, sessionId, evidenceType.toUpperCase(java.util.Locale.ROOT), content == null ? "" : content);
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public IndexedArtifact ingestSlowLog(String clientId, String sessionId, String runId,
                                         SlowLogSource.SlowLogBatch batch) {
        if (batch == null) throw new IllegalArgumentException("慢日志结果不能为空");
        try {
            String content = objectMapper.writeValueAsString(batch);
            String metadata = objectMapper.writeValueAsString(java.util.Map.of(
                    "runId", runId == null ? "" : runId,
                    "source", batch.source(),
                    "entryCount", batch.entries() == null ? 0 : batch.entries().size()));
            var artifact = artifactService.ingest(clientId, sessionId, "SLOW_LOG", "slow-log.json",
                    "application/json", content.getBytes(StandardCharsets.UTF_8), metadata);
            return index(clientId, artifact.id(), "SLOW_LOG", content);
        } catch (Exception e) {
            throw new IllegalArgumentException("慢日志证据无法写入上下文管线", e);
        }
    }

    public record IndexedArtifact(String artifactId, String documentId, int chunkCount) {}
}
