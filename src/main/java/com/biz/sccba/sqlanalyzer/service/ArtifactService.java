package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.repository.ArtifactRepository;
import com.biz.sccba.sqlanalyzer.domain.Artifact;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Stores immutable artifact bytes (Excel originals, Mapper snapshots, evidence) with SHA-256 identity. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class ArtifactService {
    private static final int CHUNK_SIZE = 1024 * 1024;
    private final ArtifactRepository artifacts;

    public ArtifactService(ArtifactRepository artifacts) {
        this.artifacts = artifacts;
    }

    @Transactional(transactionManager = "managementTransactionManager")
    public Artifact ingest(String clientId, String sessionId, String sourceType, String fileName,
                           String mediaType, byte[] content, String metadataJson) {
        if (content == null) throw new IllegalArgumentException("Artifact 内容不能为空");
        String id = "artifact_" + UUID.randomUUID();
        Artifact artifact = new Artifact(id, clientId, sessionId, sourceType, fileName, mediaType,
                sha256(content), content.length, "INGESTED", metadataJson == null ? "{}" : metadataJson, Instant.now());
        artifacts.create(artifact);
        for (int offset = 0, sequence = 0; offset < content.length; offset += CHUNK_SIZE, sequence++) {
            int end = Math.min(content.length, offset + CHUNK_SIZE);
            artifacts.writeChunk(id, sequence, java.util.Arrays.copyOfRange(content, offset, end));
        }
        if (content.length == 0) artifacts.writeChunk(id, 0, new byte[0]);
        return artifact;
    }

    public Artifact ingestText(String clientId, String sessionId, String sourceType, String text) {
        return ingest(clientId, sessionId, sourceType, null, "text/plain", text.getBytes(StandardCharsets.UTF_8), "{}");
    }

    public byte[] read(String clientId, String artifactId) {
        return artifacts.readAll(clientId, artifactId)
                .orElseThrow(() -> new IllegalArgumentException("Artifact 不存在"));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算 Artifact hash", e);
        }
    }
}
