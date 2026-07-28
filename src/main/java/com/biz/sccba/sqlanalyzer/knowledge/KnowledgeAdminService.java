package com.biz.sccba.sqlanalyzer.knowledge;

import com.biz.sccba.sqlanalyzer.domain.knowledge.Knowledge.Source;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeSourceRepository;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeVersionAdminRepository;
import com.biz.sccba.sqlanalyzer.repository.KnowledgeVersionAdminRepository.AdminVersion;
import com.biz.sccba.sqlanalyzer.service.ArtifactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Upload/parse portion of the lightweight knowledge administration service. */
@Service
@ConditionalOnProperty(prefix = "sql-analyzer.persistence", name = "enabled", havingValue = "true")
public class KnowledgeAdminService {

    private final ArtifactService artifacts;
    private final KnowledgeSourceRepository sources;
    private final KnowledgeVersionAdminRepository versions;
    private final ObjectMapper objectMapper;
    private final KnowledgeDocumentReader reader;
    private final long maxFileBytes;

    public KnowledgeAdminService(
            ArtifactService artifacts,
            KnowledgeSourceRepository sources,
            KnowledgeVersionAdminRepository versions,
            ObjectMapper objectMapper,
            @Value("${sql-analyzer.knowledge.upload.max-file-bytes:10485760}") long maxFileBytes,
            @Value("${sql-analyzer.knowledge.upload.max-chunks:1000}") int maxChunks,
            @Value("${sql-analyzer.knowledge.upload.parse-timeout-ms:30000}") long parseTimeoutMs,
            @Value("${sql-analyzer.knowledge.upload.max-expanded-bytes:52428800}") long maxExpandedBytes) {
        this.artifacts = artifacts;
        this.sources = sources;
        this.versions = versions;
        this.objectMapper = objectMapper;
        this.maxFileBytes = maxFileBytes;
        this.reader = new KnowledgeDocumentReader(maxFileBytes, maxChunks, parseTimeoutMs, maxExpandedBytes);
    }

    public UploadResult upload(String clientId, String requestedSourceId, String sourceName,
                               String originalFileName, String declaredMediaType, byte[] bytes) {
        String fileName = safeFileName(originalFileName);
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("知识文件不能为空");
        if (bytes.length > maxFileBytes) throw new IllegalArgumentException("知识文件超过大小上限");

        // The immutable original is always persisted before a parser is invoked.
        var artifact = artifacts.ingest(clientId, null, "KNOWLEDGE_DOCUMENT", fileName,
                declaredMediaType == null ? "application/octet-stream" : declaredMediaType, bytes, "{}");
        Source source = resolveSource(clientId, requestedSourceId, sourceName, fileName);
        var existing = versions.findByContentHash(clientId, source.id(), artifact.sha256());
        if (existing.isPresent()) return UploadResult.from(existing.get(), true);

        var created = sources.createVersion(clientId, "kv_" + UUID.randomUUID(), source.id(),
                sources.nextVersionNo(clientId, source.id()), artifact.id(), "{}", "[]");
        versions.updateUploadMetadata(clientId, created.id(), artifact.sha256(), fileName,
                declaredMediaType, artifact.byteSize(), "UPLOADED");
        versions.updateStatus(clientId, created.id(), "PARSING", null);
        try {
            var parsed = reader.read(fileName, declaredMediaType, bytes);
            List<DraftChunk> chunks = java.util.stream.IntStream.range(0, parsed.chunks().size())
                    .mapToObj(index -> new DraftChunk("DOCUMENT", fileName, "chunk:" + index,
                            parsed.chunks().get(index).text()))
                    .toList();
            String preview = objectMapper.writeValueAsString(new DocumentDraft(
                    "UNSTRUCTURED", artifact.sha256(), fileName, parsed.mediaType(), chunks));
            versions.markReady(clientId, created.id(), preview, chunks.size());
            return UploadResult.from(versions.findForClient(clientId, created.id()).orElseThrow(), false);
        } catch (RuntimeException exception) {
            versions.updateStatus(clientId, created.id(), "FAILED", stableReaderError(exception));
            throw exception;
        } catch (Exception exception) {
            versions.updateStatus(clientId, created.id(), "FAILED", "READER_FAILED");
            throw new IllegalStateException("知识解析结果无法保存", exception);
        }
    }

    public List<SourceSummary> listSources(String clientId) {
        return sources.listSources(clientId).stream().map(source -> {
            var latestDomain = sources.listVersions(clientId, source.id()).stream().findFirst().orElse(null);
            AdminVersion latest = latestDomain == null ? null
                    : versions.findForClient(clientId, latestDomain.id()).orElse(null);
            String status = latest == null ? null : latest.status();
            if (latest != null && latest.id().equals(source.currentVersionId())) status = "ACTIVE";
            return new SourceSummary(source.id(), source.name(), source.sourceType(),
                    source.currentVersionId(), status,
                    latest == null ? null : latest.id(),
                    latest == null ? 0 : latest.versionNo(),
                    latest == null ? null : latest.fileName(),
                    latest == null ? 0 : latest.chunkCount(),
                    source.createdAt(), source.updatedAt());
        }).toList();
    }

    public AdminVersion version(String clientId, String versionId) {
        return versions.findForClient(clientId, versionId)
                .orElseThrow(() -> new IllegalArgumentException("知识版本不存在"));
    }

    /** Marks an Admin publish attempt while keeping crash-safe retries possible. */
    public boolean beginPublishing(String clientId, String versionId) {
        AdminVersion version = version(clientId, versionId);
        if ("PUBLISHED".equals(version.status()) || "ACTIVE".equals(version.status())) return false;
        if (!Set.of("READY", "DRAFT", "PUBLISHING").contains(version.status())) {
            throw new IllegalStateException("只有 READY 草稿可以发布，当前状态：" + version.status());
        }
        versions.updateStatus(clientId, versionId, "PUBLISHING", null);
        return true;
    }

    public void publishFailed(String clientId, String versionId, String errorCode) {
        AdminVersion current = version(clientId, versionId);
        if ("PUBLISHING".equals(current.status())) {
            versions.updateStatus(clientId, versionId, "READY", errorCode);
        }
    }

    private Source resolveSource(String clientId, String requestedSourceId, String sourceName, String fileName) {
        if (requestedSourceId != null && !requestedSourceId.isBlank()) {
            return sources.findSourceForClient(clientId, requestedSourceId)
                    .orElseThrow(() -> new IllegalArgumentException("知识源不存在"));
        }
        String name = sourceName == null || sourceName.isBlank() ? fileName : sourceName.trim();
        return sources.listSources(clientId).stream()
                .filter(source -> source.name().equals(name))
                .findFirst()
                .orElseGet(() -> sources.createSource("ks_" + UUID.randomUUID(), clientId, name, "DOCUMENT"));
    }

    private static String safeFileName(String original) {
        if (original == null || original.isBlank()) throw new IllegalArgumentException("文件名不能为空");
        String normalized = original.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isBlank() || ".".equals(normalized) || "..".equals(normalized)) {
            throw new IllegalArgumentException("文件名无效");
        }
        return normalized.length() > 500 ? normalized.substring(normalized.length() - 500) : normalized;
    }

    private static String stableReaderError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null && message.contains("超过")) return "UPLOAD_LIMIT_EXCEEDED";
        if (throwable instanceof IllegalArgumentException) return "INVALID_DOCUMENT";
        return "READER_FAILED";
    }

    public record DraftChunk(String kind, String name, String locator, String text) {}

    public record DocumentDraft(String format, String contentHash, String fileName, String mediaType,
                                List<DraftChunk> chunks) {}

    public record UploadResult(String sourceId, String versionId, int versionNo, String status,
                               String contentHash, int chunkCount, boolean idempotent) {
        static UploadResult from(AdminVersion version, boolean idempotent) {
            return new UploadResult(version.sourceId(), version.id(), version.versionNo(),
                    version.status(), version.contentHash(), version.chunkCount(), idempotent);
        }
    }

    public record SourceSummary(String id, String name, String sourceType, String currentVersionId,
                                String status, String latestVersionId, int latestVersionNo,
                                String fileName, int chunkCount,
                                java.time.Instant createdAt, java.time.Instant updatedAt) {}
}
