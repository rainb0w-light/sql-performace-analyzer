package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.Artifact;

import java.util.Optional;

/** Immutable artifact bytes (Excel originals, Mapper snapshots, evidence) with SHA-256 identity. */
public interface ArtifactRepository {
    Artifact create(Artifact artifact);

    void writeChunk(String artifactId, int sequence, byte[] content);

    /** Reads all bytes after verifying the artifact belongs to the client (defense in depth). */
    Optional<byte[]> readAll(String clientId, String artifactId);

    Optional<Artifact> findByIdForClient(String artifactId, String clientId);

    /** Content-addressed reuse for the same tenant and artifact purpose. */
    default Optional<Artifact> findBySha256ForClient(String clientId, String sourceType, String sha256) {
        return Optional.empty();
    }
}
