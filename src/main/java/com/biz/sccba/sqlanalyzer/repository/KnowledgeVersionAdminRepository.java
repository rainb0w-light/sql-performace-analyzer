package com.biz.sccba.sqlanalyzer.repository;

import java.time.Instant;
import java.util.Optional;

/** Processing metadata for the lightweight knowledge administration control plane. */
public interface KnowledgeVersionAdminRepository {

    Optional<AdminVersion> findForClient(String clientId, String versionId);

    Optional<AdminVersion> findByContentHash(String clientId, String sourceId, String contentHash);

    void updateUploadMetadata(String clientId, String versionId, String contentHash, String fileName,
                              String mediaType, long fileSize, String status);

    void markReady(String clientId, String versionId, String previewJson, int chunkCount);

    void updateStatus(String clientId, String versionId, String status, String errorCode);

    record AdminVersion(
            String id,
            String sourceId,
            int versionNo,
            String status,
            String artifactId,
            String contentHash,
            String fileName,
            String mediaType,
            Long fileSize,
            int chunkCount,
            String errorCode,
            Instant createdAt,
            Instant updatedAt) {}
}
